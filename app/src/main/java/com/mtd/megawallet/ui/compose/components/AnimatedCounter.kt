package com.mtd.megawallet.ui.compose.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * شمارندهٔ عددیِ متحرک — سبکِ «اودومتر» رقم‌به‌رقم (شبیهِ شمارندهٔ Family).
 *
 * هر رقم به‌صورتِ مستقل می‌غلتد (slide عمودی + بلورِ حرکتیِ سبک) و **خواندنِ انیمیشن در فازِ draw**
 * انجام می‌شود (`Animatable` داخلِ `graphicsLayer`)، پس با هر تغییرِ مقدار **recomposition per-frame
 * رخ نمی‌دهد** — برخلافِ نسخهٔ قبلی که کلِ رشته را با `AnimatedContent` سوییچ می‌کرد و لگ می‌داد.
 *
 * API دست‌نخورده مانده تا همهٔ استفاده‌کننده‌ها (موجودیِ کل، جزئیاتِ دارایی، لیستِ ارسال) خودکار بهبود یابند.
 */
@Composable
fun AnimatedCounter(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(),
    animationDuration: Int = 240,
    styleVariantKey: Any? = null,
    animate: Boolean = true
) {
    val parts = remember(text) { parseComplexString(text) }
    val formatter = remember(parts.decimalPlaces, parts.hasCommas, parts.usePersianSeparator) {
        buildFormatter(
            decimals = parts.decimalPlaces,
            useCommas = parts.hasCommas,
            usePersianSeparator = parts.usePersianSeparator
        )
    }
    val formattedNumber = remember(parts.number, formatter) {
        formatter.format(parts.number.toDouble())
    }

    if (!animate) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = parts.prefix + formattedNumber + parts.suffix,
                modifier = modifier,
                style = style
            )
        }
        return
    }

    var previousNumber by remember { mutableFloatStateOf(parts.number) }
    var previousStyleVariant by remember { mutableStateOf(styleVariantKey) }
    val styleChanged = styleVariantKey != previousStyleVariant

    val direction = when {
        styleChanged                  -> RollDirection.None // currency swap — own animation
        parts.number < previousNumber -> RollDirection.Down // value decreased → roll down
        parts.number > previousNumber -> RollDirection.Up   // value increased → roll up
        else                          -> RollDirection.None
    }

    LaunchedEffect(parts.number) { previousNumber = parts.number }
    LaunchedEffect(styleVariantKey) { previousStyleVariant = styleVariantKey }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (parts.prefix.isNotEmpty()) {
            Text(text = parts.prefix, style = style)
        }

        RollingDigitsText(
            text = formattedNumber,
            direction = direction,
            style = style,
            durationMs = animationDuration,
            isCurrencySwap = styleChanged
        )

        if (parts.suffix.isNotEmpty()) {
            Text(text = parts.suffix, style = style)
        }
    }
}

private enum class RollDirection { Up, Down, None }

@Composable
private fun RollingDigitsText(
    text: String,
    direction: RollDirection,
    style: TextStyle,
    durationMs: Int,
    isCurrencySwap: Boolean = false
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        if (isCurrencySwap) {
            // تعویضِ ارز (تومان ↔ دلار): کلِ مقدار به‌سبکِ iOS از کنار وارد/خارج می‌شود.
            AnimatedContent(
                targetState = text,
                transitionSpec = {
                    (
                        slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            initialOffsetX = { it / 3 }
                        ) + scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            initialScale = 0.82f
                        ) + fadeIn(tween((durationMs * 0.7f).toInt()))
                    ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(
                                (durationMs * 0.6f).toInt(),
                                easing = FastOutSlowInEasing
                            ),
                            targetOffsetX = { -(it / 4) }
                        ) + scaleOut(
                            animationSpec = tween((durationMs * 0.6f).toInt()),
                            targetScale = 0.88f
                        ) + fadeOut(tween((durationMs * 0.5f).toInt()))
                    )
                },
                label = "CurrencySwap"
            ) { value ->
                Text(text = value, style = style)
            }
        } else {
            RollingCounter(
                text = text,
                rollUp = direction != RollDirection.Down, // increase/none → roll up
                style = style,
                durationMs = durationMs
            )
        }
    }
}

/**
 * شمارندهٔ اودومترِ عمومی؛ رشتهٔ خام (مثلِ «12,542,636» یا مبلغِ تایپ‌شدهٔ «0.25») را **عیناً** نشان می‌دهد
 * (بدونِ reformat) و فقط ارقامِ تغییرکرده را می‌غلتاند — انیمیشن در فازِ draw، بدونِ recomposition per-frame.
 * برای فیلدهایی مثلِ مبلغِ Send که نباید عددشان دوباره‌فرمت شود مناسب است.
 *
 * @param rollUp جهتِ غلتش؛ اگر null باشد از روی افزایش/کاهشِ مقدار خودکار تشخیص داده می‌شود.
 */
@Composable
fun RollingCounter(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(),
    durationMs: Int = 240,
    rollUp: Boolean? = null
) {
    var prevText by remember { mutableStateOf(text) }
    val autoUp = remember(text) {
        val cur = text.filter { it.isDigit() || it == '.' }.toFloatOrNull()
        val old = prevText.filter { it.isDigit() || it == '.' }.toFloatOrNull()
        if (cur != null && old != null) cur >= old else true
    }
    LaunchedEffect(text) { prevText = text }
    val up = rollUp ?: autoUp

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            val n = text.length
            text.forEachIndexed { i, c ->
                // کلید بر اساس فاصله از راست تا هویتِ رقمِ یکان/دهگان… هنگام تغییرِ طولِ عدد پایدار بماند.
                key(n - 1 - i) {
                    if (c.isDigit()) {
                        DigitSlot(target = c, rollUp = up, style = style, durationMs = durationMs)
                    } else {
                        Text(text = glyphStr(c), style = style)
                    }
                }
            }
        }
    }
}

/**
 * یک رقمِ منفرد که هنگام تغییر، به‌صورتِ عمودی می‌غلتد. مقدارِ انیمیشن فقط داخلِ `graphicsLayer`
 * خوانده می‌شود (فازِ draw) تا هیچ recomposition‌ای per-frame نداشته باشیم.
 */
@Composable
private fun DigitSlot(
    target: Char,
    rollUp: Boolean,
    style: TextStyle,
    durationMs: Int
) {
    val density = LocalDensity.current
    val rollPx = remember(style.fontSize, density) {
        with(density) {
            if (style.fontSize != TextUnit.Unspecified) style.fontSize.toPx() else 48f
        }
    }
    var shown by remember { mutableStateOf(target) }
    var prev by remember { mutableStateOf(target) }
    var animating by remember { mutableStateOf(false) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target != shown) {
            prev = shown
            shown = target
            animating = true
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
            animating = false
        }
    }

    val sign = if (rollUp) 1f else -1f
    val blurRadius = if (animating) 1.5.dp else 0.dp

    Box {
        // رقمِ جدید: از سمتِ ورود به مرکز می‌آید
        Text(
            text = shown.toString(),
            style = style,
            modifier = Modifier
                .blur(blurRadius, BlurredEdgeTreatment.Unbounded)
                .graphicsLayer {
                    val p = progress.value
                    translationY = (1f - p) * rollPx * sign
                    alpha = p
                }
        )
        // رقمِ قبلی: فقط حین انیمیشن، از مرکز خارج می‌شود
        if (animating) {
            Text(
                text = prev.toString(),
                style = style,
                modifier = Modifier
                    .blur(blurRadius, BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer {
                        val p = progress.value
                        translationY = -p * rollPx * sign
                        alpha = 1f - p
                    }
            )
        }
    }
}

private fun glyphStr(c: Char): String = if (c == ' ') " " else c.toString()

private data class ParsedText(
    val number: Float,
    val prefix: String,
    val suffix: String,
    val decimalPlaces: Int,
    val hasCommas: Boolean,
    val usePersianSeparator: Boolean
)

private fun parseComplexString(input: String): ParsedText {
    if (input == "..." || input.isEmpty()) return ParsedText(0f, "", input, 0, false, false)

    val hasPersianSeparator = input.contains('٬')
    val hasEnglishSeparator = input.contains(',')
    val usePersianSeparator = hasPersianSeparator || (!hasEnglishSeparator && input.contains("تومان"))
    val normalized = if (hasPersianSeparator) input.replace('٬', ',') else input
    val match = NUMBER_REGEX.find(normalized)
        ?: return ParsedText(0f, "", input, 0, false, usePersianSeparator)

    val numberToken = match.value
    val number = numberToken.replace(",", "").toFloatOrNull() ?: 0f
    val prefix = input.substring(0, match.range.first)
    val suffix = input.substring(match.range.last + 1)
    val decimals = numberToken.substringAfter('.', "").length

    return ParsedText(
        number = number,
        prefix = prefix,
        suffix = suffix,
        decimalPlaces = decimals,
        hasCommas = numberToken.contains(',') || hasPersianSeparator,
        usePersianSeparator = usePersianSeparator
    )
}

private fun buildFormatter(
    decimals: Int,
    useCommas: Boolean,
    usePersianSeparator: Boolean
): DecimalFormat {
    val pattern = buildString {
        append(if (useCommas) "#,##0" else "0")
        if (decimals > 0) {
            append('.')
            repeat(decimals) { append('0') }
        }
    }
    val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = if (usePersianSeparator) '٬' else ','
        decimalSeparator = '.'
    }
    return DecimalFormat(pattern, symbols).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
        isGroupingUsed = useCommas
    }
}

private val NUMBER_REGEX = Regex("[-+]?\\d[\\d,]*(?:\\.\\d+)?")
