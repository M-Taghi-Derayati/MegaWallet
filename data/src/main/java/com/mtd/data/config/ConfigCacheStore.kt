package com.mtd.data.config

import com.google.gson.Gson
import com.mtd.core.encryption.SecureStorage
import com.mtd.data.dto.ConfigBundleDto
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local persistence for the last network-validated config bundle. Backed by [SecureStorage] (the
 * same encrypted `secure_prefs` used for wallet data) so the cached catalog can't be tampered with
 * on-device. Only a signature-verified bundle is ever written here.
 */
@Singleton
class ConfigCacheStore @Inject constructor(
    private val secureStorage: SecureStorage,
    private val gson: Gson
) {

    /** Last validated bundle, or `null` if nothing has been cached / the blob is unreadable. */
    fun read(): ConfigBundleDto? {
        val json = secureStorage.getDecrypted(KEY)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { gson.fromJson(json, ConfigBundleDto::class.java) }
            .getOrElse { Timber.w(it, "Corrupt cached config bundle; ignoring"); null }
            ?.takeIf { !it.version.isNullOrBlank() }
    }

    /** Persist a validated bundle (overwrites any previous cache). */
    fun write(bundle: ConfigBundleDto) {
        secureStorage.putEncrypted(KEY, gson.toJson(bundle))
    }

    private companion object {
        const val KEY = "config_bundle_cache_v1"
    }
}
