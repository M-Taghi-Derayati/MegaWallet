package com.mtd.domain.model


interface IUserPreferencesRepository {


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
}
