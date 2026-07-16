package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

/**
 * DTOs for batch monitoring enrollment — `POST /api/mobile/v1/monitoring/subscribe` (TASK-32).
 * NOT BM-33-enveloped: the response is `{ ok, subscribed, total, results:[…] }` directly, so this
 * path does not go through [com.mtd.data.network.proxyCall].
 */
data class MonitoringSubscribeRequestDto(
    @SerializedName("addresses") val addresses: List<MonitoringAddressDto>
)

data class MonitoringAddressDto(
    @SerializedName("address") val address: String,
    @SerializedName("networkId") val networkId: String
)

data class MonitoringSubscribeResponseDto(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("subscribed") val subscribed: Int = 0,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("results") val results: List<MonitoringSubscribeResultDto>? = null
)

data class MonitoringSubscribeResultDto(
    @SerializedName("address") val address: String? = null,
    @SerializedName("networkId") val networkId: String? = null,
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("error") val error: String? = null
)
