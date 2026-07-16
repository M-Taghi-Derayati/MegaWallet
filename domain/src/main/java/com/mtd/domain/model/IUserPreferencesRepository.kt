package com.mtd.domain.model


interface IUserPreferencesRepository {


    /**
     * KAN-9 / KAN-19 — transport mode for blockchain reads/writes (DIRECT RPC vs. backend PROXY).
     * Persisted so the choice survives process death; defaults to [BlockchainConnectionMode.DIRECT].
     */
    suspend fun getConnectionMode(): BlockchainConnectionMode
    suspend fun setConnectionMode(mode: BlockchainConnectionMode)

    /**
     * وضعیت فعال بودن قفل اپ.
     */
    suspend fun isAppLockEnabled(): Boolean
    suspend fun setAppLockEnabled(enabled: Boolean)

    /**
     * هش و salt مربوط به passcode.
     */
    suspend fun getPasscodeHash(): String?
    suspend fun getPasscodeSalt(): String?
    suspend fun savePasscodeHash(hash: String, salt: String)
    suspend fun clearPasscode()

    /**
     * تنظیمات بیومتریک و تایم‌اوت.
     */
    suspend fun isBiometricUnlockEnabled(): Boolean
    suspend fun setBiometricUnlockEnabled(enabled: Boolean)
    suspend fun getLockTimeoutSeconds(): Int
    suspend fun setLockTimeoutSeconds(seconds: Int)

    /**
     * وضعیت موقت امنیتی (برای لاک‌اوت و زمان بک‌گراند).
     */
    suspend fun getLastBackgroundAt(): Long
    suspend fun setLastBackgroundAt(timestampMs: Long)
    suspend fun getFailedUnlockAttempts(): Int
    suspend fun setFailedUnlockAttempts(count: Int)
    suspend fun getLockoutUntil(): Long
    suspend fun setLockoutUntil(timestampMs: Long)

    /**
     * TASK-32 — wallet ids already enrolled for backend monitoring (`/monitoring/subscribe`).
     * Persisted so enrollment fires **once per wallet** (on create/import), never again on a plain
     * wallet switch. Pruned when a wallet is deleted so a re-import re-enrolls.
     */
    suspend fun getMonitoringSubscribedWalletIds(): Set<String>
    suspend fun setMonitoringSubscribedWalletIds(ids: Set<String>)
}
