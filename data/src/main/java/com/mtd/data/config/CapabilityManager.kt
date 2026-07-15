package com.mtd.data.config

import com.mtd.data.dto.CapabilitiesResponseDto
import com.mtd.data.dto.FeatureCapabilityDto
import com.mtd.data.dto.NetworkCapabilityDto
import com.mtd.data.service.CapabilityApiService
import com.mtd.domain.interfaceRepository.ICapabilityProvider
import com.mtd.domain.model.capability.CapabilitySnapshot
import com.mtd.domain.model.capability.FeatureCapability
import com.mtd.domain.model.capability.GaslessCapability
import com.mtd.domain.model.capability.NetworkCapability
import com.mtd.domain.model.capability.SponsorCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Capability Platform (Android Migration, Step 1) — the data-layer implementation of
 * [ICapabilityProvider], following the [ConfigManager] **Offline-First + Fallback**
 * strategy, adapted to ETag revalidation:
 *
 *  1. **Cache + revalidate** — send the cached ETag via `If-None-Match`; a `304` means
 *     "unchanged" → serve the cache with no re-parse.
 *  2. **Fresh fetch** — on `200`, map the payload to domain, cache it (with the new ETag),
 *     and return it.
 *  3. **Fail-safe** — on any network/HTTP/parse failure, return the last cached snapshot,
 *     else [CapabilitySnapshot.EMPTY]. **This method never throws.**
 *
 * Intentionally NOT wired into SendViewModel / gasless / sponsor / UnifiedTransferCoordinator
 * in this step — it is foundation only. Local Wallet Mode has no dependency on it.
 */
@Singleton
class CapabilityManager @Inject constructor(
    private val capabilityApiService: CapabilityApiService,
    private val cacheStore: CapabilityCacheStore
) : ICapabilityProvider {

    override suspend fun getCapabilities(): CapabilitySnapshot = withContext(Dispatchers.IO) {
        memorySnapshot?.takeIf { isMemoryCacheFresh() }?.let { return@withContext it }

        refreshMutex.withLock {
            memorySnapshot?.takeIf { isMemoryCacheFresh() }?.let { return@withLock it }

            fetchCapabilities().also { remember(it) }
        }
    }

    private suspend fun fetchCapabilities(): CapabilitySnapshot {
        val cached = cacheStore.read()
        return try {
            val resp = capabilityApiService.getCapabilities(cached?.etag)
            when {
                // 304 Not Modified — the cached snapshot is still current.
                resp.code() == NOT_MODIFIED && cached != null -> {
                    Timber.d("CapabilityManager: 304 — serving cached snapshot v${cached.version}")
                    cached
                }
                resp.isSuccessful && resp.body() != null -> {
                    val etag = resp.headers()["ETag"]
                    val fresh = resp.body()!!.toDomain(etag = etag, fetchedAtEpochMs = System.currentTimeMillis())
                    cacheStore.write(fresh)
                    Timber.d("CapabilityManager: cached fresh snapshot v${fresh.version} (${fresh.networks.size} networks)")
                    fresh
                }
                else -> {
                    Timber.w("CapabilityManager: HTTP ${resp.code()} — serving cache/EMPTY")
                    cached ?: CapabilitySnapshot.EMPTY
                }
            }
        } catch (e: Exception) {
            // Offline / timeout / parse error → never surface to the caller.
            Timber.w(e, "CapabilityManager: capabilities fetch failed; serving cache/EMPTY")
            cached ?: CapabilitySnapshot.EMPTY
        }
    }

    private fun isMemoryCacheFresh(): Boolean =
        memorySnapshot != null && System.currentTimeMillis() - memoryFetchedAtEpochMs < MEMORY_TTL_MS

    private fun remember(snapshot: CapabilitySnapshot) {
        memorySnapshot = snapshot
        memoryFetchedAtEpochMs = System.currentTimeMillis()
    }

    override suspend fun getNetworkCapability(networkId: String): NetworkCapability =
        getCapabilities().network(networkId) ?: NetworkCapability.unavailable(networkId)

    // --- DTO → pure domain mapping ------------------------------------------

    private fun CapabilitiesResponseDto.toDomain(etag: String?, fetchedAtEpochMs: Long): CapabilitySnapshot =
        CapabilitySnapshot(
            version = version,
            etag = etag,
            fetchedAtEpochMs = fetchedAtEpochMs,
            networks = (capabilities ?: emptyList()).mapNotNull { it.toDomain() }
        )

    private fun NetworkCapabilityDto.toDomain(): NetworkCapability? {
        val id = networkId?.takeIf { it.isNotBlank() } ?: return null
        val featureMap = (features ?: emptyMap())
            .mapNotNull { (key, dto) -> dto.toDomain(fallbackId = key)?.let { key to it } }
            .toMap()
        return NetworkCapability(
            networkId = id,
            chainId = chainId,
            relayPrefix = relayPrefix?.lowercase(),
            gasless = featureMap["gasless"].toGasless(legacy = gasless),
            sponsor = featureMap["sponsor"].toSponsor(legacy = sponsor),
            features = featureMap
        )
    }

    private fun FeatureCapabilityDto.toDomain(fallbackId: String): FeatureCapability? {
        val fid = (featureId ?: fallbackId).takeIf { it.isNotBlank() } ?: return null
        return FeatureCapability(
            featureId = fid,
            available = available == true,
            visible = visible == true,
            reasonCode = reasonCode,
            relayPrefix = relayPrefix?.lowercase(),
            minClientVersion = minClientVersion
        )
    }

    // Prefer the generic feature; fall back to the legacy network-level boolean.
    private fun FeatureCapability?.toGasless(legacy: Boolean?): GaslessCapability =
        if (this != null) GaslessCapability(available, visible, reasonCode)
        else GaslessCapability(available = legacy == true, visible = legacy == true, reasonCode = null)

    private fun FeatureCapability?.toSponsor(legacy: Boolean?): SponsorCapability =
        if (this != null) SponsorCapability(available, visible, reasonCode)
        else SponsorCapability(available = legacy == true, visible = legacy == true, reasonCode = null)

    private companion object {
        const val NOT_MODIFIED = 304
        const val MEMORY_TTL_MS = 5 * 60 * 1000L
    }

    private val refreshMutex = Mutex()
    @Volatile private var memorySnapshot: CapabilitySnapshot? = null
    @Volatile private var memoryFetchedAtEpochMs: Long = 0L
}
