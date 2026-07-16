package com.mtd.megawallet.session

import com.mtd.domain.interfaceRepository.IActiveWalletProvider
import com.mtd.domain.interfaceRepository.ITokenStore
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.usecase.auth.EnsureAuthenticatedUseCase
import com.mtd.domain.usecase.auth.RefreshSessionUseCase
import com.mtd.domain.usecase.auth.SignOutUseCase
import com.mtd.domain.usecase.monitoring.SubscribeMonitoringUseCase
import com.mtd.megawallet.notification.FcmTokenRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2 — closes the Auth phase by wiring the (previously never-invoked) sign-in flow to the wallet
 * lifecycle. Observes the unlocked wallet and drives the JWT session + realtime socket:
 *
 *  - **unlock / app-start:** the active wallet id goes non-null → baseline sign-in
 *    ([EnsureAuthenticatedUseCase]) which mints the JWT and connects `/ws`.
 *  - **switch wallet:** the id changes from one non-null to another → `forceFresh` (clear old session,
 *    re-auth for the new address).
 *  - **lock / delete wallet:** the id goes null → [SignOutUseCase] (server logout best-effort + local
 *    token clear + socket disconnect).
 *  - **proactive refresh:** after each successful auth, a single job slides the session shortly before
 *    its expiry so the user is never bounced to an anonymous (401) socket.
 *
 * Started once from [com.mtd.megawallet.ui.compose.MainActivityCompose]. Idempotent.
 */
@Singleton
class WalletSessionAuthCoordinator @Inject constructor(
    private val activeWalletProvider: IActiveWalletProvider,
    private val ensureAuthenticated: EnsureAuthenticatedUseCase,
    private val refreshSession: RefreshSessionUseCase,
    private val signOut: SignOutUseCase,
    private val subscribeMonitoring: SubscribeMonitoringUseCase,
    private val fcmTokenRegistrar: FcmTokenRegistrar,
    private val tokenStore: ITokenStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var started = false
    private var lastWalletId: String? = null
    private var refreshJob: Job? = null

    /** Begin observing the wallet lifecycle. Safe to call repeatedly; only the first call takes effect. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            activeWalletProvider.activeWalletId.collect { walletId ->
                handleWalletChange(walletId)
            }
        }
    }

    private suspend fun handleWalletChange(walletId: String?) {
        if (walletId == lastWalletId) return
        val previous = lastWalletId
        lastWalletId = walletId
        refreshJob?.cancel()

        if (walletId == null) {
            // Lock or delete-of-last-wallet → drop session + tear down realtime.
            // Unregister the FCM token first (needs the JWT that sign-out is about to clear).
            fcmTokenRegistrar.unregister()
            when (val r = signOut()) {
                is ResultResponse.Error -> Timber.w(r.exception, "[Auth] sign-out cleanup failed")
                else -> Unit
            }
            return
        }

        // A transition between two distinct non-null wallets is a switch → force a fresh session.
        val forceFresh = previous != null
        when (val r = ensureAuthenticated(forceFresh = forceFresh)) {
            is ResultResponse.Success -> {
                scheduleProactiveRefresh()
                // TASK-32 — now that the JWT is minted, enroll the active wallet for realtime/deposit
                // monitoring. The use case is a no-op when this wallet was already enrolled (persisted
                // set), so a plain wallet switch never re-sends — only a newly created/imported wallet
                // triggers the network call. Fire-and-forget off the auth path.
                enrollMonitoring()
                // Item 10 (FCM) — register this install's push token now that we have a JWT (device
                // identity). No-op when the token is unchanged, so it's cheap on every auth.
                fcmTokenRegistrar.syncToken()
            }
            is ResultResponse.Error -> Timber.w(r.exception, "[Auth] sign-in failed for wallet=$walletId")
        }
    }

    private fun enrollMonitoring() {
        scope.launch {
            when (val r = subscribeMonitoring()) {
                is ResultResponse.Success ->
                    Timber.d("[Monitoring] enrolled ${r.data.size} pair(s) for realtime/deposit monitoring")
                is ResultResponse.Error -> Timber.w(r.exception, "[Monitoring] batch enrollment failed")
            }
        }
    }

    private fun scheduleProactiveRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val expiresAt = tokenStore.getExpiresAtEpochSec() ?: return@launch
            val nowSec = System.currentTimeMillis() / 1000
            val delaySec = (expiresAt - nowSec - REFRESH_MARGIN_SEC).coerceAtLeast(MIN_REFRESH_DELAY_SEC)
            delay(delaySec * 1000)

            when (val r = refreshSession()) {
                is ResultResponse.Success -> scheduleProactiveRefresh() // chain the next slide
                is ResultResponse.Error -> Timber.w(r.exception, "[Auth] proactive refresh failed")
            }
        }
    }

    private companion object {
        const val REFRESH_MARGIN_SEC = 60L      // refresh ~1 min before expiry
        const val MIN_REFRESH_DELAY_SEC = 5L    // never busy-loop on a near-expired token
    }
}
