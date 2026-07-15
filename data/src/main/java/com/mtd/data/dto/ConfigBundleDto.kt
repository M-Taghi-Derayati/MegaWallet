package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

/**
 * Phase 3 — dynamic config bundle served by `GET /api/v1/config/bundle`.
 *
 * The relayer ships the canonical networks/assets catalog at runtime so the client no longer has to
 * trust only its bundled `networks.json` / `assets.json`. [signature] is a detached signature over
 * the serialized bundle (verified by the caller before the catalog is trusted); branch on it, never
 * on content. The per-entry [ConfigNetworkDto] / [ConfigAssetDto] shapes mirror the bundled asset
 * files — unknown fields decode to null and are ignored.
 */
data class ConfigBundleDto(
    @SerializedName("version") val version: String? = null,
    @SerializedName("networks") val networks: List<ConfigNetworkDto>? = null,
    @SerializedName("assets") val assets: List<ConfigAssetDto>? = null,
    /** Detached signature over the bundle payload; verify before trusting the catalog. */
    @SerializedName("signature") val signature: String? = null
)

data class ConfigNetworkDto(
    @SerializedName("networkId") val networkId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("chainId") val chainId: Long? = null,
    @SerializedName("isTestnet") val isTestnet: Boolean? = null
)

data class ConfigAssetDto(
    @SerializedName("assetId") val assetId: String? = null,
    @SerializedName("networkId") val networkId: String? = null,
    @SerializedName("symbol") val symbol: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("decimals") val decimals: Int? = null,
    @SerializedName("contractAddress") val contractAddress: String? = null
)

/** `GET /api/v1/config/version` — cheap probe to decide whether a full bundle fetch is needed. */
data class ConfigVersionDto(
    @SerializedName("version") val version: String? = null
)

/** `GET /api/v1/config/public-key` — pinned secp256k1 signer used to verify the bundle signature. */
data class ConfigPublicKeyDto(
    @SerializedName("publicKey") val publicKey: String? = null,
    @SerializedName("curve") val curve: String? = null,
    @SerializedName("keyId") val keyId: String? = null
)
