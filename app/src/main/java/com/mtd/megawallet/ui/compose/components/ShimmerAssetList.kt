package com.mtd.megawallet.ui.compose.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * حالتِ بارگذاریِ هر فهرستِ ارز — قرینهٔ [AssetListItems]، نه یک جملهٔ «در حال دریافت…».
 *
 * دلیلش فقط زیبایی نیست: فهرستِ ارزهای قابلِ تبدیل چند شبکه را موازی می‌گیرد و در آن فاصله صفحه
 * فقط یک خط متن بود، پس کاربر نمی‌دانست چند ردیف قرار است بیاید و لحظهٔ آمدنشان کلِ صفحه یک‌باره
 * جهش می‌کرد. شیمر همان ارتفاع و همان چیدمانِ ردیفِ واقعی را از قبل اشغال می‌کند.
 *
 * هندسه از همان `WalletScreenConstants`ی می‌آید که خودِ ردیف استفاده می‌کند؛ اگر اندازهٔ آیکون
 * آن‌جا عوض شود این هم با آن جابه‌جا می‌شود و دو حالت از هم نمی‌پاشند.
 */
@Composable
fun ShimmerAssetList(
    modifier: Modifier = Modifier,
    itemCount: Int = ShimmerConstants.DEFAULT_ITEM_COUNT
) {
    val brushProvider = rememberAssetShimmerBrush()

    Column(modifier = modifier.fillMaxWidth()) {
        repeat(itemCount) {
            ShimmerAssetRow(brushProvider = brushProvider)
        }
    }
}

/**
 * براشِ متحرکِ مشترکِ همهٔ بلوک‌های یک فهرست.
 *
 * مقدارِ انیمیشن داخلِ همین lambda خوانده می‌شود که `shimmerBackground` در فازِ draw صدایش می‌زند؛
 * در نتیجه هر فریم فقط redraw است و نه recompositionِ کلِ درخت.
 */
@Composable
private fun rememberAssetShimmerBrush(): () -> Brush {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val shimmerColors = remember(surfaceVariant) {
        listOf(
            surfaceVariant.copy(alpha = ShimmerConstants.SHIMMER_ALPHA_DARK),
            surfaceVariant.copy(alpha = ShimmerConstants.SHIMMER_ALPHA_LIGHT),
            surfaceVariant.copy(alpha = ShimmerConstants.SHIMMER_ALPHA_DARK)
        )
    }

    val transition = rememberInfiniteTransition(label = "AssetShimmer")
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
        label = "AssetShimmerTranslate"
    )

    return {
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim.value - GRADIENT_WIDTH, translateAnim.value - GRADIENT_WIDTH),
            end = Offset(translateAnim.value, translateAnim.value)
        )
    }
}

/**
 * قرینهٔ یک ردیفِ فهرست: بلوکِ آیکون با بجِ شبکه، نام و موجودی، و ارزشِ فیات در انتها.
 *
 * آیکون با [HexAssetShape] بریده می‌شود و نه دایره — وگرنه لحظهٔ جایگزینیِ شیمر با محتوای واقعی
 * شکلِ آیکون می‌پرید، که دقیقاً همان جهشی است که شیمر برای حذفش هست.
 */
@Composable
private fun ShimmerAssetRow(brushProvider: () -> Brush) {
    // همان رنگی که صفحه با آن پر شده؛ بجِ شبکه را از آیکونِ زیرش جدا نگه می‌دارد، مثلِ ماسکِ
    // `ic_pls` در ردیفِ واقعی.
    val badgeMask = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.background
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp, vertical = 12.dp),
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
                    .background(badgeMask)
                    .padding(2.dp)
                    .clip(HexAssetShape)
                    .shimmerBackground(brushProvider)
            )
        }

        Spacer(modifier = Modifier.width(WalletScreenConstants.ASSET_ICON_SPACING))

        Column(modifier = Modifier.weight(1f)) {
            ShimmerBar(width = 72.dp, height = 16.dp, brushProvider = brushProvider)
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBar(width = 108.dp, height = 12.dp, brushProvider = brushProvider)
        }

        Spacer(modifier = Modifier.width(12.dp))

        ShimmerBar(width = 64.dp, height = 16.dp, brushProvider = brushProvider)
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

/** عرضِ نوارِ درخشان و بردِ حرکتش — همان مقادیرِ بقیهٔ شیمرها تا سرعت یکی به‌نظر برسد. */
private const val GRADIENT_WIDTH = 500f
private const val MAX_TRANSLATE = 3000f

@Preview(name = "ShimmerAssetList", widthDp = 400, heightDp = 480)
@Composable
private fun ShimmerAssetListPreview() {
    MegaWalletTheme(darkTheme = false) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                ShimmerAssetList()
            }
        }
    }
}
