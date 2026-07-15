package com.mtd.data.config

import com.google.gson.Gson
import com.mtd.core.encryption.SecureStorage
import com.mtd.domain.model.capability.CapabilitySnapshot
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Capability Platform (Android Migration, Step 1) — last-known capability snapshot,
 * persisted in [SecureStorage] (the same encrypted prefs the config cache uses) so the
 * offline-first manager can serve and revalidate it. Mirrors [ConfigCacheStore].
 *
 * The snapshot carries its own ETag, so caching the domain object also caches the
 * revalidation token. A corrupt blob is treated as "no cache" (never throws).
 */
@Singleton
class CapabilityCacheStore @Inject constructor(
    private val secureStorage: SecureStorage,
    private val gson: Gson
) {

    /** Last cached snapshot, or `null` if nothing has been stored / the blob is unreadable. */
    fun read(): CapabilitySnapshot? {
        val json = secureStorage.getDecrypted(KEY)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { gson.fromJson(json, CapabilitySnapshot::class.java) }
            .getOrElse { Timber.w(it, "Corrupt cached capability snapshot; ignoring"); null }
    }

    /** Persist a snapshot (overwrites any previous cache). */
    fun write(snapshot: CapabilitySnapshot) {
        secureStorage.putEncrypted(KEY, gson.toJson(snapshot))
    }

    private companion object {
        const val KEY = "capability_snapshot_cache_v1"
    }
}
