
package com.mtd.core.utils

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
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
     * @return یک رشته فرمت شده، e.g., "0.0019 tBTC".
     */
    fun formatBalance(
        rawBalance: BigInteger,
        decimals: Int,
        displayDecimals: Int = 6 // تعداد اعشار برای نمایش
    ): String {
        // ۱. تبدیل BigInteger به BigDecimal
        val balanceDecimal = BigDecimal(rawBalance)

        // ۲. محاسبه ضریب تبدیل (10^decimals)
        val divisor = BigDecimal.TEN.pow(decimals)

        // ۳. تقسیم برای به دست آوردن مقدار در واحد اصلی
        val formattedValue = balanceDecimal.divide(divisor)

        // ۴. گرد کردن به تعداد ارقام اعشار مورد نظر برای نمایش
        val roundedValue = formattedValue.setScale(displayDecimals, RoundingMode.DOWN)
        
        // ۵. تبدیل به رشته و حذف صفرهای اضافی در انتهای اعشار
        val plainString = roundedValue.stripTrailingZeros().toPlainString()

        // ۶. اضافه کردن نماد ارز
        return "$plainString"
    }

    /**
     * یک مقدار عددی را به فرمت پولی دلاری تبدیل می‌کند.
     *
     * @param amount مقدار عددی.
     * @return یک رشته فرمت شده دلاری, e.g., "$102.50".
     */
    fun formatUsdValue(amount: BigDecimal): String {
        // ۱. یک نمونه از فرمت‌دهنده عددی عمومی برای Locale آمریکا می‌گیریم
        // (برای استفاده از کاما به عنوان جداکننده هزارگان و نقطه برای اعشار)
        val format = NumberFormat.getNumberInstance(Locale.US)

        // ۲. حداقل و حداکثر تعداد ارقام اعشار را روی ۲ تنظیم می‌کنیم
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2

        // ۳. حالت گرد کردن را برای جلوگیری از رفتارهای غیرمنتظره تنظیم می‌کنیم
        format.roundingMode = RoundingMode.HALF_UP // یا هر حالت دیگری که ترجیح می‌دهید

        // ۴. عدد را فرمت کرده و برمی‌گردانیم
        return format.format(amount)
    }
}