package com.mtd.megawallet.ui.compose.screens.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.MegaWalletTheme

enum class FeeMode {
    DIRECT,
    SMART,
    CREDIT
}

enum class TabState {
    READY,
    LOADING,
    DISABLED
}

private data class TabInfo(
    val mode: FeeMode,
    val label: String,
    val icon: ImageVector
)

/**
 * نوار انتخابِ حالتِ کارمزد (مستقیم / هوشمند / اعتباری).
 */
@Composable
internal fun FeeTabBar(
    selectedMode: FeeMode,
    onModeChange: (FeeMode) -> Unit,
    tabStates: Map<FeeMode, TabState>,
    getTabFee: (FeeMode) -> String
) {
    val tabs = listOf(
        TabInfo(FeeMode.DIRECT, "مستقیم",  Icons.Default.Bolt),
        TabInfo(FeeMode.SMART, "هوشمند",  Icons.Default.AutoAwesome),
        TabInfo(FeeMode.CREDIT, "اعتباری",  Icons.Default.CreditCard)
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        tabs.forEach { tab ->
            val state = tabStates[tab.mode] ?: TabState.READY
            val isActive = selectedMode == tab.mode
            val isDisabled = state == TabState.DISABLED
            val isTabLoading = state == TabState.LOADING

            FeeTab(
                tab = tab,
                isActive = isActive,
                isDisabled = isDisabled,
                isLoading = isTabLoading,
                feeValue = if (isDisabled) null else getTabFee(tab.mode),
                onClick = {
                    if (!isDisabled) {
                        onModeChange(tab.mode)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FeeTab(
    tab: TabInfo,
    isActive: Boolean,
    isDisabled: Boolean,
    isLoading: Boolean,
    feeValue: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alphaAnim by animateFloatAsState(
        targetValue = if (isDisabled) 0.35f else 1f,
        animationSpec = tween(200),
        label = "tabAlpha"
    )

    val labelColor by animateColorAsState(
        targetValue = when {
            isDisabled -> Color.White.copy(alpha = 0.25f)
            isActive -> Color.White
            else -> Color.White.copy(alpha = 0.4f)
        },
        animationSpec = tween(200),
        label = "labelColor"
    )

    val feeColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF818CF8) else Color.White.copy(alpha = 0.3f),
        animationSpec = tween(200),
        label = "feeColor"
    )

    Box(
        modifier = modifier
            .alpha(alphaAnim)
            .clickable(
                enabled = !isDisabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Label row with icon/emoji
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isDisabled) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                Text(
                    text = tab.label,
                    color = labelColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Fee row with small icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Small icon or spinner
                when {
                    isLoading -> {
                        TabSpinner(
                            modifier = Modifier.size(10.dp),
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    !isDisabled -> {
                        SmallFeeIcon(
                            mode = tab.mode,
                            tint = feeColor,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }

                // Fee value or status text
                Text(
                    text = when {
                        isDisabled -> "غیرفعال"
                        isLoading -> "دریافت..."
                        else -> feeValue ?: "..."
                    },
                    color = if (isDisabled) Color.White.copy(alpha = 0.2f) else feeColor,
                    fontSize = if (isDisabled || isLoading) 10.sp else 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Active indicator line
            AnimatedVisibility(
                visible = isActive && !isDisabled,
                enter = fadeIn(tween(200)) + expandHorizontally(),
                exit = fadeOut(tween(200)) + shrinkHorizontally()
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(40.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color(0xFF818CF8))
                )
            }
        }
    }
}

@Composable
private fun TabSpinner(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(modifier = modifier.rotate(rotation)) {
        // Simple circle spinner using Canvas or Icon
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = color,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun SmallFeeIcon(
    mode: FeeMode,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val icon = when (mode) {
        FeeMode.DIRECT -> Icons.Default.Bolt
        FeeMode.SMART -> Icons.Default.Schedule
        FeeMode.CREDIT -> Icons.Default.CreditCard
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

// ============================================
// Previews
// ============================================

@Composable
private fun FeeTabBarPreviewContent() {
    FeeTabBar(
        selectedMode = FeeMode.DIRECT,
        onModeChange = {},
        tabStates = mapOf(
            FeeMode.DIRECT to TabState.READY,
            FeeMode.SMART to TabState.LOADING,
            FeeMode.CREDIT to TabState.DISABLED
        ),
        getTabFee = { mode ->
            when (mode) {
                FeeMode.DIRECT -> "$0.15"
                FeeMode.SMART -> "ETH 1"
                FeeMode.CREDIT -> "ETH 1"
            }
        }
    )
}

@Preview(name = "FeeTabBar - Light")
@Composable
private fun FeeTabBarLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) { FeeTabBarPreviewContent() }
        }
    }
}

@Preview(name = "FeeTabBar - Dark")
@Composable
private fun FeeTabBarDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) { FeeTabBarPreviewContent() }
        }
    }
}
