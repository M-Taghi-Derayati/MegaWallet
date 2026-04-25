package com.mtd.data.dto

import com.google.gson.annotations.SerializedName
import com.mtd.domain.model.assets.AssetPriceDto

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
    val data: Map<String, AssetPriceDto>
)



