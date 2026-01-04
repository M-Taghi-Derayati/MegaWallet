package com.mtd.domain.model

import java.math.BigDecimal

data class CurrencyRate(
    val quoteCurrency: String, // e.g., "IRR"
    val baseCurrency: String = "USD",
    val rate: BigDecimal, // 1 USD = ? IRR
    val lastUpdated: Long
)
