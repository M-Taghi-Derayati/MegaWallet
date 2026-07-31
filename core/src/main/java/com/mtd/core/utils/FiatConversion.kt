package com.mtd.core.utils

import com.mtd.domain.model.CurrencyRate
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * TASK-56 — **the single place** where a USD amount becomes a تومان amount, and the single place that
 * knows the rial↔تومان factor of 10.
 *
 * ### Which unit each side holds
 *
 * | side | unit |
 * |---|---|
 * | every `priceUsdRaw`, `balanceUsdt`, `fiatValue` in the app | **USD** |
 * | [CurrencyRate.rate] as produced today by `MarketDataRepositoryImpl` | **تومان per 1 USD** |
 * | the output of [tomanPerUsd] / [usdToToman] | **تومان** |
 *
 * The middle row is the one that has bitten this codebase. The Wallex response field is
 * `result.uSDTTMN` — **TMN, i.e. تومان** — and `MarketDataRepositoryImpl` reads it into a variable
 * named `latestPriceToman`. For a long time it was then labelled `quoteCurrency = "IRR"` with a comment
 * admitting the label was wrong. So: **there is no `/ 10` in today's production path, and adding one
 * would make every تومان amount in the app ten times too small.** The producers now label the value
 * `TMN` to match what it actually holds.
 *
 * The `IRR` branch below is therefore not dead weight for its own sake: it is the contract for a
 * source that genuinely sends rial (the relayer `/prices` DTO carries an `irr` field). Because the unit
 * is resolved from [CurrencyRate.quoteCurrency] in this one function, such a source can be adopted by
 * labelling it correctly — not by sprinkling `/ 10` at call sites.
 *
 * An unrecognised label yields `null` (**unknown**), never a guess. A ten-fold error in a money value
 * is far worse than a placeholder.
 */
object FiatConversion {

    /**
     * What every fiat surface renders when the rate is not known yet. Never `0` — that would read as a
     * real balance of zero — and never a hardcoded rate (a fabricated `70000` fallback priced Toman
     * amounts *and the MAX-send math* until TASK-54 removed it).
     */
    const val UNKNOWN_PLACEHOLDER = "—"

    /** تومان amounts are large; sub-تومان precision is noise. Display scale for every تومان value. */
    const val TOMAN_DISPLAY_SCALE = 0

    /** 1 تومان = 10 ﷼. The only occurrence of this factor in the codebase. */
    private val RIAL_PER_TOMAN: BigDecimal = BigDecimal.TEN

    /** Kept well above [TOMAN_DISPLAY_SCALE] so normalising the *rate* never loses precision. */
    private const val RATE_SCALE = 10

    /**
     * Normalises [rate] to **تومان per 1 USD**, or `null` when the rate is unknown or its unit is not
     * recognised.
     *
     * @param rate the observed value from `IUsdToIrrRateProvider.rate`; `null` means "not known yet".
     */
    fun tomanPerUsd(rate: CurrencyRate?): BigDecimal? {
        val value = rate?.rate ?: return null
        if (value <= BigDecimal.ZERO) return null

        return when (rate.quoteCurrency.trim().uppercase(Locale.US)) {
            // Already تومان — the Wallex `uSDTTMN` path, i.e. everything in production today.
            "TMN", "TOMAN", "IRT" -> value
            // Genuine rial. This is the one and only `/ 10` in the app.
            "IRR", "RIAL" -> value.divide(RIAL_PER_TOMAN, RATE_SCALE, RoundingMode.HALF_UP)
            // Unknown unit: refuse to guess rather than risk being wrong by a factor of ten.
            else -> null
        }
    }

    /**
     * Converts a **USD** amount to **تومان**, or `null` when the rate is unknown.
     *
     * The result is unrounded; rounding to [TOMAN_DISPLAY_SCALE] belongs to the formatter, so
     * intermediate arithmetic (totals, MAX-send) keeps full precision.
     */
    fun usdToToman(usdAmount: BigDecimal, rate: CurrencyRate?): BigDecimal? {
        val factor = tomanPerUsd(rate) ?: return null
        return usdAmount.multiply(factor)
    }

    /**
     * Converts a **تومان** amount back to **USD**, or `null` when the rate is unknown.
     *
     * Needed by the send screen: the user types an amount in the selected fiat currency and it has to
     * become a crypto amount, which is priced in USD.
     */
    fun tomanToUsd(tomanAmount: BigDecimal, rate: CurrencyRate?): BigDecimal? {
        val factor = tomanPerUsd(rate) ?: return null
        if (factor <= BigDecimal.ZERO) return null
        return tomanAmount.divide(factor, USD_SCALE, RoundingMode.HALF_UP)
    }

    /** Enough precision that a تومان→USD→crypto round-trip does not visibly lose value. */
    private const val USD_SCALE = 10
}
