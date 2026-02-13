
package com.mtd.megawallet.ui.compose.screens.wallet

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


/**
 * نمایش حالت لودینگ به صورت Shimmer
 * شبیه به ساختار اصلی صفحه طراحی شده تا پرش UI کمتر باشد
 * 
 * @param shimmerItemCount تعداد آیتم‌های shimmer برای لیست دارایی‌ها (پیش‌فرض: 6)
 */
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
        label = "ShimmerTranslate"
    )

    val brush = remember(translateAnim.value) {
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(x = translateAnim.value, y = translateAnim.value)
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
            // جایگزین عدد بزرگ
            Box(
                modifier = Modifier
                    .width(ShimmerConstants.TOTAL_BALANCE_WIDTH)
                    .height(ShimmerConstants.TOTAL_BALANCE_HEIGHT)
                    .clip(RoundedCornerShape(ShimmerConstants.TOTAL_BALANCE_CORNER_RADIUS))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(ShimmerConstants.TOTAL_BALANCE_SUBTITLE_SPACING))
            // جایگزین متن کوچک زیرش
            Box(
                modifier = Modifier
                    .width(ShimmerConstants.TOTAL_BALANCE_SUBTITLE_WIDTH)
                    .height(ShimmerConstants.TOTAL_BALANCE_SUBTITLE_HEIGHT)
                    .clip(RoundedCornerShape(ShimmerConstants.TOTAL_BALANCE_SUBTITLE_CORNER_RADIUS))
                    .background(brush)
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
                    .background(brush)
            )
            Spacer(modifier = Modifier.width(ShimmerConstants.TAB_SPACING))
            Box(
                modifier = Modifier
                    .width(ShimmerConstants.TAB_WIDTH_1)
                    .height(ShimmerConstants.TAB_HEIGHT)
                    .clip(RoundedCornerShape(ShimmerConstants.TAB_CORNER_RADIUS))
                    .background(brush)
            )
        }
        
        HorizontalDivider(
            modifier = Modifier.padding(
                top = ShimmerConstants.DIVIDER_PADDING_TOP,
                bottom = ShimmerConstants.DIVIDER_PADDING_BOTTOM
            ),
            thickness = ShimmerConstants.DIVIDER_THICKNESS,
            color = MaterialTheme.colorScheme.onSurface
        )

        // 3. Shimmer for Asset List Items
        Column(modifier = Modifier.padding(horizontal = ShimmerConstants.ASSET_LIST_PADDING_HORIZONTAL)) {
            repeat(shimmerItemCount) {
                ShimmerAssetItem(brush)
            }
        }
    }
}

@Composable
private fun ShimmerAssetItem(brush: Brush) {
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
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(ShimmerConstants.ASSET_NAME_SPACING))
            Box(
                modifier = Modifier
                    .width(ShimmerConstants.ASSET_PERCENTAGE_WIDTH)
                    .height(ShimmerConstants.ASSET_PERCENTAGE_HEIGHT)
                    .clip(RoundedCornerShape(ShimmerConstants.ASSET_NAME_CORNER_RADIUS))
                    .background(brush)
            )
        }

        // ستون وسط (نام ارز)
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(ShimmerConstants.ASSET_NAME_WIDTH)
                    .height(ShimmerConstants.ASSET_NAME_HEIGHT)
                    .clip(RoundedCornerShape(ShimmerConstants.ASSET_NAME_CORNER_RADIUS))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(ShimmerConstants.ASSET_NAME_SPACING))
            Box(
                modifier = Modifier
                    .width(ShimmerConstants.ASSET_SYMBOL_WIDTH)
                    .height(ShimmerConstants.ASSET_SYMBOL_HEIGHT)
                    .clip(RoundedCornerShape(ShimmerConstants.ASSET_NAME_CORNER_RADIUS))
                    .background(brush)
            )
        }


        Spacer(modifier = Modifier.width(ShimmerConstants.ASSET_ICON_SPACING))


        // آیکون دایره‌ای
        Box(
            modifier = Modifier
                .size(ShimmerConstants.ASSET_ICON_SIZE)
                .clip(CircleShape)
                .background(brush)
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
