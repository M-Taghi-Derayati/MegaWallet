
package com.mtd.core.utils

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

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
        rawBalance: BigInteger,
        decimals: Int,
        displayDecimals: Int = 6, // تعداد اعشار برای نمایش
        usePersianSeparator: Boolean = false
    ): String {
        // ۱. تبدیل BigInteger به BigDecimal
        val balanceDecimal = BigDecimal(rawBalance)

        // ۲. محاسبه ضریب تبدیل (10^decimals)
        val divisor = BigDecimal.TEN.pow(decimals)

        // ۳. تقسیم برای به دست آوردن مقدار در واحد اصلی
        val formattedValue = balanceDecimal.divide(divisor)

        // ۴. گرد کردن به تعداد ارقام اعشار مورد نظر برای نمایش
        val roundedValue = formattedValue.setScale(displayDecimals, RoundingMode.DOWN)
        
        // ۵. استفاده از formatNumberWithSeparator برای اضافه کردن جداکننده هزارگان
        return formatNumberWithSeparator(
            number = roundedValue,
            usePersianSeparator = usePersianSeparator,
            minFractionDigits = 0,
            maxFractionDigits = displayDecimals
        )
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
        return formatNumberWithSeparator(
            number = amount,
            usePersianSeparator = usePersianSeparator,
            minFractionDigits = 2,
            maxFractionDigits = 2
        )
    }

    /**
     * فرمت کردن عدد با جداکننده هزارگان
     * پشتیبانی از فارسی (٬) و انگلیسی (,)
     * 
     * @param number عدد برای فرمت کردن
     * @param usePersianSeparator اگر true باشد از جداکننده فارسی (٬) استفاده می‌کند، در غیر این صورت از انگلیسی (,)
     * @param minFractionDigits حداقل تعداد ارقام اعشار (پیش‌فرض: 0)
     * @param maxFractionDigits حداکثر تعداد ارقام اعشار (پیش‌فرض: 2)
     * @return رشته فرمت شده با جداکننده هزارگان
     */
    fun formatNumberWithSeparator(
        number: Number,
        usePersianSeparator: Boolean = false,
        minFractionDigits: Int = 0,
        maxFractionDigits: Int = 2
    ): String {
        val format = NumberFormat.getNumberInstance(Locale.US)
        format.minimumFractionDigits = minFractionDigits
        format.maximumFractionDigits = maxFractionDigits
        format.roundingMode = RoundingMode.HALF_UP
        
        val formatted = format.format(number)
        
        // اگر از جداکننده فارسی استفاده می‌کنیم، کاما انگلیسی را با کاما فارسی جایگزین می‌کنیم
        return if (usePersianSeparator) {
            formatted.replace(",", "٬")
        } else {
            formatted
        }
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