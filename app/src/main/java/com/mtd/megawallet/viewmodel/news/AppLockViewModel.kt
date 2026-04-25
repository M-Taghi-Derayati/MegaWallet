package com.mtd.megawallet.viewmodel.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtd.domain.model.AppLockUiState
import com.mtd.domain.model.AuthPurpose
import com.mtd.domain.security.AppLockManager
import com.mtd.domain.security.UnlockAttemptResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val appLockManager: AppLockManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    private var lockoutCountdownJob: Job? = null
    private var authCancelNonce: Long = 0L

    init {
        viewModelScope.launch {
            appLockManager.isLocked.collect { locked ->
                _uiState.value = _uiState.value.copy(
                    isLocked = locked,
                    authPurpose = if (locked) _uiState.value.authPurpose else AuthPurpose.APP_LOCK
                )
            }
        }
        viewModelScope.launch {
            appLockManager.isInitialized.collect { initialized ->
                _uiState.value = _uiState.value.copy(isInitialized = initialized)
            }
        }
    }

    fun initialize() {
        viewModelScope.launch {
            appLockManager.initialize()
            refreshSnapshot()
        }
    }

    fun onAppBackgrounded() {
        viewModelScope.launch {
            appLockManager.onAppBackgrounded()
        }
    }

    fun onAppForegrounded() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(authPurpose = AuthPurpose.APP_LOCK)
            appLockManager.onAppForegrounded()
            refreshSnapshot()
        }
    }

    fun refreshSnapshot() {
        viewModelScope.launch {
            val snapshot = appLockManager.getSecuritySnapshot()
            _uiState.value = _uiState.value.copy(snapshot = snapshot)
        }
    }

    fun saveNewPasscode(passcode: String, biometricEnabled: Boolean, timeoutSeconds: Int = 30, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = appLockManager.saveNewPasscode(
                passcode = passcode,
                biometricEnabled = biometricEnabled,
                timeoutSeconds = timeoutSeconds
            )
            if (ok) {
                _uiState.value = _uiState.value.copy(
                    unlockError = null,
                    lockoutRemainingSeconds = 0
                )
                refreshSnapshot()
            }
            onDone(ok)
        }
    }

    fun disableAppLock() {
        viewModelScope.launch {
            appLockManager.disableAppLock()
            _uiState.value = _uiState.value.copy(
                unlockError = null,
                lockoutRemainingSeconds = 0
            )
            refreshSnapshot()
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appLockManager.setBiometricEnabled(enabled)
            refreshSnapshot()
        }
    }

    fun setTimeoutSeconds(seconds: Int) {
        viewModelScope.launch {
            appLockManager.setTimeoutSeconds(seconds)
            refreshSnapshot()
        }
    }

    fun unlockWithPasscode(passcode: String) {
        viewModelScope.launch {
            when (val result = appLockManager.unlockWithPasscode(passcode)) {
                UnlockAttemptResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        unlockError = null,
                        lockoutRemainingSeconds = 0,
                        authPurpose = AuthPurpose.APP_LOCK
                    )
                    refreshSnapshot()
                }

                UnlockAttemptResult.NotConfigured -> {
                    _uiState.value = _uiState.value.copy(unlockError = "قفل برنامه تنظیم نشده است")
                }

                is UnlockAttemptResult.InvalidPasscode -> {
                    _uiState.value = _uiState.value.copy(
                        unlockError = "رمز اشتباه است ${result.remainingAttempts} تلاش باقی مانده"
                    )
                }

                is UnlockAttemptResult.LockedOut -> {
                    startLockoutCountdown(result.remainingMs)
                }
            }
        }
    }

    fun completeBiometricUnlock() {
        viewModelScope.launch {
            when (appLockManager.completeBiometricUnlock()) {
                UnlockAttemptResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        unlockError = null,
                        lockoutRemainingSeconds = 0,
                        authPurpose = AuthPurpose.APP_LOCK
                    )
                    refreshSnapshot()
                }

                UnlockAttemptResult.NotConfigured -> {
                    _uiState.value = _uiState.value.copy(unlockError = "قفل برنامه تنظیم نشده است")
                }

                else -> Unit
            }
        }
    }

    fun onBiometricError(message: String) {
        _uiState.value = _uiState.value.copy(unlockError = message.ifBlank { "تایید اثر انگشت ناموفق بود" })
    }

    fun lockNowForSensitiveAction() {
        viewModelScope.launch {
            appLockManager.lockNow()
            _uiState.value = _uiState.value.copy(authPurpose = AuthPurpose.SENSITIVE_ACTION)
            refreshSnapshot()
        }
    }

    fun cancelSensitiveAuthRequest() {
        viewModelScope.launch {
            if (_uiState.value.authPurpose != AuthPurpose.SENSITIVE_ACTION) return@launch
            when (appLockManager.completeBiometricUnlock()) {
                UnlockAttemptResult.Success -> {
                    authCancelNonce += 1
                    _uiState.value = _uiState.value.copy(
                        unlockError = null,
                        lockoutRemainingSeconds = 0,
                        authPurpose = AuthPurpose.APP_LOCK,
                        authCancelNonce = authCancelNonce
                    )
                    refreshSnapshot()
                }

                else -> Unit
            }
        }
    }

    fun clearUnlockError() {
        _uiState.value = _uiState.value.copy(unlockError = null)
    }

    private fun startLockoutCountdown(remainingMs: Long) {
        lockoutCountdownJob?.cancel()
        lockoutCountdownJob = viewModelScope.launch {
            var remaining = (remainingMs / 1000L).toInt().coerceAtLeast(1)
            _uiState.value = _uiState.value.copy(
                unlockError = "به دلیل تلاش ناموفق متعدد، موقتاً قفل شد",
                lockoutRemainingSeconds = remaining
            )
            while (remaining > 0) {
                delay(1000)
                remaining--
                _uiState.value = _uiState.value.copy(lockoutRemainingSeconds = remaining)
            }
            _uiState.value = _uiState.value.copy(unlockError = null)
            refreshSnapshot()
        }
    }
}




