package com.mtd.data.dto

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class HistoricalOhlcResponse(
    @SerializedName("data") val data: List<OhlcCandle>? = null)

data class OhlcCandle(
    @SerializedName("priceUsd") val priceUsd: String? = null,
    @SerializedName("time") val time: Long? = null,

)

data class AssetPriceResponse(
    @SerializedName("data")
    val data: List< AssetPriceDataDto>
)

data class AssetPriceDataDto(
    @SerializedName("symbol")
    val assetId: String,
    @SerializedName("priceUsd")
    val priceUsd: BigDecimal,
    @SerializedName("changePercent24Hr")
    val priceChanges24h: BigDecimal
)


