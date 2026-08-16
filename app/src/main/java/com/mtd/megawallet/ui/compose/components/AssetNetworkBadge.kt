package com.mtd.megawallet.ui.compose.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.mtd.common_ui.R
import com.mtd.common_ui.theme.AssetIcon
import com.mtd.common_ui.theme.NetworkIcon
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants

/**
 * آیکونِ ارز با بجِ شبکه روی گوشهٔ پایینی‌اش — تنها شکلِ نمایشِ «این ارز، روی این شبکه» در اپ.
 *
 * تا امروز این چیدمان در فهرستِ دارایی‌ها، کارتِ داراییِ فازِ مبلغ و شیمرِ فهرستِ توکن جداگانه
 * نوشته شده بود و فلوی تبدیل اصلاً بج نداشت؛ یعنی کاربر در همان صفحه دو زبانِ متفاوت می‌دید.
 * حالا هر سه از این‌جا می‌آیند و اندازه‌ها به نسبتِ [iconSize] مقیاس می‌شوند، پس یک آیکونِ ۵۶dp
 * در صفحهٔ تأیید همان تناسبی را دارد که ردیفِ ۴۴dpِ فهرست.
 *
 * ماسکِ `ic_pls` زیرِ بج عمداً به رنگِ **پس‌زمینه** است، نه شفاف: بج روی خودِ آیکونِ ارز می‌نشیند و
 * بدونِ آن دو تصویر روی هم می‌افتادند.
 */
@Composable
fun AssetIconWithNetworkBadge(
    iconUrl: String?,
    symbol: String,
    networkIconUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = WalletScreenConstants.ASSET_ICON_MAIN_SIZE,
    showBadge: Boolean = true,
    /**
     * لایهٔ خودِ آیکون. فلوی تبدیل این را جایگزین می‌کند تا آیکونِ در حالِ پروازش را همان‌جا
     * بنشاند و بج هم با آن حرکت کند.
     */
    icon: @Composable (Modifier) -> Unit = { iconModifier ->
        AssetIcon(
            iconUrl = iconUrl,
            symbol = symbol,
            contentDescription = contentDescription,
            modifier = iconModifier
        )
    }
) {
    val isDark = isSystemInDarkTheme()
    val badgeSize = iconSize * BADGE_RATIO
    val badgePadding = badgeSize * BADGE_PADDING_RATIO

    Box(modifier = modifier.size(iconSize * CONTAINER_RATIO)) {
        Box(
            modifier = Modifier.size(iconSize),
            contentAlignment = Alignment.Center
        ) {
            icon(Modifier.size(iconSize))
        }

        if (showBadge) {
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_pls),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = if (isDark) Color.Black else Color.White
                )

                NetworkIcon(
                    iconUrl = networkIconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(badgePadding)
                )
            }
        }
    }
}

/**
 * نسبت‌ها از همان مقادیرِ فهرستِ دارایی مشتق می‌شوند (۴۸ / ۴۴ / ۲۶ / ۱٫۱)، پس اندازهٔ پیش‌فرض
 * عیناً همان چیزی است که قبلاً دستی نوشته شده بود و بقیهٔ اندازه‌ها با آن هم‌شکل می‌مانند.
 */
private val CONTAINER_RATIO =
    WalletScreenConstants.ASSET_ICON_SIZE / WalletScreenConstants.ASSET_ICON_MAIN_SIZE
private val BADGE_RATIO =
    WalletScreenConstants.ASSET_ICON_NETWORK_SIZE_LARGE / WalletScreenConstants.ASSET_ICON_MAIN_SIZE
private val BADGE_PADDING_RATIO =
    WalletScreenConstants.ASSET_ICON_NETWORK_PADDING / WalletScreenConstants.ASSET_ICON_NETWORK_SIZE_LARGE
