package com.mtd.data.config

import com.google.gson.Gson
import com.mtd.data.dto.ConfigBundleDto
import com.mtd.data.service.ConfigApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 — dynamic config authority with a strict **Offline-First + Fallback** strategy.
 *
 * [getValidatedConfig] resolves the network/asset catalog in this exact priority order:
 *
 *  1. **Local cache (fastest)** — if a previously validated bundle is cached AND a cheap
 *     `/config/version` probe confirms it is still the latest (or the probe can't be reached, i.e.
 *     we are offline), return the cache immediately with no full fetch.
 *  2. **Network fetch (freshness)** — otherwise fetch `/config/bundle`, verify its secp256k1
 *     signature against the pinned public key, cache it on success, and return it.
 *  3. **Local asset fallback (resilience)** — if the network fails (timeout / IOException / 5xx) or
 *     the signature can't be trusted, return the last validated cache if present, else the bundled
 *     `networks.json` / `assets.json`. This path NEVER surfaces an error to the caller.
 *
 * The bundled local assets are intentionally retained in `core/src/main/assets/` and are trusted
 * without signature verification.
 */
@Singleton
class ConfigManager @Inject constructor(
    private val configApiService: ConfigApiService,
    private val signatureVerifier: ConfigSignatureVerifier,
    private val cacheStore: ConfigCacheStore,
    private val localAssetProvider: LocalConfigAssetProvider,
    private val gson: Gson
) {

    suspend fun getValidatedConfig(): ConfigBundleDto = withContext(Dispatchers.IO) {
        val cached = cacheStore.read()

        // Priority 1 — serve the cache when it is (or cannot be disproven to be) the latest version.
        if (cached != null) {
            val latestVersion = probeLatestVersion()
            if (latestVersion == null || latestVersion == cached.version) {
                Timber.d("ConfigManager: serving cached bundle v${cached.version}")
                return@withContext cached
            }
            Timber.d("ConfigManager: cache v${cached.version} stale (latest=$latestVersion); refreshing")
        }

        // Priority 2 — fetch + verify a fresh signed bundle.
        fetchAndVerify()?.let { fresh ->
            cacheStore.write(fresh)
            Timber.d("ConfigManager: cached fresh bundle v${fresh.version}")
            return@withContext fresh
        }

        // Priority 3 — network failed / untrusted: stale cache, else bundled local assets.
        Timber.w("ConfigManager: network config unavailable; falling back to local source")
        cached ?: localAssetProvider.load()
    }

    /** Cheap `/config/version` probe. `null` ⇒ unreachable (treated as "can't disprove cache"). */
    private suspend fun probeLatestVersion(): String? {
        return try {
            val resp = configApiService.getConfigVersion()
            if (resp.isSuccessful) resp.body()?.version?.takeIf { it.isNotBlank() } else null
        } catch (e: Exception) {
            Timber.w(e, "ConfigManager: version probe failed")
            null
        }
    }

    /** Fetch the raw bundle, verify its signature, and parse it. `null` on any network/trust failure. */
    private suspend fun fetchAndVerify(): ConfigBundleDto? {
        return try {
            val resp = configApiService.getConfigBundleRaw()
            val raw = resp.body()?.string()
            if (!resp.isSuccessful || raw.isNullOrBlank()) {
                Timber.w("ConfigManager: bundle fetch failed (HTTP ${resp.code()})")
                return null
            }
            if (!signatureVerifier.verify(raw)) {
                Timber.w("ConfigManager: bundle signature invalid; ignoring network bundle")
                return null
            }
            gson.fromJson(raw, ConfigBundleDto::class.java)?.takeIf { !it.version.isNullOrBlank() }
        } catch (e: Exception) {
            Timber.w(e, "ConfigManager: bundle fetch threw")
            null
        }
    }
}
