package com.mtd.data.repository.auth

import com.mtd.core.encryption.SecureStorage
import com.mtd.domain.interfaceRepository.ITokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ITokenStore] backed by the AndroidKeystore-encrypted [SecureStorage] (AES-256-GCM).
 * The JWT is a bearer credential, so it lives under the same protection as wallet secrets.
 */
@Singleton
class SecureTokenStore @Inject constructor(
    private val secureStorage: SecureStorage
) : ITokenStore {

    override fun getTokenDevice(): String? =
        secureStorage.getDecrypted(KEY_TOKEN)?.takeIf { it.isNotBlank() }

    override fun getDeviceId(): String? =
        secureStorage.getDecrypted(KEY_DEVICE_ID)?.takeIf { it.isNotBlank() }

    override fun isTokenValid(nowEpochSec: Long): Boolean {
        if (getTokenDevice() == null) return false
        val expiresAt = getExpiresAtEpochSec() ?: return false
        return nowEpochSec < (expiresAt - CLOCK_SKEW_SEC)
    }

    override fun getExpiresAtEpochSec(): Long? =
        secureStorage.getDecrypted(KEY_EXPIRES_AT)?.toLongOrNull()?.takeIf { it > 0L }

    override fun save(token: String, expiresAtEpochSec: Long, deviceId: String?) {
        secureStorage.putEncrypted(KEY_TOKEN, token)
        secureStorage.putEncrypted(KEY_EXPIRES_AT, expiresAtEpochSec.toString())
        if (deviceId != null) secureStorage.putEncrypted(KEY_DEVICE_ID, deviceId)
    }

    override fun clear() {
        secureStorage.putEncrypted(KEY_TOKEN, "")
        secureStorage.putEncrypted(KEY_EXPIRES_AT, "0")
        secureStorage.putEncrypted(KEY_DEVICE_ID, "")
    }

    private companion object {
        const val KEY_TOKEN = "auth_jwt"
        const val KEY_EXPIRES_AT = "auth_jwt_expires_at"
        const val KEY_DEVICE_ID = "auth_device_id"
        const val CLOCK_SKEW_SEC = 30L
    }
}
