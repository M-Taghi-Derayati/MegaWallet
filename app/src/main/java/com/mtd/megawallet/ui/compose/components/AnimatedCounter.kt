package com.mtd.megawallet.ui.compose.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.mtd.core.utils.formatWithSeparator
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/**
 * شمارنده پیشرفته و جذاب برای نمایش موجودی‌ها
 * این ورژن از Animatable استفاده می‌کند تا حرکتی بسیار نرم و حرفه‌ای داشته باشد.
 */
@Composable
fun AnimatedCounter(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(),
    animationDuration: Int = 1000
) {
    // استخراج عدد و بخش‌های متنی (پیشوند و پسوند)
    val parts = remember(text) { parseComplexString(text) }
    val targetValue = parts.number

    // فرمت کردن مقدار نهایی (target) برای AnimatedContent
    // این فقط زمانی تغییر می‌کند که text تغییر کند
    val targetFormattedNumber = remember(parts) {
        formatByTemplate(
            targetValue,
            parts.decimalPlaces,
            parts.hasCommas,
            parts.usePersianSeparator
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // پیشوند (مثل $ یا 💰)
        if (parts.prefix.isNotEmpty()) {
            Text(text = parts.prefix, style = style)
        }

        // بخش عددی انیمیشنی
        // استفاده از AnimatedContent برای افکت اسلاید عمودی
        // AnimatedContent فقط زمانی trigger می‌شود که targetFormattedNumber تغییر کند
        AnimatedContent(
            targetState = targetFormattedNumber,
            transitionSpec = {
                // عدد جدید از پایین وارد می‌شود و عدد قدیم به بالا می‌رود
                // استفاده از duration بیشتر و easing نرم‌تر برای انیمیشن smooth
                (slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f) // Ease-in-out برای نرمی بیشتر
                    )
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
                    )
                )).togetherWith(
                    slideOutVertically(
                        targetOffsetY = { fullHeight -> -fullHeight },
                        animationSpec = tween(
                            durationMillis = 500,
                            easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
                        )
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = 500,
                            easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
                        )
                    )
                )
            },
            label = "NumberSlideAnimation"
        ) { formattedTarget ->
            // داخل AnimatedContent، از Animatable برای انیمیشن تدریجی استفاده می‌کنیم
            AnimatedNumberText(
                targetText = formattedTarget,
                targetValue = targetValue,
                parts = parts,
                style = style,
                animationDuration = animationDuration
            )
        }

        // پسوند (مثل تتر یا تومان)
        if (parts.suffix.isNotEmpty()) {
            Text(text = parts.suffix, style = style)
        }
    }
}

/**
 * نگهدارنده اطلاعات استخراج شده از رشته ورودی
 */
private data class ParsedText(
    val number: Float,
    val prefix: String,
    val suffix: String,
    val decimalPlaces: Int,
    val hasCommas: Boolean,
    val usePersianSeparator: Boolean = false // آیا از جداکننده فارسی استفاده می‌شود
)

/**
 * تجزیه هوشمند رشته برای جدا کردن عدد از متن
 */
private fun parseComplexString(input: String): ParsedText {
    if (input == "..." || input.isEmpty()) return ParsedText(0f, "", input, 0, false, false)

    // تشخیص نوع جداکننده: فارسی (٬) یا انگلیسی (,)
    val hasPersianSeparator = input.contains('٬')
    val hasEnglishSeparator = input.contains(',')
    val usePersianSeparator = hasPersianSeparator || (!hasEnglishSeparator && input.contains("تومان"))

    // 1. یک فرمتر عدد برای Locale آمریکا ایجاد می‌کنیم (که از , برای هزارگان و . برای اعشار استفاده می‌کند)
    // این کار کد ما را مستقل از Locale پیش‌فرض دستگاه می‌کند.
    val numberFormat = NumberFormat.getNumberInstance(Locale.US)

    // 2. اگر جداکننده فارسی داریم، ابتدا آن را با انگلیسی جایگزین می‌کنیم برای parse
    val normalizedInput = if (hasPersianSeparator) {
        input.replace('٬', ',')
    } else {
        input
    }

    // 3. موقعیت شروع عدد را در رشته پیدا می‌کنیم.
    val parsePosition = java.text.ParsePosition(0)

    // 4. تلاش برای parse کردن عدد
    val number = numberFormat.parse(normalizedInput, parsePosition)?.toFloat()

    // 5. اگر هیچ عددی در ابتدای رشته پیدا نشد، یک جستجوی دیگر انجام می‌دهیم.
    if (number == null) {
        val firstDigitIndex = normalizedInput.indexOfFirst { it.isDigit() }
        if (firstDigitIndex == -1) return ParsedText(0f, "", input, 0, false, usePersianSeparator)

        parsePosition.index = firstDigitIndex
        val numberAfterPrefix = numberFormat.parse(normalizedInput, parsePosition)?.toFloat() ?: 0f

        val prefix = input.substring(0, firstDigitIndex)
        val numberEndIndex = parsePosition.index
        val suffix = input.substring(numberEndIndex)
        val numberStr = normalizedInput.substring(firstDigitIndex, numberEndIndex)

        return ParsedText(
            number = numberAfterPrefix,
            prefix = prefix,
            suffix = suffix,
            decimalPlaces = numberStr.substringAfter('.', "").length,
            hasCommas = numberStr.contains(',') || hasPersianSeparator,
            usePersianSeparator = usePersianSeparator
        )
    } else {
        // اگر عدد با موفقیت از ابتدای رشته parse شد
        val numberEndIndex = parsePosition.index
        val suffix = input.substring(numberEndIndex)
        val numberStr = normalizedInput.substring(0, numberEndIndex)

        return ParsedText(
            number = number,
            prefix = "",
            suffix = suffix,
            decimalPlaces = numberStr.substringAfter('.', "").length,
            hasCommas = numberStr.contains(',') || hasPersianSeparator,
            usePersianSeparator = usePersianSeparator
        )
    }
}

/**
 * فرمت کردن عدد بر اساس الگوی استخراج شده
 * استفاده از formatWithSeparator برای پشتیبانی از جداکننده فارسی و انگلیسی
 */
private fun formatByTemplate(
    value: Float,
    decimals: Int,
    useCommas: Boolean,
    usePersianSeparator: Boolean = false
): String {
    // اگر جداکننده نیاز نیست، فقط عدد را فرمت می‌کنیم
    if (!useCommas) {
        val pattern = StringBuilder("0")
        if (decimals > 0) {
            pattern.append(".")
            repeat(decimals) {
                pattern.append("0")
            }
        }
        val df = DecimalFormat(pattern.toString(), DecimalFormatSymbols(Locale.US))
        return df.format(value.toDouble())
    }

    // استفاده از formatWithSeparator برای جداکننده هزارگان
    return value.toDouble().formatWithSeparator(
        usePersianSeparator = usePersianSeparator,
        minFractionDigits = if (decimals > 0) decimals else 0,
        maxFractionDigits = decimals
    )
}

/**
 * کامپوننت داخلی برای انیمیشن تدریجی عدد داخل AnimatedContent
 */
@Composable
private fun AnimatedNumberText(
    targetText: String,
    targetValue: Float,
    parts: ParsedText,
    style: TextStyle,
    animationDuration: Int
) {
    // نگهداشتن مقدار فعلی برای انیمیشن تدریجی
    val animatedValue = remember(targetValue) { Animatable(targetValue) }

    // فرمت کردن مقدار در حال انیمیشن برای نمایش تدریجی
    val currentFormattedNumber = remember {
        derivedStateOf {
            formatByTemplate(
                animatedValue.value,
                parts.decimalPlaces,
                parts.hasCommas,
                parts.usePersianSeparator
            )
        }
    }

    LaunchedEffect(targetValue) {
        // انیمیشن تدریجی عدد با duration مناسب و easing نرم
        animatedValue.animateTo(
            targetValue = targetValue,
            animationSpec = tween(
                durationMillis = animationDuration,
                easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f) // Ease-in-out برای نرمی بیشتر
            )
        )
    }

    Text(
        text = currentFormattedNumber.value,
        style = style
    )
}

