package com.mtd.core.utils

import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.FiatCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.math.BigDecimal

/**
 * TASK-56 — the fiat display path in both currencies.
 *
 * The cases that matter here are the ones that cost real money if they regress: the unit the rate is
 * carried in (تومان vs rial is a factor of ten), and the difference between "unknown" and "zero".
 */
class FiatFormattingTest {

    private fun toman(value: String) = CurrencyRate(
        quoteCurrency = "TMN",
        baseCurrency = "USDT",
        rate = BigDecimal(value),
        lastUpdated = 1_000L
    )

    private fun rial(value: String) = CurrencyRate(
        quoteCurrency = "IRR",
        baseCurrency = "USD",
        rate = BigDecimal(value),
        lastUpdated = 1_000L
    )

    // ---- unit normalisation (the factor of 10) ----

    @Test
    fun `a TMN-quoted rate is already Toman and is not divided`() {
        assertEquals(BigDecimal("70000"), FiatConversion.tomanPerUsd(toman("70000")))
    }

    @Test
    fun `TOMAN and IRT are accepted as aliases for TMN`() {
        assertEquals(BigDecimal("70000"), FiatConversion.tomanPerUsd(toman("70000").copy(quoteCurrency = "TOMAN")))
        assertEquals(BigDecimal("70000"), FiatConversion.tomanPerUsd(toman("70000").copy(quoteCurrency = "irt")))
    }

    @Test
    fun `an IRR-quoted rate is rial and IS divided by ten`() {
        // 700,000 rial per USD == 70,000 تومان per USD.
        assertEquals(0, BigDecimal("70000").compareTo(FiatConversion.tomanPerUsd(rial("700000"))))
    }

    @Test
    fun `an unrecognised quote currency is unknown rather than a guess`() {
        // Being wrong by a factor of ten is worse than showing a placeholder.
        assertNull(FiatConversion.tomanPerUsd(toman("70000").copy(quoteCurrency = "XYZ")))
    }

    @Test
    fun `a null or non-positive rate is unknown`() {
        assertNull(FiatConversion.tomanPerUsd(null))
        assertNull(FiatConversion.tomanPerUsd(toman("0")))
        assertNull(FiatConversion.tomanPerUsd(toman("-1")))
    }

    @Test
    fun `usdToToman multiplies by the normalised factor`() {
        assertEquals(0, BigDecimal("140000").compareTo(FiatConversion.usdToToman(BigDecimal("2"), toman("70000"))))
        // Same amount via a rial-quoted rate must land on the same تومان figure.
        assertEquals(0, BigDecimal("140000").compareTo(FiatConversion.usdToToman(BigDecimal("2"), rial("700000"))))
    }

    @Test
    fun `tomanToUsd is the inverse of usdToToman`() {
        val usd = FiatConversion.tomanToUsd(BigDecimal("140000"), toman("70000"))
        assertEquals(0, BigDecimal("2").compareTo(usd))
    }

    @Test
    fun `tomanToUsd is unknown when the rate is unknown`() {
        assertNull(FiatConversion.tomanToUsd(BigDecimal("140000"), null))
    }

    // ---- formatting ----

    @Test
    fun `USD renders two decimals with a dollar prefix`() {
        assertEquals(
            "$1,234.50",
            BalanceFormatter.formatFiatValue(BigDecimal("1234.5"), FiatCurrency.USD, null)
        )
    }

    @Test
    fun `USD opens up to six decimals for dust below one cent`() {
        assertEquals(
            "$0.001235",
            BalanceFormatter.formatFiatValue(BigDecimal("0.0012345"), FiatCurrency.USD, null)
        )
    }

    @Test
    fun `USD ignores the rate entirely`() {
        assertEquals(
            BalanceFormatter.formatFiatValue(BigDecimal("10"), FiatCurrency.USD, null),
            BalanceFormatter.formatFiatValue(BigDecimal("10"), FiatCurrency.USD, toman("70000"))
        )
    }

    @Test
    fun `Toman renders whole units with the Persian separator and suffix`() {
        // 2 USD x 70,000 = 140,000 تومان
        // Latin digits with the Persian thousands separator (٬) — that is what
        // [BalanceFormatter.formatNumberWithSeparator] produces, and what the wallet already displays.
        assertEquals(
            "140٬000 تومان",
            BalanceFormatter.formatFiatValue(BigDecimal("2"), FiatCurrency.TOMAN, toman("70000"))
        )
    }

    @Test
    fun `Toman rounds to whole units, half up`() {
        // 1.000005 USD x 70,000 = 70,000.35 تومان -> 70,000
        assertEquals(
            "70٬000",
            BalanceFormatter.formatFiatValue(
                BigDecimal("1.000005"), FiatCurrency.TOMAN, toman("70000"), withSymbol = false
            )
        )
        // 1.00001 USD x 70,000 = 70,000.7 تومان -> 70,001
        assertEquals(
            "70٬001",
            BalanceFormatter.formatFiatValue(
                BigDecimal("1.00001"), FiatCurrency.TOMAN, toman("70000"), withSymbol = false
            )
        )
    }

    @Test
    fun `Toman never shows a fraction`() {
        val formatted = BalanceFormatter.formatFiatValue(
            BigDecimal("0.5"), FiatCurrency.TOMAN, toman("70000"), withSymbol = false
        )
        assertEquals("35٬000", formatted)
        assertFalse("تومان must render whole units, got $formatted", formatted.contains("."))
    }

    @Test
    fun `an IRR-quoted rate produces the same Toman string as its TMN equivalent`() {
        assertEquals(
            BalanceFormatter.formatFiatValue(BigDecimal("3"), FiatCurrency.TOMAN, toman("70000")),
            BalanceFormatter.formatFiatValue(BigDecimal("3"), FiatCurrency.TOMAN, rial("700000"))
        )
    }

    // ---- the unknown-rate placeholder ----

    @Test
    fun `an unknown rate renders the placeholder, not zero`() {
        assertEquals(
            FiatConversion.UNKNOWN_PLACEHOLDER,
            BalanceFormatter.formatFiatValue(BigDecimal("1234.5"), FiatCurrency.TOMAN, null)
        )
    }

    @Test
    fun `the placeholder is not a number and carries no currency suffix`() {
        val placeholder = BalanceFormatter.formatFiatValue(BigDecimal("1"), FiatCurrency.TOMAN, null)
        assertEquals("—", placeholder)
        assertFalse("an unknown rate must never render as a digit", placeholder.any { it.isDigit() })
        assertFalse(
            "a failed conversion should not be dressed up as a تومان amount",
            placeholder.contains(BalanceFormatter.TOMAN_SUFFIX)
        )
    }

    @Test
    fun `a genuine zero balance is still a zero, not the placeholder`() {
        assertEquals(
            "0 تومان",
            BalanceFormatter.formatFiatValue(BigDecimal.ZERO, FiatCurrency.TOMAN, toman("70000"))
        )
        assertEquals("$0.00", BalanceFormatter.formatFiatValue(BigDecimal.ZERO, FiatCurrency.USD, null))
    }

    // ---- AssetItem display strings ----

    private fun asset(balance: String, price: String) = AssetItem(
        id = "USDT-SEPOLIA",
        networkId = "sepolia",
        name = "Tether",
        symbol = "USDT",
        networkName = "on sepolia",
        iconUrl = null,
        balance = balance,
        balanceUsdt = "...",
        balanceRaw = BigDecimal(balance),
        priceUsdRaw = BigDecimal(price)
    )

    @Test
    fun `withFiatBalances writes both currencies and selects the active one`() {
        val item = asset("2", "1.5").withFiatBalances(FiatCurrency.TOMAN, toman("70000"))

        assertEquals("3.00 ", item.balanceUsdt)
        assertEquals("210٬000 ", item.balanceIrr)
        assertEquals(item.balanceIrr, item.formattedDisplayBalance)
    }

    @Test
    fun `withFiatBalances selects USD when USD is active, both still populated`() {
        val item = asset("2", "1.5").withFiatBalances(FiatCurrency.USD, toman("70000"))

        assertEquals("3.00 ", item.balanceUsdt)
        assertEquals("210٬000 ", item.balanceIrr)
        assertEquals(item.balanceUsdt, item.formattedDisplayBalance)
    }

    @Test
    fun `withFiatBalances shows the placeholder in Toman when the rate is unknown`() {
        val item = asset("2", "1.5").withFiatBalances(FiatCurrency.TOMAN, null)

        // USD is always available — it is the unit balances are computed in.
        assertEquals("3.00 ", item.balanceUsdt)
        assertEquals("— ", item.balanceIrr)
        assertEquals("— ", item.formattedDisplayBalance)
    }

    @Test
    fun `withFiatBalances leaves an asset untouched while its price is still being fetched`() {
        // Otherwise a not-yet-priced asset would render a confident "0.00" instead of its placeholder.
        val pending = asset("2", "0")
        assertSame(pending, pending.withFiatBalances(FiatCurrency.USD, toman("70000")))
    }

    @Test
    fun `withFiatBalances shows the placeholder once an asset is known to have no price`() {
        // The server drops implausible prices for illiquid pools on purpose, so "no price" is a real
        // permanent state. Rendering it as $0.00 makes the holding look worthless rather than unpriced.
        val settled = asset("2", "0").copy(priceLookupSettled = true)
            .withFiatBalances(FiatCurrency.USD, toman("70000"))

        assertEquals("—", settled.balanceUsdt)
        assertEquals("—", settled.balanceIrr)
        assertEquals("—", settled.formattedDisplayBalance)
    }
}
