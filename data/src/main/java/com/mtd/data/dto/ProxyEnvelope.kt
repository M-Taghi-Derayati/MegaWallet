package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

/**
 * The BM-33 envelope wrapping every per-network mobile-proxy response (§1.7 of the contract).
 *
 * Success: `{ ok:true, networkId, data:{...}, meta:{...} }`
 * Failure: `{ ok:false, networkId, error:{ code, message, detail }, meta:{...} }`
 *
 * NOTE: `POST /api/mobile/v1/history` does NOT use this envelope (it returns `{items,pageInfo}`) —
 * that is handled separately in Phase 2.
 */
data class ProxyEnvelope<T>(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("networkId") val networkId: String? = null,
    @SerializedName("data") val data: T? = null,
    @SerializedName("error") val error: ProxyErrorDto? = null,
    @SerializedName("meta") val meta: ProxyMetaDto? = null
)

data class ProxyErrorDto(
    @SerializedName("code") val code: String? = null,
    /** Human message — may be Persian. Display only; never branch on it. */
    @SerializedName("message") val message: String? = null,
    @SerializedName("detail") val detail: String? = null
)

data class ProxyMetaDto(
    @SerializedName("engine") val engine: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("configVersion") val configVersion: String? = null
)
