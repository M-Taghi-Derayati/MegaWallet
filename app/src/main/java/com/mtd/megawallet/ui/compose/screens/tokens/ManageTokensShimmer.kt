package com.mtd.megawallet.ui.compose.screens.tokens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtd.common_ui.theme.HexAssetShape
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.megawallet.ui.compose.animations.constants.ShimmerConstants
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.animations.shimmerBackground

/**
 * حالتِ بارگذاریِ فهرستِ توکن — جایگزینِ اسپینرِ وسطِ صفحه.
 *
 * دلیلش فقط زیبایی نیست: فهرستِ پیش‌فرض چند شبکه را **موازی** می‌گیرد و در آن فاصله صفحه کاملاً
 * خالی بود، پس کاربر نمی‌دانست چند ردیف قرار است بیاید و لحظهٔ آمدنشان کلِ صفحه یک‌باره جهش
 * می‌کرد. شیمر همان ارتفاع و همان چیدمانِ [TokenManageRow] را از قبل اشغال می‌کند.
 *
 * هندسه عمداً از همان `WalletScreenConstants`ی می‌آید که خودِ ردیف استفاده می‌کند — اگر اندازهٔ
 * آیکون آن‌جا عوض شود، این هم با آن جابه‌جا می‌شود و دو حالت از هم نمی‌پاشند.
 */
@Composable
fun ShimmerManageTokensList(itemCount: Int = ShimmerConstants.DEFAULT_ITEM_COUNT) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val shimmerColors = remember(surfaceVariant) {
        listOf(
            surfaceVariant.copy(alpha = ShimmerConstants.SHIMMER_ALPHA_DARK),
            surfaceVariant.copy(alpha = ShimmerConstants.SHIMMER_ALPHA_LIGHT),
            surfaceVariant.copy(alpha = ShimmerConstants.SHIMMER_ALPHA_DARK),
        )
    }

    val transition = rememberInfiniteTransition(label = "ManageTokensShimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = MAX_TRANSLATE,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = ShimmerConstants.ANIMATION_DURATION,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ManageTokensShimmerTranslate"
    )

    // مقدارِ انیمیشن داخلِ همین lambda خوانده می‌شود که `shimmerBackground` در فازِ draw صدایش
    // می‌زند؛ در نتیجه هر فریم فقط redraw است و نه recompositionِ کلِ درخت.
    val brushProvider: () -> Brush = {
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim.value - GRADIENT_WIDTH, translateAnim.value - GRADIENT_WIDTH),
            end = Offset(translateAnim.value, translateAnim.value)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(16.dp))

        // تیترِ بخش هم شیمر می‌گیرد: اولین چیزی که واقعاً می‌آید یک سرفصل است، نه یک ردیف.
        ShimmerBar(width = 96.dp, height = 14.dp, brushProvider = brushProvider)
        Spacer(modifier = Modifier.height(6.dp))
        ShimmerBar(width = 160.dp, height = 10.dp, brushProvider = brushProvider)
        Spacer(modifier = Modifier.height(10.dp))

        repeat(itemCount) {
            ShimmerTokenRow(brushProvider = brushProvider)
        }
    }
}

/** نوارِ خاکستریِ پایه — تنها شکلی که در کلِ این فایل تکرار می‌شود. */
@Composable
private fun ShimmerBar(
    width: Dp,
    height: Dp,
    brushProvider: () -> Brush
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(ShimmerConstants.ASSET_NAME_CORNER_RADIUS))
            .shimmerBackground(brushProvider)
    )
}

/**
 * قرینهٔ [TokenManageRow]: بلوکِ آیکون با بجِ شبکه، دو خط متن، و کلیدِ گردِ انتهایی.
 *
 * آیکون با [HexAssetShape] بریده می‌شود و نه دایره — وگرنه لحظهٔ جایگزینیِ شیمر با محتوای واقعی
 * شکلِ آیکون می‌پرید، که دقیقاً همان جهشی است که شیمر برای حذفش هست.
 */
@Composable
private fun ShimmerTokenRow(brushProvider: () -> Brush) {
    // همان رنگی که شیت با آن پر شده؛ بجِ شبکه را از آیکونِ زیرش جدا نگه می‌دارد، مثلِ ماسکِ
    // `ic_pls` در ردیفِ واقعی.
    val sheetBackground = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.background
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_SIZE)) {
            Box(
                modifier = Modifier
                    .size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE)
                    .clip(HexAssetShape)
                    .shimmerBackground(brushProvider)
            )

            Box(
                modifier = Modifier
                    .size(WalletScreenConstants.ASSET_ICON_NETWORK_SIZE_LARGE)
                    .align(Alignment.BottomEnd)
                    .clip(HexAssetShape)
                    .background(sheetBackground)
                    .padding(2.dp)
                    .clip(HexAssetShape)
                    .shimmerBackground(brushProvider)
            )
        }

        Spacer(modifier = Modifier.width(WalletScreenConstants.ASSET_ICON_SPACING))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerBar(width = 56.dp, height = 16.dp, brushProvider = brushProvider)
                Spacer(modifier = Modifier.weight(1f))
                ShimmerBar(width = 64.dp, height = 14.dp, brushProvider = brushProvider)
            }
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBar(width = 132.dp, height = 12.dp, brushProvider = brushProvider)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .shimmerBackground(brushProvider)
        )
    }

    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
}

/** عرضِ نوارِ درخشان و بردِ حرکتش — همان مقادیرِ `ShimmerWalletScreen` تا سرعت یکی به‌نظر برسد. */
private const val GRADIENT_WIDTH = 500f
private const val MAX_TRANSLATE = 3000f

@Preview(name = "ManageTokens Shimmer", widthDp = 400, heightDp = 560)
@Composable
private fun ShimmerManageTokensPreview() {
    MegaWalletTheme(darkTheme = false) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                ShimmerManageTokensList()
            }
        }
    }
}
