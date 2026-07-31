package com.mtd.domain.model

import java.math.BigDecimal

/**
 * @property quoteCurrency the unit [rate] is expressed in — **"TMN"** (تومان) for the Wallex source,
 *   "IRR" for a genuine rial source. This is not decoration: `core/utils/FiatConversion` resolves the
 *   unit from this string, and تومان vs rial differ by a factor of ten. Label it accurately.
 * @property rate how many [quoteCurrency] one [baseCurrency] buys.
 */
data class CurrencyRate(
    val quoteCurrency: String, // e.g., "TMN"
    val baseCurrency: String = "USD",
    val rate: BigDecimal, // 1 USD = ? <quoteCurrency>
    val lastUpdated: Long
)
