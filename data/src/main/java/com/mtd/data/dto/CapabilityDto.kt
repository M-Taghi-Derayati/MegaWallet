package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

/**
 * Capability Platform (Android Migration, Step 1) — wire DTOs for
 * `GET /api/v1/capabilities`. Mirrors the backend payload; mapped onto the pure
 * domain models in [com.mtd.data.config.CapabilityManager]. Unknown fields decode
 * to null and are ignored (forward-compatible).
 *
 * Payload shape:
 * { ok, ts, version, capabilities: [ { networkId, chainId, relayPrefix, mounted,
 *     gasless, sponsor, advertised:{...}, features:{ "gasless": {…}, "sponsor": {…} } } ] }
 */
data class CapabilitiesResponseDto(
    @SerializedName("ok") val ok: Boolean? = null,
    @SerializedName("version") val version: String? = null,
    @SerializedName("capabilities") val capabilities: List<NetworkCapabilityDto>? = null
)

data class NetworkCapabilityDto(
    @SerializedName("networkId") val networkId: String? = null,
    @SerializedName("chainId") val chainId: Long? = null,
    /** Canonical lowercase relay path segment (e.g. "evm"); legacy uppercase tolerated by mapping. */
    @SerializedName("relayPrefix") val relayPrefix: String? = null,
    @SerializedName("mounted") val mounted: Boolean? = null,
    /** Legacy network-level booleans (kept for back-compat; features map is preferred). */
    @SerializedName("gasless") val gasless: Boolean? = null,
    @SerializedName("sponsor") val sponsor: Boolean? = null,
    /** Generic per-feature map keyed by featureId. */
    @SerializedName("features") val features: Map<String, FeatureCapabilityDto>? = null
)

data class FeatureCapabilityDto(
    @SerializedName("featureId") val featureId: String? = null,
    @SerializedName("available") val available: Boolean? = null,
    @SerializedName("visible") val visible: Boolean? = null,
    @SerializedName("reasonCode") val reasonCode: String? = null,
    @SerializedName("relayPrefix") val relayPrefix: String? = null,
    @SerializedName("minClientVersion") val minClientVersion: Map<String, Int>? = null
)
