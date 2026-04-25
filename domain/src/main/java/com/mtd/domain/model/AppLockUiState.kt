package com.mtd.domain.model

import com.mtd.domain.security.SecuritySnapshot

data class AppLockUiState(
    val isInitialized: Boolean = false,
    val isLocked: Boolean = false,
    val authPurpose: AuthPurpose = AuthPurpose.APP_LOCK,
    val authCancelNonce: Long = 0L,
    val unlockError: String? = null,
    val lockoutRemainingSeconds: Int = 0,
    val snapshot: SecuritySnapshot = SecuritySnapshot(
        appLockEnabled = false,
        passcodeConfigured = false,
        biometricEnabled = false,
        lockTimeoutSeconds = 30,
        isLocked = false
    )
)