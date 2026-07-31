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

/**
 * TASK-53 — این DTO قبلاً فقط `networkId/name/type/chainId/isTestnet` داشت، یعنی حتی بعد از
 * سیم‌کشی هم نمی‌شد از رویش یک شبکهٔ قابل‌استفاده ساخت: نه RPC داشت، نه decimals، نه
 * derivationPath. حالا شکلِ [com.mtd.domain.model.core.NetworkConfig] را آینه می‌کند تا یک
 * ورودیِ باندل واقعاً بتواند آدرس بسازد، موجودی بخواند و ارسال کند.
 *
 * فیلدهای ناموجود `null` می‌شوند و در نگاشت با پیش‌فرضِ امن پر می‌گردند؛ ورودیِ بدونِ فیلدهای
 * حیاتی (id/type/derivationPath) کنار گذاشته می‌شود.
 */
data class ConfigNetworkDto(
    @SerializedName("networkId") val networkId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("chainId") val chainId: Long? = null,
    @SerializedName("isTestnet") val isTestnet: Boolean? = null,

    @SerializedName("derivationPath") val derivationPath: String? = null,
    @SerializedName("rpcUrls") val rpcUrls: List<String>? = null,
    @SerializedName("rpcUrlsEvm") val rpcUrlsEvm: List<String>? = null,
    @SerializedName("webSocketUrl") val webSocketUrl: String? = null,
    @SerializedName("currencySymbol") val currencySymbol: String? = null,
    @SerializedName("decimals") val decimals: Int? = null,
    @SerializedName("explorers") val explorers: List<String>? = null,
    @SerializedName("explorerTxUrl") val explorerTxUrl: String? = null,
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("regex") val regex: String? = null,
    @SerializedName("color") val color: String? = null,
    @SerializedName("faName") val faName: String? = null,
    @SerializedName("explorerApi") val explorerApi: String? = null,
    @SerializedName("hasL1DataFee") val hasL1DataFee: Boolean? = null
)

data class ConfigAssetDto(
    @SerializedName("assetId") val assetId: String? = null,
    @SerializedName("networkId") val networkId: String? = null,
    @SerializedName("symbol") val symbol: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("decimals") val decimals: Int? = null,
    @SerializedName("contractAddress") val contractAddress: String? = null,
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("faName") val faName: String? = null
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
