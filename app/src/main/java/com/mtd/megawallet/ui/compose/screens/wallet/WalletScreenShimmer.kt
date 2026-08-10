
package com.mtd.megawallet.ui.compose.screens.wallet

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import com.mtd.megawallet.ui.compose.animations.constants.ShimmerConstants
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.animations.shimmerBackground

@Composable
fun ShimmerWalletScreen(shimmerItemCount: Int = ShimmerConstants.DEFAULT_ITEM_COUNT) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val shimmerColors = remember(surfaceVariant) {
        listOf(
            surfaceVariant.copy(alpha = ShimmerConstants.SHIMMER_ALPHA_DARK),
            surfaceVariant.copy(alpha = ShimmerConstants.SHIMMER_ALPHA_LIGHT),
            surfaceVariant.copy(alpha = ShimmerConstants.SHIMMER_ALPHA_DARK),
        )
    }

    val transition = rememberInfiniteTransition(label = "Shimmer")

    // عرض ثابت نوار درخشان (Shimmer)
    val gradientWidth = 500f
    // مقداری بزرگتر از ابعاد صفحه تا مطمئن شویم ریست شدن انیمیشن کاملاً خارج از دید کاربر رخ می‌دهد
    val maxTranslate = 3000f

    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = maxTranslate,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = ShimmerConstants.ANIMATION_DURATION,
                easing = LinearEasing // حرکت یکنواخت برای پیوستگی کامل (به جای FastOutSlowIn)
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )

    // خواندن مقدارِ انیمیشن داخل این lambda انجام می‌شود که در فاز draw (توسط shimmerBackground)
    // صدا زده می‌شود؛ در نتیجه placeholder هر فریم فقط redraw می‌شود، نه recomposition کاملِ درخت.
    val brushProvider: () -> Brush = {
        Brush.linearGradient(
            colors = shimmerColors,
            // حرکت دادن گرادیان با اندازه ثابت به جای کشیدن آن
            start = Offset(translateAnim.value - gradientWidth, translateAnim.value - gradientWidth),
            end = Offset(translateAnim.value, translateAnim.value)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Shimmer for Total Balance Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = ShimmerConstants.TOTAL_BALANCE_PADDING_TOP,
                    bottom = ShimmerConstants.TOTAL_BALANCE_PADDING_BOTTOM
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(ShimmerConstants.TOTAL_BALANCE_WIDTH)
                    .height(ShimmerConstants.TOTAL_BALANCE_HEIGHT)
                    .clip(RoundedCornerShape(ShimmerConstants.TOTAL_BALANCE_CORNER_RADIUS))
                    .shimmerBackground(brushProvider)
            )
            Spacer(modifier = Modifier.height(ShimmerConstants.TOTAL_BALANCE_SUBTITLE_SPACING))
            Box(
                modifier = Modifier
                    .width(ShimmerConstants.TOTAL_BALANCE_SUBTITLE_WIDTH)
                    .height(ShimmerConstants.TOTAL_BALANCE_SUBTITLE_HEIGHT)
                    .clip(RoundedCornerShape(ShimmerConstants.TOTAL_BALANCE_SUBTITLE_CORNER_RADIUS))
                    .shimmerBackground(brushProvider)
            )
        }

        // 2. Shimmer for Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ShimmerConstants.TABS_PADDING_HORIZONTAL),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .width(ShimmerConstants.TAB_WIDTH_2)
                    .height(ShimmerConstants.TAB_HEIGHT)
                    .clip(RoundedCornerShape(ShimmerConstants.TAB_CORNER_RADIUS))
                    .shimmerBackground(brushProvider)
            )
            Spacer(modifier = Modifier.width(ShimmerConstants.TAB_SPACING))
            Box(
                modifier = Modifier
                    .width(ShimmerConstants.TAB_WIDTH_1)
                    .height(ShimmerConstants.TAB_HEIGHT)
                    .clip(RoundedCornerShape(ShimmerConstants.TAB_CORNER_RADIUS))
                    .shimmerBackground(brushProvider)
            )
        }


        HorizontalDivider(
            modifier = Modifier.padding(top = WalletScreenConstants.DIVIDER_SPACING_TOP),
            thickness = WalletScreenConstants.DIVIDER_THICKNESS,
            color =  MaterialTheme.colorScheme.surfaceVariant
        )


        // 3. Shimmer for Asset List Items
        Column(modifier = Modifier.padding(horizontal = ShimmerConstants.ASSET_LIST_PADDING_HORIZONTAL)) {
            repeat(shimmerItemCount) {
                ShimmerAssetItem(brushProvider)
            }
        }
    }
}

@Composable
private fun ShimmerAssetItem(brushProvider: () -> Brush) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ShimmerConstants.ASSET_ITEM_PADDING_VERTICAL),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ستون آخر (قیمت و درصد)
        Column(horizontalAlignment = Alignment.Start) {
            Box(
                modifier = Modifier
                    .width(ShimmerConstants.ASSET_PRICE_WIDTH)
                    .height(ShimmerConstants.ASSET_PRICE_HEIGHT)
                    .clip(RoundedCornerShape(ShimmerConstants.ASSET_NAME_CORNER_RADIUS))
                    .shimmerBackground(brushProvider)
            )
            Spacer(modifier = Modifier.height(ShimmerConstants.ASSET_NAME_SPACING))
            Box(
                modifier = Modifier
                    .width(ShimmerConstants.ASSET_PERCENTAGE_WIDTH)
                    .height(ShimmerConstants.ASSET_PERCENTAGE_HEIGHT)
                    .clip(RoundedCornerShape(ShimmerConstants.ASSET_NAME_CORNER_RADIUS))
                    .shimmerBackground(brushProvider)
            )
        }

        // ستون وسط (نام ارز)
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(ShimmerConstants.ASSET_NAME_WIDTH)
                    .height(ShimmerConstants.ASSET_NAME_HEIGHT)
                    .clip(RoundedCornerShape(ShimmerConstants.ASSET_NAME_CORNER_RADIUS))
                    .shimmerBackground(brushProvider)
            )
            Spacer(modifier = Modifier.height(ShimmerConstants.ASSET_NAME_SPACING))
            Box(
                modifier = Modifier
                    .width(ShimmerConstants.ASSET_SYMBOL_WIDTH)
                    .height(ShimmerConstants.ASSET_SYMBOL_HEIGHT)
                    .clip(RoundedCornerShape(ShimmerConstants.ASSET_NAME_CORNER_RADIUS))
                    .shimmerBackground(brushProvider)
            )
        }

        Spacer(modifier = Modifier.width(ShimmerConstants.ASSET_ICON_SPACING))

        // آیکون دایره‌ای
        Box(
            modifier = Modifier
                .size(ShimmerConstants.ASSET_ICON_SIZE)
                .clip(CircleShape)
                .shimmerBackground(brushProvider)
        )
    }
}

@Preview
@Composable
fun ShimmerPreview(){
    MaterialTheme() {
        ShimmerWalletScreen()
    }
}
