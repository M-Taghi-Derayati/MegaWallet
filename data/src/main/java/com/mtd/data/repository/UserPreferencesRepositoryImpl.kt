package com.mtd.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.mtd.domain.model.IUserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val prefs: SharedPreferences
) : IUserPreferencesRepository {

    private companion object {
        const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        const val KEY_PASSCODE_HASH = "app_lock_passcode_hash"
        const val KEY_PASSCODE_SALT = "app_lock_passcode_salt"
        const val KEY_BIOMETRIC_UNLOCK_ENABLED = "biometric_unlock_enabled"
        const val KEY_LOCK_TIMEOUT_SECONDS = "lock_timeout_seconds"
        const val KEY_LAST_BACKGROUND_AT = "last_background_at"
        const val KEY_FAILED_UNLOCK_ATTEMPTS = "failed_unlock_attempts"
        const val KEY_LOCKOUT_UNTIL = "lockout_until"
        const val DEFAULT_LOCK_TIMEOUT_SECONDS = 30
    }


    override suspend fun isAppLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
    }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_APP_LOCK_ENABLED, enabled) }
    }

    override suspend fun getPasscodeHash(): String? {
        return prefs.getString(KEY_PASSCODE_HASH, null)
    }

    override suspend fun getPasscodeSalt(): String? {
        return prefs.getString(KEY_PASSCODE_SALT, null)
    }

    override suspend fun savePasscodeHash(hash: String, salt: String) {
        prefs.edit {
            putString(KEY_PASSCODE_HASH, hash)
            putString(KEY_PASSCODE_SALT, salt)
        }
    }

    override suspend fun clearPasscode() {
        prefs.edit {
            remove(KEY_PASSCODE_HASH)
            remove(KEY_PASSCODE_SALT)
            putInt(KEY_FAILED_UNLOCK_ATTEMPTS, 0)
            putLong(KEY_LOCKOUT_UNTIL, 0L)
        }
    }

    override suspend fun isBiometricUnlockEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_UNLOCK_ENABLED, false)
    }

    override suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BIOMETRIC_UNLOCK_ENABLED, enabled) }
    }

    override suspend fun getLockTimeoutSeconds(): Int {
        return prefs.getInt(KEY_LOCK_TIMEOUT_SECONDS, DEFAULT_LOCK_TIMEOUT_SECONDS)
    }

    override suspend fun setLockTimeoutSeconds(seconds: Int) {
        prefs.edit { putInt(KEY_LOCK_TIMEOUT_SECONDS, seconds.coerceAtLeast(0)) }
    }

    override suspend fun getLastBackgroundAt(): Long {
        return prefs.getLong(KEY_LAST_BACKGROUND_AT, 0L)
    }

    override suspend fun setLastBackgroundAt(timestampMs: Long) {
        prefs.edit { putLong(KEY_LAST_BACKGROUND_AT, timestampMs) }
    }

    override suspend fun getFailedUnlockAttempts(): Int {
        return prefs.getInt(KEY_FAILED_UNLOCK_ATTEMPTS, 0)
    }

    override suspend fun setFailedUnlockAttempts(count: Int) {
        prefs.edit { putInt(KEY_FAILED_UNLOCK_ATTEMPTS, count.coerceAtLeast(0)) }
    }

    override suspend fun getLockoutUntil(): Long {
        return prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
    }

    override suspend fun setLockoutUntil(timestampMs: Long) {
        prefs.edit { putLong(KEY_LOCKOUT_UNTIL, timestampMs.coerceAtLeast(0L)) }
    }
}
