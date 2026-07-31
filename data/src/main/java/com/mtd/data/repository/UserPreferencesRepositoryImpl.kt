package com.mtd.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.mtd.data.BuildConfig
import com.mtd.domain.model.BlockchainConnectionMode
import com.mtd.domain.model.FiatCurrency
import com.mtd.domain.interfaceRepository.IUserPreferencesRepository
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
        const val KEY_CONNECTION_MODE = "blockchain_connection_mode"
        const val KEY_SHOW_TESTNETS = "show_testnets"
        const val KEY_FIAT_CURRENCY = "fiat_currency"
        const val KEY_MONITORING_SUBSCRIBED_WALLET_IDS = "monitoring_subscribed_wallet_ids"
        const val KEY_REGISTERED_FCM_TOKEN = "registered_fcm_token"
        const val DEFAULT_LOCK_TIMEOUT_SECONDS = 30
    }

    override suspend fun getConnectionMode(): BlockchainConnectionMode {
        val stored = prefs.getString(KEY_CONNECTION_MODE, null)
        return runCatching { BlockchainConnectionMode.valueOf(stored ?: "") }
            .getOrDefault(BlockchainConnectionMode.DIRECT)
    }

    /**
     * TASK-53 — پیش‌فرض به نوعِ بیلد بسته است: در بیلدهای دیباگ شبکه‌های تست دیده می‌شوند،
     * در ریلیز نه. این‌جا تعیین می‌شود چون BuildConfig در لایهٔ `data` است، نه `domain`.
     */
    override suspend fun getShowTestnets(): Boolean =
        prefs.getBoolean(KEY_SHOW_TESTNETS, BuildConfig.DEBUG)

    override suspend fun setShowTestnets(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_TESTNETS, show) }
    }

    override suspend fun setConnectionMode(mode: BlockchainConnectionMode) {
        prefs.edit { putString(KEY_CONNECTION_MODE, mode.name) }
    }

    override suspend fun getFiatCurrency(): FiatCurrency =
        FiatCurrency.fromNameOrDefault(prefs.getString(KEY_FIAT_CURRENCY, null))

    override suspend fun setFiatCurrency(currency: FiatCurrency) {
        prefs.edit { putString(KEY_FIAT_CURRENCY, currency.name) }
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

    override suspend fun getMonitoringSubscribedWalletIds(): Set<String> {
        // Copy — the Set returned by SharedPreferences must not be mutated or retained.
        return prefs.getStringSet(KEY_MONITORING_SUBSCRIBED_WALLET_IDS, emptySet())
            ?.toSet()
            ?: emptySet()
    }

    override suspend fun setMonitoringSubscribedWalletIds(ids: Set<String>) {
        prefs.edit { putStringSet(KEY_MONITORING_SUBSCRIBED_WALLET_IDS, ids) }
    }

    override suspend fun getRegisteredFcmToken(): String? {
        return prefs.getString(KEY_REGISTERED_FCM_TOKEN, null)
    }

    override suspend fun setRegisteredFcmToken(token: String?) {
        prefs.edit {
            if (token.isNullOrBlank()) remove(KEY_REGISTERED_FCM_TOKEN)
            else putString(KEY_REGISTERED_FCM_TOKEN, token)
        }
    }
}
