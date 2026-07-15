package com.mtd.megawallet.session

import com.mtd.core.keymanager.KeyManager
import com.mtd.domain.interfaceRepository.IActiveWalletProvider
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.security.AppLockManager
import com.mtd.domain.security.SecuritySnapshot
import androidx.lifecycle.LifecycleOwner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * TASK-06 — verifies the [WalletLockKeyCoordinator] invariant: decrypted keys are cached **iff** the
 * app is unlocked AND foregrounded, and the cache is re-hydrated once it becomes usable again.
 *
 * Drives the enforcement loop via [WalletLockKeyCoordinator.launchEnforcementLoop] + the
 * `onStart`/`onStop` lifecycle callbacks directly, so the tests never touch the Android-only
 * `ProcessLifecycleOwner` that production `start()` registers against.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WalletLockKeyCoordinatorTest {

    private val lockedFlow = MutableStateFlow(false)
    private val walletIdFlow = MutableStateFlow<String?>("wallet-1")
    private val owner = mockk<LifecycleOwner>(relaxed = true)

    private val keyManager = mockk<KeyManager>(relaxed = true)
    private val walletRepository = mockk<IWalletRepository> {
        coEvery { loadExistingWallet() } returns ResultResponse.Success(null)
    }

    private fun appLock(enabled: Boolean): AppLockManager {
        val manager = mockk<AppLockManager>()
        every { manager.isLocked } returns lockedFlow
        coEvery { manager.getSecuritySnapshot() } returns SecuritySnapshot(
            appLockEnabled = enabled,
            passcodeConfigured = enabled,
            biometricEnabled = false,
            lockTimeoutSeconds = 30,
            isLocked = lockedFlow.value
        )
        return manager
    }

    private fun provider(): IActiveWalletProvider {
        val p = mockk<IActiveWalletProvider>()
        every { p.activeWalletId } returns walletIdFlow
        return p
    }

    private fun coordinatorOn(scope: CoroutineScope, appLockEnabled: Boolean = true) =
        WalletLockKeyCoordinator(
            appLockManager = appLock(appLockEnabled),
            activeWalletProvider = provider(),
            keyManager = keyManager,
            walletRepository = { walletRepository },
            scope = scope
        )

    @Test
    fun `unlocked foreground with a wallet re-hydrates the key cache`() = runTest {
        val coordinator = coordinatorOn(backgroundScope)
        coordinator.launchEnforcementLoop()
        coordinator.onStart(owner) // unlocked (locked=false) + foreground
        advanceUntilIdle()

        coVerify { walletRepository.loadExistingWallet() }
    }

    @Test
    fun `locking while foregrounded clears the key cache`() = runTest {
        val coordinator = coordinatorOn(backgroundScope)
        coordinator.launchEnforcementLoop()
        coordinator.onStart(owner)
        advanceUntilIdle()

        lockedFlow.value = true // auto-lock fires
        advanceUntilIdle()

        verify { keyManager.clearCache() }
    }

    @Test
    fun `backgrounding clears the key cache even without a lock transition`() = runTest {
        val coordinator = coordinatorOn(backgroundScope)
        coordinator.launchEnforcementLoop()
        coordinator.onStart(owner)
        advanceUntilIdle()

        coordinator.onStop(owner)
        advanceUntilIdle()

        verify { keyManager.clearCache() }
    }

    @Test
    fun `no-passcode users are never touched (no clear, no re-hydrate)`() = runTest {
        val coordinator = coordinatorOn(backgroundScope, appLockEnabled = false)
        coordinator.launchEnforcementLoop()
        coordinator.onStart(owner)
        advanceUntilIdle()
        lockedFlow.value = true
        coordinator.onStop(owner)
        advanceUntilIdle()

        verify(exactly = 0) { keyManager.clearCache() }
        coVerify(exactly = 0) { walletRepository.loadExistingWallet() }
    }
}
