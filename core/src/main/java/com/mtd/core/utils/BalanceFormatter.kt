
package com.mtd.core.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object BalanceFormatter {

    /**
     * یک موجودی خام (در کوچکترین واحد) را به یک رشته قابل نمایش برای کاربر تبدیل می‌کند.
     *
     * @param rawBalance موجودی خام به صورت BigInteger (e.g., Wei or Satoshi).
     * @param decimals تعداد ارقام اعشار ارز (e.g., 18 for ETH, 8 for BTC).
     * @param symbol نماد ارز برای نمایش (e.g., "ETH", "tBTC").
     * @param displayDecimals تعداد ارقام اعشاری که می‌خواهیم به کاربر نمایش دهیم.
     * @param usePersianSeparator اگر true باشد از جداکننده فارسی (٬) استفاده می‌کند
     * @return یک رشته فرمت شده، e.g., "0.0019 tBTC".
     */
    fun formatBalance(
        balance: BigDecimal,
        decimals: Int,
        displayDecimals: Int = 6, // تعداد اعشار برای نمایش
        usePersianSeparator: Boolean = false
    ): String {
        val actualValue = balance.stripTrailingZeros()
        if (actualValue.signum() == 0) return "0"

        // محاسبه هوشمند تعداد اعشار (همان منطقی که بالاتر توضیح دادم)
        val finalScale = calculateDynamicScale(actualValue, decimals, displayDecimals)

        // حالا از همان تابع فرمت‌کننده استفاده می‌کنیم اما با تنظیمات درست
        return formatNumberWithSeparator(
            number = actualValue,
            usePersianSeparator = usePersianSeparator,
            minFractionDigits = 0,
            maxFractionDigits = finalScale,
            roundingMode = RoundingMode.DOWN // برای موجودی همیشه DOWN
        )
    }
    private fun calculateDynamicScale(value: BigDecimal, assetDecimals: Int, displayDecimals: Int): Int {
        if (value >= BigDecimal("1000")) return 2
        if (value >= BigDecimal("1")) return displayDecimals

        val plainString = value.toPlainString()
        val decimalPointIndex = plainString.indexOf('.')
        val firstSignificant = plainString.indexOfFirst { it in '1'..'9' && plainString.indexOf(it) > decimalPointIndex }

        return if (decimalPointIndex != -1 && firstSignificant != -1) {
            // تغییر این خط: استفاده از max بین دقت شبکه و موقعیت عدد معنادار
            val neededScale = (firstSignificant - decimalPointIndex) + 2
            // اینجا اجازه می‌دهیم تا سقف ۱۸ یا ۲۰ رقم باز شود
            max(displayDecimals, min(max(assetDecimals, 18), neededScale))
        } else {
            displayDecimals
        }
    }

    /**
     * یک مقدار عددی را به فرمت پولی دلاری تبدیل می‌کند.
     *
     * @param amount مقدار عددی.
     * @param usePersianSeparator اگر true باشد از جداکننده فارسی (٬) استفاده می‌کند
     * @return یک رشته فرمت شده دلاری, e.g., "$102.50".
     */
    fun formatUsdValue(
        amount: BigDecimal,
        usePersianSeparator: Boolean = false
    ): String {
        val isZero = amount.compareTo(BigDecimal.ZERO) == 0

        // اگر عدد بین 0 و 0.01 بود، اعشار را تا 6 رقم باز کن، در غیر این صورت همان 2 رقم
        val dynamicMaxDecimals = if (!isZero && amount < BigDecimal("0.01")) 6 else 2

        return formatNumberWithSeparator(
            number = amount,
            usePersianSeparator = usePersianSeparator,
            minFractionDigits = 2,
            maxFractionDigits = dynamicMaxDecimals
        )
    }

    // این همان تابع شماست که کمی اصلاح شده تا منعطف‌تر باشد
    fun formatNumberWithSeparator(
        number: Number,
        usePersianSeparator: Boolean = false,
        minFractionDigits: Int = 0,
        maxFractionDigits: Int = 2,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): String {
        val format = NumberFormat.getNumberInstance(Locale.US)
        format.minimumFractionDigits = minFractionDigits
        format.maximumFractionDigits = maxFractionDigits
        format.roundingMode = roundingMode

        val formatted = format.format(number)

        return if (usePersianSeparator) formatted.replace(",", "٬") else formatted
    }
}

/**
 * Extension function برای فرمت کردن اعداد با جداکننده هزارگان
 * 
 * @param usePersianSeparator اگر true باشد از جداکننده فارسی (٬) استفاده می‌کند
 * @param minFractionDigits حداقل تعداد ارقام اعشار
 * @param maxFractionDigits حداکثر تعداد ارقام اعشار
 */
fun Number.formatWithSeparator(
    usePersianSeparator: Boolean = false,
    minFractionDigits: Int = 0,
    maxFractionDigits: Int = 2
): String {
    return BalanceFormatter.formatNumberWithSeparator(
        number = this,
        usePersianSeparator = usePersianSeparator,
        minFractionDigits = minFractionDigits,
        maxFractionDigits = maxFractionDigits
    )
}

/**
 * Extension function برای فرمت کردن BigDecimal با جداکننده هزارگان
 */
fun BigDecimal.formatWithSeparator(
    usePersianSeparator: Boolean = false,
    minFractionDigits: Int = 0,
    maxFractionDigits: Int = 2
): String {
    return BalanceFormatter.formatNumberWithSeparator(
        number = this,
        usePersianSeparator = usePersianSeparator,
        minFractionDigits = minFractionDigits,
        maxFractionDigits = maxFractionDigits
    )
}

/**
 * Extension function برای فرمت کردن Double با جداکننده هزارگان
 */
fun Double.formatWithSeparator(
    usePersianSeparator: Boolean = false,
    minFractionDigits: Int = 0,
    maxFractionDigits: Int = 2
): String {
    return BalanceFormatter.formatNumberWithSeparator(
        number = this,
        usePersianSeparator = usePersianSeparator,
        minFractionDigits = minFractionDigits,
        maxFractionDigits = maxFractionDigits
    )
}

/**
 * Extension function برای فرمت کردن Float با جداکننده هزارگان
 */
fun Float.formatWithSeparator(
    usePersianSeparator: Boolean = false,
    minFractionDigits: Int = 0,
    maxFractionDigits: Int = 2
): String {
    return BalanceFormatter.formatNumberWithSeparator(
        number = this,
        usePersianSeparator = usePersianSeparator,
        minFractionDigits = minFractionDigits,
        maxFractionDigits = maxFractionDigits
    )
}

/**
 * Extension function برای فرمت کردن Long با جداکننده هزارگان
 */
fun Long.formatWithSeparator(
    usePersianSeparator: Boolean = false
): String {
    return BalanceFormatter.formatNumberWithSeparator(
        number = this,
        usePersianSeparator = usePersianSeparator,
        minFractionDigits = 0,
        maxFractionDigits = 0
    )
}

/**
 * Extension function برای فرمت کردن Int با جداکننده هزارگان
 */
fun Int.formatWithSeparator(
    usePersianSeparator: Boolean = false
): String {
    return BalanceFormatter.formatNumberWithSeparator(
        number = this,
        usePersianSeparator = usePersianSeparator,
        minFractionDigits = 0,
        maxFractionDigits = 0
    )
}
