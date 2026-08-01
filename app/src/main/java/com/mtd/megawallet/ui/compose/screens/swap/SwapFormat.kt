package com.mtd.megawallet.ui.compose.screens.swap

import com.mtd.core.utils.BalanceFormatter
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.FiatCurrency
import java.math.BigDecimal
import java.math.BigInteger

/**
 * لبهٔ نمایش: تنها جایی که پولِ خام (کوچک‌ترین واحد، [BigInteger]) به رشته تبدیل می‌شود.
 * هیچ محاسبه‌ای اینجا انجام نمی‌شود که به مسیرِ ارسال برگردد.
 */
object SwapFormat {

    fun amount(raw: BigInteger, decimals: Int): String =
        BalanceFormatter.formatBalance(raw.toDisplay(decimals), decimals)

    fun amountWithSymbol(raw: BigInteger, decimals: Int, symbol: String): String =
        "${amount(raw, decimals)} $symbol"

    /** ارزشِ فیاتِ یک مقدارِ خام، در واحدِ انتخاب‌شدهٔ کاربر. نرخِ نامعلوم ⇒ placeholder، نه صفر. */
    fun fiat(
        raw: BigInteger,
        decimals: Int,
        priceUsd: BigDecimal,
        currency: FiatCurrency,
        rate: CurrencyRate?
    ): String = BalanceFormatter.formatFiatValue(
        usdAmount = raw.toDisplay(decimals).multiply(priceUsd),
        currency = currency,
        rate = rate
    )

    fun usd(
        usdAmount: BigDecimal,
        currency: FiatCurrency,
        rate: CurrencyRate?
    ): String = BalanceFormatter.formatFiatValue(usdAmount, currency, rate)

    /** «۰٫۵٪» از bps. */
    fun percentFromBps(bps: Int): String {
        val value = BigDecimal(bps).movePointLeft(2).stripTrailingZeros()
        return "${value.toPlainString()}٪"
    }

    private fun BigInteger.toDisplay(decimals: Int): BigDecimal =
        BigDecimal(this).movePointLeft(decimals)
}
