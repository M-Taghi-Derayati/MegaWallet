package com.mtd.megawallet.ui.compose.screens.send

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.MegaWalletTheme

@Composable
internal fun GaslessBanner(
    enabled: Boolean,
    isPreviewReady: Boolean,
    errorMessage: String?,
    previewMessage: String?,
    onToggle: () -> Unit
) {
    val hasError = enabled && !errorMessage.isNullOrBlank()
    val borderColor = when {
        hasError -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
        enabled -> Color(0xFF6C63FF).copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val bgColor = when {
        hasError -> Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                MaterialTheme.colorScheme.error.copy(alpha = 0.04f)
            )
        )
        enabled -> Brush.horizontalGradient(
            listOf(
                Color(0xFF2D2560).copy(alpha = 0.8f),
                Color(0xFF1A1440).copy(alpha = 0.6f)
            )
        )
        else -> Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surface
            )
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bgColor).border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(if (enabled) Color(0xFF6C63FF).copy(0.25f) else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(
                        if (hasError) Icons.Default.PriorityHigh else Icons.Default.Bolt,
                        null,
                        tint = when {
                            hasError -> MaterialTheme.colorScheme.error
                            enabled -> Color(0xFF9C8FFF)
                            else -> MaterialTheme.colorScheme.onTertiary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "ارسال گس‌لس",
                        color = when {
                            hasError -> MaterialTheme.colorScheme.error
                            enabled -> Color(0xFFCBC6FF)
                            else -> MaterialTheme.colorScheme.tertiary
                        },
                        fontFamily = IranSansBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = when {
                            hasError -> errorMessage.orEmpty()
                            enabled && isPreviewReady -> previewMessage ?: "جزئیات هزینه از سرور دریافت شد"
                            enabled -> "در حال دریافت هزینه نهایی از سرور"
                            else -> "ارسال معمولی با کارمزد شبکه"
                        },
                        color = if (enabled && !hasError) Color.White else MaterialTheme.colorScheme.onTertiary,
                        fontFamily = IranSansRegular,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            GaslessTogglePill(enabled = enabled)
        }
    }
}

@Composable
private fun GaslessTogglePill(enabled: Boolean) {
    val trackColor by animateColorState(if (enabled) Color(0xFF6C63FF) else MaterialTheme.colorScheme.surfaceVariant)
    Box(
        modifier = Modifier.width(42.dp).height(24.dp).clip(RoundedCornerShape(12.dp)).background(trackColor).padding(3.dp),
        contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun animateColorState(target: Color): androidx.compose.runtime.State<Color> = animateColorAsState(targetValue = target, animationSpec = tween(220), label = "cAnim")

// ============================================
// Previews
// ============================================

@Preview(name = "GaslessBanner Enabled - Dark")
@Composable
private fun GaslessBannerEnabledDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                GaslessBanner(
                    enabled = true,
                    isPreviewReady = true,
                    errorMessage = null,
                    previewMessage = "جزئیات هزینه از سرور دریافت شد",
                    onToggle = {}
                )
            }
        }
    }
}

@Preview(name = "GaslessBanner Disabled - Light")
@Composable
private fun GaslessBannerDisabledLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                GaslessBanner(
                    enabled = false,
                    isPreviewReady = false,
                    errorMessage = null,
                    previewMessage = null,
                    onToggle = {}
                )
            }
        }
    }
}
