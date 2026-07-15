package com.mtd.domain.model.capability

/**
 * Capability Platform (Android Migration, Step 1) — PURE domain models for the
 * server-driven capability snapshot. These describe "what optional Server-Enhanced
 * features are available right now", per network.
 *
 * Deliberately framework-free: no Android, no Retrofit, no Gson/DTO, no data-layer
 * types. The data layer maps its wire DTOs onto these; Local Wallet Mode never
 * needs them. Absence of capability MUST degrade gracefully — see [CapabilitySnapshot.EMPTY]
 * and the `unavailable(...)` factories — so the wallet core can ignore this entirely.
 */
data class CapabilitySnapshot(
    /** Backend catalog/capability version, when known (opaque to the client). */
    val version: String? = null,
    /** Opaque freshness token (HTTP ETag) used for cheap `If-None-Match` revalidation. */
    val etag: String? = null,
    /** When this snapshot was obtained (epoch ms); 0 = unknown / [EMPTY]. */
    val fetchedAtEpochMs: Long = 0L,
    val networks: List<NetworkCapability> = emptyList()
) {
    fun network(networkId: String): NetworkCapability? =
        networks.firstOrNull { it.networkId == networkId }

    val isEmpty: Boolean get() = networks.isEmpty() && fetchedAtEpochMs == 0L

    companion object {
        /** The safe "nothing known" state — returned when the backend was never reachable. */
        val EMPTY = CapabilitySnapshot()
    }
}

data class NetworkCapability(
    val networkId: String,
    val chainId: Long? = null,
    /** Canonical, lowercase relay path segment (e.g. "evm", "tron", "base"); null if no relayer. */
    val relayPrefix: String? = null,
    val gasless: GaslessCapability = GaslessCapability.UNAVAILABLE,
    val sponsor: SponsorCapability = SponsorCapability.UNAVAILABLE,
    /** Generic, forward-compatible feature map (gasless/sponsor mirrored here too). */
    val features: Map<String, FeatureCapability> = emptyMap()
) {
    fun feature(featureId: String): FeatureCapability? = features[featureId]

    companion object {
        /** Safe default for an unknown / unreachable network — nothing is offered. */
        fun unavailable(networkId: String) = NetworkCapability(networkId = networkId)
    }
}

/**
 * Generic per-feature capability. New server features (swap, bridge, …) flow through
 * this same shape with no model change; unknown [featureId]s are simply ignored by
 * clients that don't render them.
 */
data class FeatureCapability(
    val featureId: String,
    val available: Boolean = false,
    val visible: Boolean = false,
    val reasonCode: String? = null,
    val relayPrefix: String? = null,
    val minClientVersion: Map<String, Int>? = null
) {
    companion object {
        fun unavailable(featureId: String) =
            FeatureCapability(featureId = featureId, available = false, visible = false, reasonCode = "NETWORK_NOT_SUPPORTED")
    }
}

/** Typed convenience view of the "gasless" feature (the generic map remains source of truth). */
data class GaslessCapability(
    val available: Boolean = false,
    val visible: Boolean = false,
    val reasonCode: String? = null
) {
    companion object { val UNAVAILABLE = GaslessCapability() }
}

/** Typed convenience view of the "sponsor" feature. */
data class SponsorCapability(
    val available: Boolean = false,
    val visible: Boolean = false,
    val reasonCode: String? = null
) {
    companion object { val UNAVAILABLE = SponsorCapability() }
}
