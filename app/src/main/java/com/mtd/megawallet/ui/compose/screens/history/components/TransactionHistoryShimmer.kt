package com.mtd.megawallet.ui.compose.screens.history.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtd.megawallet.ui.compose.animations.constants.ShimmerConstants
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants

@Composable
fun TransactionHistoryShimmer(
    modifier: Modifier = Modifier,
    itemCount: Int = 7
) {
    val brush = rememberHistoryShimmerBrush()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {

        item(key = "history_shimmer_header_today") {
            HistoryShimmerHeader(brush = brush, width = 86.dp)
        }

        items(itemCount) { index ->
            HistoryShimmerItem(
                brush = brush,
                isPendingShape = index == 1,
                centerPrimaryWidth = if (index % 2 == 0) 112.dp else 86.dp,
                centerSecondaryWidth = if (index % 3 == 0) 76.dp else 104.dp,
                amountWidth = if (index % 2 == 0) 88.dp else 68.dp,
                fiatWidth = if (index % 3 == 0) 58.dp else 72.dp
            )
        }

        item(key = "history_shimmer_header_previous") {
            HistoryShimmerHeader(brush = brush, width = 132.dp)
        }

        items(3) { index ->
            HistoryShimmerItem(
                brush = brush,
                isPendingShape = false,
                centerPrimaryWidth = if (index == 0) 94.dp else 120.dp,
                centerSecondaryWidth = if (index == 2) 86.dp else 110.dp,
                amountWidth = if (index == 1) 96.dp else 74.dp,
                fiatWidth = 64.dp
            )
        }
    }
}

@Composable
private fun rememberHistoryShimmerBrush(): Brush {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val shimmerColors = remember(surfaceVariant) {
        listOf(
            surfaceVariant.copy(alpha = ShimmerConstants.SHIMMER_ALPHA_DARK),
            surfaceVariant.copy(alpha = ShimmerConstants.SHIMMER_ALPHA_LIGHT),
            surfaceVariant.copy(alpha = ShimmerConstants.SHIMMER_ALPHA_DARK),
        )
    }
    val transition = rememberInfiniteTransition(label = "HistoryShimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = ShimmerConstants.ANIMATION_TARGET_VALUE,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = ShimmerConstants.ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "HistoryShimmerTranslate"
    )

    return remember(translateAnim.value, shimmerColors) {
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(x = translateAnim.value, y = translateAnim.value)
        )
    }
}

@Composable
private fun HistoryShimmerToolbar(brush: Brush) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        ShimmerBlock(
            brush = brush,
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.CenterEnd),
            shape = CircleShape
        )
    }
}

@Composable
private fun HistoryShimmerHeader(
    brush: Brush,
    width: Dp
) {
    ShimmerBlock(
        brush = brush,
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .width(width)
            .height(22.dp),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun HistoryShimmerItem(
    brush: Brush,
    isPendingShape: Boolean,
    centerPrimaryWidth: Dp,
    centerSecondaryWidth: Dp,
    amountWidth: Dp,
    fiatWidth: Dp
) {
    val outerModifier = Modifier
        .fillMaxWidth()
        .padding(
            horizontal = if (isPendingShape) 14.dp else 0.dp,
            vertical = if (isPendingShape) 5.dp else 0.dp
        )
        .clip(if (isPendingShape) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp))
        .background(
            if (isPendingShape) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.background
            }
        )

    Row(
        modifier = outerModifier.padding(
            horizontal = if (isPendingShape) 14.dp else 16.dp,
            vertical = if (isPendingShape) 14.dp else 12.dp
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_SIZE)) {
            ShimmerBlock(
                brush = brush,
                modifier = Modifier
                    .size(WalletScreenConstants.ASSET_ICON_SIZE)
                    .align(Alignment.Center),
                shape = CircleShape
            )
            ShimmerBlock(
                brush = brush,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.TopStart),
                shape = CircleShape
            )
            ShimmerBlock(
                brush = brush,
                modifier = Modifier
                    .size(WalletScreenConstants.ASSET_ICON_NETWORK_SIZE_SMALE)
                    .align(Alignment.BottomEnd),
                shape = CircleShape
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerBlock(
                    brush = brush,
                    modifier = Modifier
                        .width(54.dp)
                        .height(14.dp),
                    shape = RoundedCornerShape(7.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                ShimmerBlock(
                    brush = brush,
                    modifier = Modifier
                        .width(centerPrimaryWidth)
                        .height(14.dp),
                    shape = RoundedCornerShape(7.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBlock(
                brush = brush,
                modifier = Modifier
                    .width(centerSecondaryWidth)
                    .height(18.dp),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            ShimmerBlock(
                brush = brush,
                modifier = Modifier
                    .width(amountWidth)
                    .height(18.dp),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBlock(
                brush = brush,
                modifier = Modifier
                    .width(fiatWidth)
                    .height(12.dp),
                shape = RoundedCornerShape(6.dp)
            )
        }
    }
}

@Composable
private fun ShimmerBlock(
    brush: Brush,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}
