package com.mtd.data.dto

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class HistoricalOhlcResponse(
    @SerializedName("Data") val data: List<OhlcCandle>? = null,
    @SerializedName("Response") val response: String? = null)

data class OhlcCandle(
    @SerializedName("TIMESTAMP") val timestamp: Long? = null,
    @SerializedName("QUOTE") val quote: String? = null,
    @SerializedName("OPEN") val open: Double? = null,
    @SerializedName("CLOSE") val close: Double? = null
)

data class AssetPriceResponse(
    @SerializedName("Data")
    val data: Map<String, AssetPriceDataDto>
)

data class AssetPriceDataDto(
    @SerializedName("BASE")
    val assetId: String? = null,
    @SerializedName("PRICE")
    val priceUsd: BigDecimal,
    @SerializedName("MOVING_24_HOUR_CHANGE_PERCENTAGE")
    val priceChanges24h: BigDecimal
)


