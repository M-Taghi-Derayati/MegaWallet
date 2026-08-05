package com.mtd.data.config

import com.google.gson.Gson
import com.mtd.data.dto.ConfigBundleDto
import com.mtd.data.service.ConfigApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ConfigManager @Inject constructor(
    private val configApiService: ConfigApiService,
    private val signatureVerifier: ConfigSignatureVerifier,
    private val cacheStore: ConfigCacheStore,
    private val localAssetProvider: LocalConfigAssetProvider,
    private val gson: Gson
) {

    suspend fun getValidatedConfig(): ConfigBundleDto = withContext(Dispatchers.IO) {
        val rawCached = cacheStore.read()

        // Priority 0 — a cache below [MIN_BUNDLE_VERSION] is not merely stale, it is *wrong*: up to
        // v1.0.40 the bundle declared Base USDC with 18 decimals instead of 6, so every amount that
        // token formats or sends is off by 10^12. The version probe is not enough here — it only
        // refetches when the server reports a *different* version, and a probe failure deliberately
        // keeps serving the cache. So a bad cache is dropped outright and the fetch below is forced.
        //
        // This self-terminates rather than refetching on every launch: once a bundle at or above the
        // floor is cached, the check passes forever. Only a version we can positively prove is older
        // forces a refetch, so an unparseable version never traps us in a refetch loop either.
        val cached = rawCached?.takeUnless { isOlderThan(it.version, MIN_BUNDLE_VERSION) }
        if (rawCached != null && cached == null) {
            Timber.w(
                "ConfigManager: cached bundle v${rawCached.version} is below the v$MIN_BUNDLE_VERSION " +
                    "floor (wrong Base USDC decimals); discarding it and forcing a refetch"
            )
        }

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

        // Priority 3 — network failed / untrusted: stale cache, else bundled local assets. A cache
        // rejected above is *not* revived here; the APK-local seed carries the corrected decimals.
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

    /**
     * `true` only when [version] is *provably* older than [floor]. Anything we cannot parse on
     * either side answers `false`, so an unrecognised version is trusted rather than refetched
     * forever.
     */
    private fun isOlderThan(version: String?, floor: String): Boolean {
        val actual = version?.let(::numericParts) ?: return false
        val minimum = numericParts(floor) ?: return false
        for (i in 0 until maxOf(actual.size, minimum.size)) {
            val a = actual.getOrElse(i) { 0 }
            val b = minimum.getOrElse(i) { 0 }
            if (a != b) return a < b
        }
        return false
    }

    /** `"1.0.41"` → `[1, 0, 41]`; `"0.0.0-local"` → `[0, 0, 0]`; unparseable → `null`. */
    private fun numericParts(version: String): List<Int>? {
        val parts = version.trim().split('.')
        if (parts.isEmpty()) return null
        return parts.map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: return null }
    }

    private companion object {
        /**
         * Lowest bundle version whose catalog is safe to use. Bump this only for a correctness fix
         * that a normal version check would not force onto an already-cached client — v1.0.41 fixed
         * Base USDC's decimals (18 → 6).
         */
        const val MIN_BUNDLE_VERSION = "1.0.41"
    }
}
