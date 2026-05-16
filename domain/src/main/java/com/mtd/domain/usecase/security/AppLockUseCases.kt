package com.mtd.domain.usecase.security

import com.mtd.domain.security.AppLockManager
import com.mtd.domain.security.SecuritySnapshot
import com.mtd.domain.security.UnlockAttemptResult
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveAppLockedUseCase @Inject constructor(
    private val appLockManager: AppLockManager
) {
    operator fun invoke(): StateFlow<Boolean> = appLockManager.isLocked
}

class ObserveAppLockInitializedUseCase @Inject constructor(
    private val appLockManager: AppLockManager
) {
    operator fun invoke(): StateFlow<Boolean> = appLockManager.isInitialized
}

class InitializeAppLockUseCase @Inject constructor(
    private val appLockManager: AppLockManager
) {
    suspend operator fun invoke() = appLockManager.initialize()
}

class NotifyAppBackgroundedUseCase @Inject constructor(
    private val appLockManager: AppLockManager
) {
    suspend operator fun invoke() = appLockManager.onAppBackgrounded()
}

class NotifyAppForegroundedUseCase @Inject constructor(
    private val appLockManager: AppLockManager
) {
    suspend operator fun invoke() = appLockManager.onAppForegrounded()
}

class GetSecuritySnapshotUseCase @Inject constructor(
    private val appLockManager: AppLockManager
) {
    suspend operator fun invoke(): SecuritySnapshot = appLockManager.getSecuritySnapshot()
}

class SaveNewPasscodeUseCase @Inject constructor(
    private val appLockManager: AppLockManager
) {
    suspend operator fun invoke(
        passcode: String,
        biometricEnabled: Boolean,
        timeoutSeconds: Int
    ): Boolean {
        return appLockManager.saveNewPasscode(
            passcode = passcode,
            biometricEnabled = biometricEnabled,
            timeoutSeconds = timeoutSeconds
        )
    }
}

class DisableAppLockUseCase @Inject constructor(
    private val appLockManager: AppLockManager
) {
    suspend operator fun invoke() = appLockManager.disableAppLock()
}

class SetBiometricEnabledUseCase @Inject constructor(
    private val appLockManager: AppLockManager
) {
    suspend operator fun invoke(enabled: Boolean) = appLockManager.setBiometricEnabled(enabled)
}

class SetLockTimeoutUseCase @Inject constructor(
    private val appLockManager: AppLockManager
) {
    suspend operator fun invoke(seconds: Int) = appLockManager.setTimeoutSeconds(seconds)
}

class UnlockWithPasscodeUseCase @Inject constructor(
    private val appLockManager: AppLockManager
) {
    suspend operator fun invoke(passcode: String): UnlockAttemptResult {
        return appLockManager.unlockWithPasscode(passcode)
    }
}

class CompleteBiometricUnlockUseCase @Inject constructor(
    private val appLockManager: AppLockManager
) {
    suspend operator fun invoke(): UnlockAttemptResult {
        return appLockManager.completeBiometricUnlock()
    }
}

class LockForSensitiveActionUseCase @Inject constructor(
    private val appLockManager: AppLockManager
) {
    suspend operator fun invoke() = appLockManager.lockNow()
}
