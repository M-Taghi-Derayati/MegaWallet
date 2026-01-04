package com.mtd.megawallet.event

import java.math.BigDecimal

/**
 * مدل داده برای کش کردن موجودی هر asset
 */
data class CachedAssetBalance(
    val assetId: String,
    val walletId: String,
    val balanceRaw: BigDecimal,
    val priceUsdRaw: BigDecimal,
    val balance: String,
    val balanceUsdt: String,
    val balanceIrr: String,
    val priceChange24h: Double,
    val cachedAt: Long = System.currentTimeMillis()
)




