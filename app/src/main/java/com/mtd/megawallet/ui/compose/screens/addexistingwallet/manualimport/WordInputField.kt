package com.mtd.megawallet.ui.compose.screens.addexistingwallet.manualimport

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector3D
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mtd.common_ui.R
import kotlin.math.abs

/**
 * Animated input card for word entry.
 * Shows scale, offset, and alpha animations based on distance from current index.
 */
@Composable
fun AnimatedInputCard(
    modifier: Modifier = Modifier,
    index: Int,
    currentIndex: Int,
    text: String,
    onTextChange: (String) -> Unit,
    onClick: (Int) -> Unit
) {
    val distance = index - currentIndex
    val density = LocalDensity.current
    val latestOnTextChange by rememberUpdatedState(onTextChange)

    val anim = remember { Animatable(AnimatedState(1f, 0f, 1f), AnimatedState.VectorConverter) }

    LaunchedEffect(distance) {
        val target = AnimatedState(
            scale = when (distance) {
                0 -> 1f
                1 -> 0.9f
                else -> 0.8f
            },
            offsetY = with(density) {
                when {
                    distance < 0 -> -30.dp.toPx()
                    distance == 0 -> 0.dp.toPx()
                    distance == 1 -> 30.dp.toPx()
                    else -> 80.dp.toPx()
                }
            },
            alpha = when {
                distance == 0 -> 1f
                distance == 1 -> 0.8f
                distance < 0 -> 0.8f
                else -> 0f
            }
        )
        anim.animateTo(target, animationSpec = tween(400, easing = EaseOutCubic))
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = anim.value.scale
                scaleY = anim.value.scale
                translationY = anim.value.offsetY
                alpha = anim.value.alpha
            }
            .zIndex(if (distance == 0) 100f else -abs(distance).toFloat()),
        contentAlignment = Alignment.Center
    ) {
        InputCard(
            index = index,
            text = text,
            isEnabled = (distance == 0),
            onTextChange = latestOnTextChange,
            onClick = { onClick(index) }
        )
    }
}

/**
 * Input card component for word entry.
 */
@Composable
private fun InputCard(
    index: Int,
    text: String,
    onTextChange: (String) -> Unit,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    Card(
        modifier = Modifier
            .width(300.dp)
            .height(72.dp)
            .clickable(enabled = !isEnabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isEnabled) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1C1C1C) else Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) Color(0xFF252525) else Color(0xFFF7F8FA))
                .drawWithContent {
                    drawContent()
                    val strokeWidth = if (!isDark) 2.dp.toPx() else 0.dp.toPx()
                    drawRoundRect(
                        color = Color(0xFFC4C4C4),
                        style = Stroke(
                            width = strokeWidth,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(10f, 10f),
                                0f
                            )
                        ),
                        cornerRadius = CornerRadius(10.dp.toPx())
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // شماره کلمه
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (isDark) Color.Gray else Color(0xFF9E9E9E),
                fontFamily = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Medium)),
                modifier = Modifier.padding(end = 10.dp, start = 15.dp)
            )

            // ورودی متن
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = text,
                onValueChange = onTextChange,
                enabled = isEnabled,
                readOnly = !isEnabled,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 18.sp,
                    color = if (isDark) Color.White else Color(0xFF424242),
                    fontFamily = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Medium))
                ),
                placeholder = {
                    Text(
                        "کلمه را وارد کنید",
                        style = TextStyle(
                            color = if (isDark) Color.Gray.copy(alpha = 0.5f) else Color.LightGray,
                            fontSize = 18.sp,
                            fontFamily = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Medium))
                        )
                    )
                },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = Color(0xFF22C55E)
                )
            )
        }
    }
}

/**
 * Data class for animation state.
 */
@Stable
data class AnimatedState(val scale: Float, val offsetY: Float, val alpha: Float) {
    companion object {
        val VectorConverter = TwoWayConverter<AnimatedState, AnimationVector3D>(
            convertToVector = { AnimationVector3D(it.scale, it.offsetY, it.alpha) },
            convertFromVector = { AnimatedState(it.v1, it.v2, it.v3) }
        )
    }
}

