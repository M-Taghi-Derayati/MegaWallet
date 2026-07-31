package com.mtd.megawallet.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtd.domain.model.AppLockUiState
import com.mtd.domain.model.AuthPurpose
import com.mtd.domain.security.UnlockAttemptResult
import com.mtd.domain.usecase.security.CompleteBiometricUnlockUseCase
import com.mtd.domain.usecase.security.DisableAppLockUseCase
import com.mtd.domain.usecase.security.GetSecuritySnapshotUseCase
import com.mtd.domain.usecase.security.InitializeAppLockUseCase
import com.mtd.domain.usecase.security.LockForSensitiveActionUseCase
import com.mtd.domain.usecase.security.NotifyAppBackgroundedUseCase
import com.mtd.domain.usecase.security.NotifyAppForegroundedUseCase
import com.mtd.domain.usecase.security.ObserveAppLockInitializedUseCase
import com.mtd.domain.usecase.security.ObserveAppLockedUseCase
import com.mtd.domain.usecase.security.SaveNewPasscodeUseCase
import com.mtd.domain.usecase.security.SetBiometricEnabledUseCase
import com.mtd.domain.usecase.security.SetLockTimeoutUseCase
import com.mtd.domain.usecase.security.UnlockWithPasscodeUseCase
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
    private val observeAppLockedUseCase: ObserveAppLockedUseCase,
    private val observeAppLockInitializedUseCase: ObserveAppLockInitializedUseCase,
    private val initializeAppLockUseCase: InitializeAppLockUseCase,
    private val notifyAppBackgroundedUseCase: NotifyAppBackgroundedUseCase,
    private val notifyAppForegroundedUseCase: NotifyAppForegroundedUseCase,
    private val getSecuritySnapshotUseCase: GetSecuritySnapshotUseCase,
    private val saveNewPasscodeUseCase: SaveNewPasscodeUseCase,
    private val disableAppLockUseCase: DisableAppLockUseCase,
    private val setBiometricEnabledUseCase: SetBiometricEnabledUseCase,
    private val setLockTimeoutUseCase: SetLockTimeoutUseCase,
    private val unlockWithPasscodeUseCase: UnlockWithPasscodeUseCase,
    private val completeBiometricUnlockUseCase: CompleteBiometricUnlockUseCase,
    private val lockForSensitiveActionUseCase: LockForSensitiveActionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    private var lockoutCountdownJob: Job? = null
    private var authCancelNonce: Long = 0L

    init {
        viewModelScope.launch {
            observeAppLockedUseCase().collect { locked ->
                _uiState.value = _uiState.value.copy(
                    isLocked = locked,
                    authPurpose = if (locked) _uiState.value.authPurpose else AuthPurpose.APP_LOCK
                )
            }
        }
        viewModelScope.launch {
            observeAppLockInitializedUseCase().collect { initialized ->
                _uiState.value = _uiState.value.copy(isInitialized = initialized)
            }
        }
    }

    fun initialize() {
        viewModelScope.launch {
            initializeAppLockUseCase()
            refreshSnapshot()
        }
    }

    fun onAppBackgrounded() {
        viewModelScope.launch {
            notifyAppBackgroundedUseCase()
        }
    }

    fun onAppForegrounded() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(authPurpose = AuthPurpose.APP_LOCK)
            notifyAppForegroundedUseCase()
            refreshSnapshot()
        }
    }

    fun refreshSnapshot() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(snapshot = getSecuritySnapshotUseCase())
        }
    }

    fun saveNewPasscode(
        passcode: String,
        biometricEnabled: Boolean,
        timeoutSeconds: Int = 30,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val ok = saveNewPasscodeUseCase(
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
            disableAppLockUseCase()
            _uiState.value = _uiState.value.copy(
                unlockError = null,
                lockoutRemainingSeconds = 0
            )
            refreshSnapshot()
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            setBiometricEnabledUseCase(enabled)
            refreshSnapshot()
        }
    }

    fun setTimeoutSeconds(seconds: Int) {
        viewModelScope.launch {
            setLockTimeoutUseCase(seconds)
            refreshSnapshot()
        }
    }

    fun unlockWithPasscode(passcode: String) {
        viewModelScope.launch {
            when (val result = unlockWithPasscodeUseCase(passcode)) {
                UnlockAttemptResult.Success -> onUnlockSucceeded()
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
            when (completeBiometricUnlockUseCase()) {
                UnlockAttemptResult.Success -> onUnlockSucceeded()
                UnlockAttemptResult.NotConfigured -> {
                    _uiState.value = _uiState.value.copy(unlockError = "قفل برنامه تنظیم نشده است")
                }
                else -> Unit
            }
        }
    }

    fun onBiometricError(message: String) {
        _uiState.value = _uiState.value.copy(
            unlockError = message.ifBlank { "تایید اثر انگشت ناموفق بود" }
        )
    }

    fun lockNowForSensitiveAction() {
        viewModelScope.launch {
            lockForSensitiveActionUseCase()
            _uiState.value = _uiState.value.copy(authPurpose = AuthPurpose.SENSITIVE_ACTION)
            refreshSnapshot()
        }
    }

    fun cancelSensitiveAuthRequest() {
        viewModelScope.launch {
            if (_uiState.value.authPurpose != AuthPurpose.SENSITIVE_ACTION) return@launch
            when (completeBiometricUnlockUseCase()) {
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

    private fun onUnlockSucceeded() {
        _uiState.value = _uiState.value.copy(
            unlockError = null,
            lockoutRemainingSeconds = 0,
            authPurpose = AuthPurpose.APP_LOCK
        )
        refreshSnapshot()
    }

    private fun startLockoutCountdown(remainingMs: Long) {
        lockoutCountdownJob?.cancel()
        lockoutCountdownJob = viewModelScope.launch {
            var remaining = (remainingMs / 1000L).toInt().coerceAtLeast(1)
            _uiState.value = _uiState.value.copy(
                unlockError = "به دلیل تلاش ناموفق متعدد، موقتا قفل شد",
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
