package com.mtd.megawallet.session

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mtd.core.keymanager.KeyManager
import com.mtd.domain.interfaceRepository.IActiveWalletProvider
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.security.AppLockManager
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class WalletLockKeyCoordinator internal constructor(
    private val appLockManager: AppLockManager,
    private val activeWalletProvider: IActiveWalletProvider,
    private val keyManager: KeyManager,
    private val walletRepository: Lazy<IWalletRepository>,
    private val scope: CoroutineScope
) : DefaultLifecycleObserver {
    // Hilt entry point — the production graph uses a dedicated IO-dispatched scope; tests inject their
    // own (e.g. runTest's backgroundScope) via the internal constructor.
    @Inject
    constructor(
        appLockManager: AppLockManager,
        activeWalletProvider: IActiveWalletProvider,
        keyManager: KeyManager,
        walletRepository: Lazy<IWalletRepository>
    ) : this(
        appLockManager,
        activeWalletProvider,
        keyManager,
        walletRepository,
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    private val foreground = MutableStateFlow(false)

    @Volatile private var started = false
    private var rehydrateJob: Job? = null

    /**
     * Begin enforcing the key-cache invariant. Must be called on the main thread (registers a
     * [ProcessLifecycleOwner] observer); safe to call repeatedly — only the first call takes effect.
     */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        launchEnforcementLoop()
    }

    // Split out from [start] so unit tests can drive the enforcement loop (and foreground via
    // onStart/onStop) without touching the Android-only ProcessLifecycleOwner.
    internal fun launchEnforcementLoop() {
        scope.launch {
            combine(appLockManager.isLocked, foreground) { locked, fg -> locked to fg }
                .distinctUntilChanged()
                .collect { (locked, fg) -> enforce(locked = locked, foreground = fg) }
        }
    }

    /** App-wide foreground. */
    override fun onStart(owner: LifecycleOwner) {
        foreground.value = true
    }

    /** App-wide background (backgrounded / screen locked). */
    override fun onStop(owner: LifecycleOwner) {
        foreground.value = false
    }

    private suspend fun enforce(locked: Boolean, foreground: Boolean) {
        // Engage only when the user has actually turned on app-lock; otherwise leave behavior unchanged
        // (no clearing, no resume-time re-derivation) for users who never opted into a lock.
        if (!appLockManager.getSecuritySnapshot().appLockEnabled) return

        val keysAllowed = !locked && foreground
        if (!keysAllowed) {
            rehydrateJob?.cancel()
            keyManager.clearCache()
            Timber.d("[Lock] cleared in-memory key cache (locked=%s, foreground=%s)", locked, foreground)
            return
        }

        // Unlocked + foreground → re-hydrate the cache we cleared on the way in. Skipped until a wallet
        // has actually been loaded (the initial cold-start load is owned by HomeViewModel); loading is
        // idempotent, so a redundant call is harmless.
        if (activeWalletProvider.activeWalletId.value == null) return
        rehydrateJob?.cancel()
        rehydrateJob = scope.launch {
            when (val r = walletRepository.get().loadExistingWallet()) {
                is ResultResponse.Error -> Timber.w(r.exception, "[Lock] key re-hydration failed")
                else -> Timber.d("[Lock] re-hydrated in-memory key cache")
            }
        }
    }
}
