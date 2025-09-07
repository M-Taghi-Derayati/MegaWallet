// In: common_ui/src/main/java/com/mtd/common_ui/extensions/ViewExtensions.kt

package com.mtd.common_ui

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.mtd.common_ui.R // مطمئن شوید R از ماژول common_ui ایمپورت می‌شود

/**
 * به بخش‌های ابتدایی و انتهایی یک رشته (مثل آدرس) رنگ متفاوتی اعمال می‌کند.
 *
 * @param context برای دسترسی به رنگ‌ها.
 * @param startChars تعداد کاراکترهای ابتدایی برای رنگ‌آمیزی.
 * @param endChars تعداد کاراکترهای انتهایی برای رنگ‌آمیزی.
 * @param highlightColor رنگی که برای بخش‌های اول و آخر استفاده می‌شود.
 * @param defaultColor رنگی که برای بخش میانی استفاده می‌شود (به صورت پیش‌فرض text_secondary).
 * @return یک SpannableStringBuilder که آماده قرار گرفتن در TextView است.
 */
fun String.toStyledAddress(
    context: Context,
    startChars: Int = 6,
    endChars: Int = 6,
    @ColorInt highlightColor: Int,
    @ColorInt defaultColor: Int = ContextCompat.getColor(context, R.color.text_secondary)
): SpannableStringBuilder {

    // اگر رشته کوتاه‌تر از مجموع بخش‌های ابتدایی و انتهایی بود، کلش را هایلایت کن
    if (this.length <= startChars + endChars) {
        return SpannableStringBuilder(this).apply {
            setSpan(ForegroundColorSpan(highlightColor), 0, this.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    val builder = SpannableStringBuilder(this)

    // ۱. رنگ کردن کل متن با رنگ پیش‌فرض (خاکستری)
    builder.setSpan(
        ForegroundColorSpan(defaultColor),
        0,
        this.length,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )

    // ۲. رنگ کردن بخش اول با رنگ هایلایت (رنگ پیش‌فرض را override می‌کند)
    builder.setSpan(
        ForegroundColorSpan(highlightColor),
        0, // شروع از ایندکس 0
        startChars, // تا انتهای بخش اول
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )

    // ۳. رنگ کردن بخش دوم با رنگ هایلایت (رنگ پیش‌فرض را override می‌کند)
    builder.setSpan(
        ForegroundColorSpan(highlightColor),
        this.length - endChars, // شروع بخش دوم
        this.length, // تا انتهای متن
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )

    return builder
}