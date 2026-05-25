package com.mtd.domain.model.assets

import java.math.BigDecimal

data class AssetPriceDto(
    val assetId: String,
    val priceUsd: BigDecimal,
    val priceChanges24h: BigDecimal
)
