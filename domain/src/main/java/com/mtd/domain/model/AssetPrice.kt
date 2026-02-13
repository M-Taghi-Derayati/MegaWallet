package com.mtd.domain.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class AssetPriceResponse(
    @SerializedName("Data")
    val data: Map<String, AssetPrice>
)

data class AssetPrice(
    @SerializedName("BASE")
    val assetId: String, // e.g., "ethereum", "bitcoin"
    @SerializedName("PRICE")
    val priceUsd: BigDecimal,
    @SerializedName("MOVING_24_HOUR_CHANGE_PERCENTAGE")
    val priceChanges24h: BigDecimal // درصد تغییرات ۲۴ ساعته
)

