package com.mtd.megawallet.ui.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.MegaWalletTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ConfirmSliderButton(
    enabled: Boolean,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "برای تایید بکشید",
    loadingDurationMs: Long = 2000L,
    isSuccess: Boolean = false,
    isError: Boolean = false
) {
    val density = LocalDensity.current
    val thumbSizeDp = 56.dp
    val trackHeight = 64.dp
    val trackPadding = 4.dp

    var fullTrackWidthDp by remember { mutableStateOf(0.dp) }
    var trackWidthPx by remember { mutableIntStateOf(0) }

    val maxOffsetPx = remember(trackWidthPx) {
        (trackWidthPx - with(density) {
            (thumbSizeDp + trackPadding * 2).toPx()
        }).coerceAtLeast(0f)
    }

    val offsetX = remember { Animatable(0f) }
    var sliderState by remember { mutableStateOf(SliderState.Idle) }
    val scope = rememberCoroutineScope()

    // ───── عرض انیمیشنی ترک ─────
    val animatedTrackWidth by animateDpAsState(
        targetValue = when (sliderState) {
            SliderState.Collapsing,
            SliderState.Loading,
            SliderState.Success -> trackHeight
            else -> fullTrackWidthDp
        },
        animationSpec = when (sliderState) {
            SliderState.Collapsing -> tween(500, easing = FastOutSlowInEasing)
            else -> tween(400, easing = FastOutSlowInEasing)
        },
        label = "trackWidth"
    )

    // ✅ جایگزین مطمئن برای finishedListener
    LaunchedEffect(sliderState) {
        when (sliderState) {
            SliderState.Collapsing -> {
                delay(550)
                if (sliderState == SliderState.Collapsing) {
                    sliderState = SliderState.Loading
                    onConfirmed() // Trigger actual submission once collapsed
                }
            }
            else -> {}
        }
    }

    // Monitor external success state
    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            sliderState = SliderState.Success
        }
    }

    // Monitor external error state or manual reset
    LaunchedEffect(isError) {
        if (isError && (sliderState == SliderState.Loading || sliderState == SliderState.Collapsing)) {
            sliderState = SliderState.Idle
            offsetX.animateTo(0f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow))
        }
    }

    LaunchedEffect(enabled) {
        if (enabled && sliderState == SliderState.Success) {
            delay(1000)
            sliderState = SliderState.Idle
            offsetX.snapTo(0f)
        }
    }

    val trackColor by animateColorAsState(
        targetValue = when (sliderState) {
            SliderState.Success -> Color(0xFF34C759)
            SliderState.Loading -> Color(0xFFE8E8ED)
            else -> if (enabled) Color(0xFFF2F2F7) else Color(0xFFE8E8E8)
        },
        animationSpec = tween(400),
        label = "trackColor"
    )

    val dragProgress = if (maxOffsetPx > 0f) {
        (offsetX.value / maxOffsetPx).coerceIn(0f, 1f)
    } else 0f

    val textAlpha by animateFloatAsState(
        targetValue = when (sliderState) {
            SliderState.Idle -> 1f
            SliderState.Dragging -> (1f - dragProgress * 1.5f).coerceAtLeast(0f)
            else -> 0f
        },
        animationSpec = tween(200),
        label = "textAlpha"
    )

    val checkScale by animateFloatAsState(
        targetValue = if (sliderState == SliderState.Success) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkScale"
    )


    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(trackHeight)
                .onSizeChanged {
                    trackWidthPx = it.width
                    fullTrackWidthDp = with(density) { it.width.toDp() }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(animatedTrackWidth)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(trackColor),
                contentAlignment = Alignment.Center
            ) {

                if (sliderState == SliderState.Idle ||
                    sliderState == SliderState.Dragging
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(
                                with(density) {
                                    (offsetX.value + (thumbSizeDp + trackPadding * 2).toPx()).toDp()
                                }
                            )
                            .clip(RoundedCornerShape(trackHeight / 2))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF007AFF).copy(alpha = 0.08f),
                                        Color(0xFF007AFF).copy(alpha = 0.18f)
                                    )
                                )
                            )
                    )
                }

                // Text + Chevrons
                if ((sliderState == SliderState.Idle ||
                            sliderState == SliderState.Dragging) && enabled
                ) {

                    CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Rtl
                    ) {
                        Row(
                            modifier = Modifier
                                .graphicsLayer { alpha = textAlpha }
                                .padding(end = thumbSizeDp + trackPadding * 2),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val chevronTransition = rememberInfiniteTransition(label = "chevrons")
                            repeat(3) { index ->
                                val chevronAlpha by chevronTransition.animateFloat(
                                    initialValue = 0.15f,
                                    targetValue = 0.7f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, delayMillis = index * 150),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "chevron_${index}"  // âœ… fixed interpolation
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = null,
                                    tint = Color(0xFF8E8E93),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .offset(x = ((-6) * index).dp)
                                        .graphicsLayer { alpha = chevronAlpha }
                                )
                            }

                            Text(
                                text = text,
                                color = Color(0xFF8E8E93),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = IranSansRegular,
                            )
                        }
                    }
                }


                if ((sliderState == SliderState.Idle ||
                            sliderState == SliderState.Dragging) && !enabled
                ) {
                    Text(
                        text = text,
                        color = Color(0xFFAEAEB2),
                        fontSize = 15.sp,
                        fontFamily = IranSansRegular,
                        fontWeight = FontWeight.Medium
                    )
                }


                if (sliderState == SliderState.Loading) {
                    val loadingTransition = rememberInfiniteTransition(label = "loading")
                    val spinAngle by loadingTransition.animateFloat(
                        initialValue = 0f, targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1100, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "spin"
                    )
                    val sweepAngle by loadingTransition.animateFloat(
                        initialValue = 30f, targetValue = 270f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "sweep"
                    )
                    Canvas(modifier = Modifier.size(30.dp)) {
                        val strokeWidth = 3.dp.toPx()
                        val diameter = min(size.width, size.height)
                        val rect = Size(diameter - strokeWidth, diameter - strokeWidth)
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                        drawArc(
                            color = Color(0xFF007AFF),
                            startAngle = spinAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = rect,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }


                if (sliderState == SliderState.Success) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "تأیید شد",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp).graphicsLayer { scaleX = checkScale; scaleY = checkScale }
                    )
                }


                if (sliderState == SliderState.Idle ||
                    sliderState == SliderState.Dragging
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(trackPadding)
                            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                            .size(thumbSizeDp)
                            .shadow(8.dp, CircleShape,
                                ambientColor = Color(0xFF007AFF),
                                spotColor = Color(0xFF007AFF))
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = if (enabled) {
                                        listOf(Color(0xFF007AFF), Color(0xFF0055D4))
                                    } else {
                                        listOf(Color(0xFFBBBBBB), Color(0xFF999999))
                                    }
                                )
                            )
                            .then(
                                if (enabled) {
                                    Modifier.pointerInput(maxOffsetPx) {
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                sliderState = SliderState.Dragging
                                            },
                                            onDragEnd = {
                                                scope.launch {
                                                    if (offsetX.value >= maxOffsetPx * 0.85f) {
                                                        offsetX.animateTo(
                                                            maxOffsetPx,
                                                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                                                        )
                                                        sliderState = SliderState.Collapsing
                                                    } else {
                                                        offsetX.animateTo(
                                                            0f,
                                                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                                                        )
                                                        sliderState = SliderState.Idle
                                                    }
                                                }
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                scope.launch {
                                                    offsetX.snapTo(
                                                        (offsetX.value + dragAmount)
                                                            .coerceIn(0f, maxOffsetPx)
                                                    )
                                                }
                                            }
                                        )
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "بکشید",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * وضعیت‌های مختلف اسلایدر
 */
private enum class SliderState {
    Idle,       // آماده کشیدن
    Dragging,   // در حال کشیدن
    Collapsing, // جمع شدن به دایره
    Loading,    // نمایش پراگرس دایره‌ای چرخان
    Success     // تیک سبز ✅
}

@Preview(name = "ConfirmSliderButton - Light")
@Composable
private fun ConfirmSliderButtonLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                ConfirmSliderButton(enabled = true, onConfirmed = {})
            }
        }
    }
}

@Preview(name = "ConfirmSliderButton - Dark")
@Composable
private fun ConfirmSliderButtonDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                ConfirmSliderButton(enabled = false, onConfirmed = {})
            }
        }
    }
}
