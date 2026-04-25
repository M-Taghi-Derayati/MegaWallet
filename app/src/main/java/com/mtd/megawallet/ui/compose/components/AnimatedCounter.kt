package com.mtd.megawallet.ui.compose.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import java.lang.Integer.max
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Composable
fun AnimatedCounter(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(),
    animationDuration: Int = 240,
    styleVariantKey: Any? = null
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

    var previousNumber by remember { mutableFloatStateOf(parts.number) }
    var previousStyleVariant by remember { mutableStateOf(styleVariantKey) }
    val styleChanged = styleVariantKey != previousStyleVariant

    val direction = when {
        styleChanged       -> RollDirection.None  // currency swap — uses its own animation
        parts.number < previousNumber -> RollDirection.Up
        parts.number > previousNumber -> RollDirection.Down
        else               -> RollDirection.None
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
            // Force lightweight when style swaps (currency toggle) OR many chars changed
            forceLightweightTransition = styleChanged,
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
    forceLightweightTransition: Boolean,
    isCurrencySwap: Boolean = false
) {
    var previousText by remember { mutableStateOf(text) }

    val maxLen = max(previousText.length, text.length)
    val newPadded = text.padStart(maxLen, ' ')
    val oldPadded = previousText.padStart(maxLen, ' ')
    val changedChars = remember(oldPadded, newPadded) {
        oldPadded.indices.count { oldPadded[it] != newPadded[it] }
    }

    // Lightweight path: whole-text transition
    // Used when: currency toggles, many chars changed, or force-flagged
    val useLightweightTransition = forceLightweightTransition || maxLen > 6 || changedChars > 2

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        if (useLightweightTransition) {
            AnimatedContent(
                targetState = text,
                transitionSpec = {
                    if (initialState == targetState || direction == RollDirection.None && !isCurrencySwap) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else if (isCurrencySwap) {
                        // ─── Currency Toggle (e.g. Toman ↔ Dollar) ───
                        // iOS Wallet-style: new value scales+slides in from trailing side,
                        // old value scales out toward leading side.
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
                    } else if (direction == RollDirection.Up) {
                        // Value increased → roll UP (new comes from below)
                        (
                            slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = 0.68f,
                                    stiffness = 700f
                                ),
                                initialOffsetY = { it / 2 }
                            ) + fadeIn(tween((durationMs * 0.6f).toInt()))
                        ).togetherWith(
                            slideOutVertically(
                                animationSpec = tween(
                                    (durationMs * 0.55f).toInt(),
                                    easing = FastOutSlowInEasing
                                ),
                                targetOffsetY = { -(it / 2) }
                            ) + fadeOut(tween((durationMs * 0.5f).toInt()))
                        )
                    } else {
                        // Value decreased → roll DOWN (new comes from above)
                        (
                            slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = 0.68f,
                                    stiffness = 700f
                                ),
                                initialOffsetY = { -(it / 2) }
                            ) + fadeIn(tween((durationMs * 0.6f).toInt()))
                        ).togetherWith(
                            slideOutVertically(
                                animationSpec = tween(
                                    (durationMs * 0.55f).toInt(),
                                    easing = FastOutSlowInEasing
                                ),
                                targetOffsetY = { it / 2 }
                            ) + fadeOut(tween((durationMs * 0.5f).toInt()))
                        )
                    }
                },
                label = "RollingNumberText"
            ) { value ->
                Text(text = value, style = style)
            }
        } else {
            // ─── Per-digit path (fewer changed chars → more precise roll) ───
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(maxLen) { index ->
                    val targetChar = newPadded[index]

                    if (oldPadded[index] == targetChar || direction == RollDirection.None) {
                        Text(
                            text = if (targetChar == ' ') "\u00A0" else targetChar.toString(),
                            style = style
                        )
                    } else {
                        AnimatedContent(
                            targetState = targetChar,
                            transitionSpec = {
                                if (direction == RollDirection.Up) {
                                    // Roll UP with spring bounce
                                    (
                                        slideInVertically(
                                            animationSpec = spring(
                                                dampingRatio = 0.58f,
                                                stiffness = 900f
                                            ),
                                            initialOffsetY = { it }
                                        ) + fadeIn(tween((durationMs * 0.5f).toInt()))
                                    ).togetherWith(
                                        slideOutVertically(
                                            animationSpec = tween(
                                                (durationMs * 0.45f).toInt(),
                                                easing = FastOutSlowInEasing
                                            ),
                                            targetOffsetY = { -it }
                                        ) + fadeOut(tween((durationMs * 0.4f).toInt()))
                                    )
                                } else {
                                    // Roll DOWN with spring bounce
                                    (
                                        slideInVertically(
                                            animationSpec = spring(
                                                dampingRatio = 0.58f,
                                                stiffness = 900f
                                            ),
                                            initialOffsetY = { -it }
                                        ) + fadeIn(tween((durationMs * 0.5f).toInt()))
                                    ).togetherWith(
                                        slideOutVertically(
                                            animationSpec = tween(
                                                (durationMs * 0.45f).toInt(),
                                                easing = FastOutSlowInEasing
                                            ),
                                            targetOffsetY = { it }
                                        ) + fadeOut(tween((durationMs * 0.4f).toInt()))
                                    )
                                }
                            },
                            label = "RollingDigit_$index"
                        ) { char ->
                            Text(
                                text = if (char == ' ') "\u00A0" else char.toString(),
                                style = style
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(text) { previousText = text }
}

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

