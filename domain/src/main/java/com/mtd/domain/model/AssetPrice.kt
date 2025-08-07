package com.mtd.domain.model

import java.math.BigDecimal

data class AssetPrice(
    val assetId: String, // e.g., "ethereum", "bitcoin"
    val priceUsd: BigDecimal,
    val priceChanges24h: BigDecimal // درصد تغییرات ۲۴ ساعته
)