package com.mtd.core.utils

import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.FiatCurrency
import java.math.BigDecimal

/**
 * TASK-56 — **the one place** an [AssetItem]'s fiat display strings are produced.
 *
 * Four screens render an asset's fiat value (wallet list, asset detail, the send picker, the send
 * confirm sheet) and three different code paths used to build the strings, each with its own format:
 * `"$12.34"` here, `"12.34 "` there, `"1,234 تومان "` somewhere else — which is why an amount could
 * look different depending on which screen you reached it from. They all call this now.
 *
 * Both currencies are always written, and [AssetItem.formattedDisplayBalance] selects one. Flipping the
 * toggle is then a pure re-selection with no arithmetic, and the two stored strings stay mutually
 * consistent — a value cannot be fresh in one currency and stale in the other.
 *
 * @param currency which of the two [AssetItem.formattedDisplayBalance] should show.
 * @param rate the USD→تومان rate; `null` (unknown) makes the تومان string a placeholder, not a zero.
 * @return the item with its three fiat strings rebuilt, or **unchanged** when the asset has no known
 *   price — formatting that would print a confident `0.00` for a balance that simply is not priced yet.
 */
fun AssetItem.withFiatBalances(currency: FiatCurrency, rate: CurrencyRate?): AssetItem {
    if (priceUsdRaw <= BigDecimal.ZERO) return this

    val usd = balanceRaw.multiply(priceUsdRaw)
    // Trailing space preserved: the wallet-list rows put the currency glyph in a sibling composable and
    // rely on it for spacing.
    val usdText = "${BalanceFormatter.formatFiatValue(usd, FiatCurrency.USD, null, withSymbol = false)} "
    val tomanText = "${BalanceFormatter.formatFiatValue(usd, FiatCurrency.TOMAN, rate, withSymbol = false)} "

    return copy(
        balanceUsdt = usdText,
        balanceIrr = tomanText,
        formattedDisplayBalance = if (currency == FiatCurrency.TOMAN) tomanText else usdText
    )
}
