package com.mtd.domain.security

import com.mtd.domain.model.IUserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockManager @Inject constructor(
    private val userPreferencesRepository: IUserPreferencesRepository
) {

    companion object {
        const val PASSCODE_LENGTH = 6
        private const val DEFAULT_LOCK_TIMEOUT_SECONDS = 30
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 30_000L
    }

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    suspend fun initialize() {
        if (_isInitialized.value) return
        _isLocked.value = isPasscodeConfigured()
        _isInitialized.value = true
    }

    suspend fun onAppBackgrounded() {
        if (!isPasscodeConfigured()) return
        userPreferencesRepository.setLastBackgroundAt(System.currentTimeMillis())
    }

    suspend fun onAppForegrounded() {
        if (!isPasscodeConfigured()) {
            _isLocked.value = false
            return
        }

        if (_isLocked.value) return

        val lastBackgroundAt = userPreferencesRepository.getLastBackgroundAt()
        if (lastBackgroundAt <= 0L) return

        val timeoutSeconds = userPreferencesRepository.getLockTimeoutSeconds()
            .takeIf { it >= 0 }
            ?: DEFAULT_LOCK_TIMEOUT_SECONDS

        val elapsedMs = System.currentTimeMillis() - lastBackgroundAt
        val shouldLock = timeoutSeconds == 0 || elapsedMs >= timeoutSeconds * 1000L
        if (shouldLock) _isLocked.value = true
    }

    suspend fun lockNow() {
        if (isPasscodeConfigured()) _isLocked.value = true
    }

    suspend fun completeBiometricUnlock(): UnlockAttemptResult {
        if (!isPasscodeConfigured()) return UnlockAttemptResult.NotConfigured
        resetUnlockFailures()
        _isLocked.value = false
        return UnlockAttemptResult.Success
    }

    suspend fun unlockWithPasscode(passcode: String): UnlockAttemptResult {
        if (!isPasscodeConfigured()) return UnlockAttemptResult.NotConfigured

        val lockoutUntil = userPreferencesRepository.getLockoutUntil()
        val now = System.currentTimeMillis()
        if (lockoutUntil > now) {
            return UnlockAttemptResult.LockedOut(lockoutUntil - now)
        }

        val storedSalt = userPreferencesRepository.getPasscodeSalt().orEmpty()
        val storedHash = userPreferencesRepository.getPasscodeHash().orEmpty()
        if (storedSalt.isBlank() || storedHash.isBlank()) {
            return UnlockAttemptResult.NotConfigured
        }

        val isValid = PasscodeCrypto.verifyPasscode(
            passcode = passcode,
            saltBase64 = storedSalt,
            expectedHashBase64 = storedHash
        )

        if (isValid) {
            resetUnlockFailures()
            _isLocked.value = false
            return UnlockAttemptResult.Success
        }

        val failedAttempts = userPreferencesRepository.getFailedUnlockAttempts() + 1
        userPreferencesRepository.setFailedUnlockAttempts(failedAttempts)

        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            val until = now + LOCKOUT_DURATION_MS
            userPreferencesRepository.setLockoutUntil(until)
            userPreferencesRepository.setFailedUnlockAttempts(0)
            return UnlockAttemptResult.LockedOut(LOCKOUT_DURATION_MS)
        }

        return UnlockAttemptResult.InvalidPasscode(MAX_FAILED_ATTEMPTS - failedAttempts)
    }

    suspend fun saveNewPasscode(
        passcode: String,
        biometricEnabled: Boolean,
        timeoutSeconds: Int = DEFAULT_LOCK_TIMEOUT_SECONDS
    ): Boolean {
        if (passcode.length != PASSCODE_LENGTH || passcode.any { !it.isDigit() }) return false

        val salt = PasscodeCrypto.generateSalt()
        val hash = PasscodeCrypto.hashPasscode(passcode, salt)

        userPreferencesRepository.savePasscodeHash(hash = hash, salt = salt)
        userPreferencesRepository.setAppLockEnabled(true)
        userPreferencesRepository.setBiometricUnlockEnabled(biometricEnabled)
        userPreferencesRepository.setLockTimeoutSeconds(timeoutSeconds.coerceAtLeast(0))
        resetUnlockFailures()
        _isLocked.value = false
        _isInitialized.value = true
        return true
    }

    suspend fun disableAppLock() {
        userPreferencesRepository.setAppLockEnabled(false)
        userPreferencesRepository.setBiometricUnlockEnabled(false)
        userPreferencesRepository.clearPasscode()
        userPreferencesRepository.setLastBackgroundAt(0L)
        userPreferencesRepository.setLockoutUntil(0L)
        userPreferencesRepository.setFailedUnlockAttempts(0)
        _isLocked.value = false
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        userPreferencesRepository.setBiometricUnlockEnabled(enabled)
    }

    suspend fun setTimeoutSeconds(seconds: Int) {
        userPreferencesRepository.setLockTimeoutSeconds(seconds.coerceAtLeast(0))
    }

    suspend fun getSecuritySnapshot(): SecuritySnapshot {
        val appLockEnabled = userPreferencesRepository.isAppLockEnabled()
        val passcodeConfigured = isPasscodeConfigured()
        return SecuritySnapshot(
            appLockEnabled = appLockEnabled && passcodeConfigured,
            passcodeConfigured = passcodeConfigured,
            biometricEnabled = userPreferencesRepository.isBiometricUnlockEnabled(),
            lockTimeoutSeconds = userPreferencesRepository.getLockTimeoutSeconds()
                .takeIf { it >= 0 } ?: DEFAULT_LOCK_TIMEOUT_SECONDS,
            isLocked = _isLocked.value
        )
    }

    private suspend fun isPasscodeConfigured(): Boolean {
        if (!userPreferencesRepository.isAppLockEnabled()) return false
        val hash = userPreferencesRepository.getPasscodeHash()
        val salt = userPreferencesRepository.getPasscodeSalt()
        return !hash.isNullOrBlank() && !salt.isNullOrBlank()
    }

    private suspend fun resetUnlockFailures() {
        userPreferencesRepository.setFailedUnlockAttempts(0)
        userPreferencesRepository.setLockoutUntil(0L)
    }
}

data class SecuritySnapshot(
    val appLockEnabled: Boolean,
    val passcodeConfigured: Boolean,
    val biometricEnabled: Boolean,
    val lockTimeoutSeconds: Int,
    val isLocked: Boolean
)

sealed interface UnlockAttemptResult {
    data object Success : UnlockAttemptResult
    data object NotConfigured : UnlockAttemptResult
    data class InvalidPasscode(val remainingAttempts: Int) : UnlockAttemptResult
    data class LockedOut(val remainingMs: Long) : UnlockAttemptResult
}
