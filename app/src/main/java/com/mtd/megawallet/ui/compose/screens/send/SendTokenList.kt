package com.mtd.megawallet.ui.compose.screens.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import com.mtd.common_ui.R
import com.mtd.core.utils.BalanceFormatter
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.core.NetworkType
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.components.AnimatedCounter
import com.mtd.megawallet.ui.compose.screens.wallet.getLocalIconResId
import com.mtd.megawallet.ui.compose.screens.wallet.getNetworkIconResId
import com.mtd.megawallet.ui.compose.screens.wallet.getPlaceholderIconResId
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode
import com.mtd.common_ui.theme.InterBoldBold
import com.mtd.common_ui.theme.InterRegularMedium
import com.mtd.common_ui.theme.IranSansBoldBold
import com.mtd.common_ui.theme.IranSansRegularBold
import com.mtd.common_ui.theme.MegaWalletTheme

/**
 * لیست دارایی‌های قابل ارسال با ورود مرحله‌ای (staggered) هر آیتم.
 */
@Composable
internal fun TokenList(
    assets: List<AssetItem>,
    selectedAssetId: String?,
    onTokenClick: (AssetItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(items = assets, key = { _, it -> it.id }) { index, asset ->
            var isVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(index * 50L)
                isVisible = true
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(400)),
                label = "StaggeredItem"
            ) {

                AssetListItems(modifier = Modifier,asset,onClick = { onTokenClick(asset) })
            }
        }
    }
}



@Composable
private fun AssetListItems(
    modifier: Modifier = Modifier,
    asset: AssetItem,
    onClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val imageLoader = LocalContext.current.imageLoader


    // تلاش برای پیدا کردن آیکون لوکال با فرمت ic_symbol (مثلا ic_btc)
    val localIconResId = remember(asset.symbol) {
        getLocalIconResId(asset.symbol)
    }
    val localIconNetworkResId = remember(asset.networkId) {
        getNetworkIconResId(asset.networkId)
    }

    // جداسازی مقدار و نماد برای انیمیشن
    val balanceAmount = remember(asset.balance, asset.symbol) {
        asset.balance
    }

    Row(
        modifier = modifier // ✅ modifier از بیرون اعمال می‌شود
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        // بخش آیکون‌ها (اصلی + بج شبکه)
        Box(
            modifier = Modifier
                .size(WalletScreenConstants.ASSET_ICON_SIZE)
        ) {
            // آیکون اصلی ارز (Local یا Remote)
            if (localIconResId != 0) {
                Box(
                    modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = localIconResId),
                        contentDescription = "${asset.name} icon",
                        modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                        contentScale = ContentScale.Fit,
                        colorFilter = null
                    )
                }
            }
            else {
                val placeholderResId = remember { getPlaceholderIconResId() }
                Box(
                    modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = asset.iconUrl,
                        contentDescription = "${asset.name} icon",
                        modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                        contentScale = ContentScale.Fit,
                        placeholder = painterResource(id = placeholderResId),
                        error = painterResource(id = placeholderResId),
                        fallback = painterResource(id = placeholderResId),
                        imageLoader = imageLoader
                    )
                }
            }

            // بج شبکه (پایین سمت راست)
            if (asset.networkName.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(WalletScreenConstants.ASSET_ICON_NETWORK_SIZE_LARGE)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pls),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = if (isDark) Color.Black else Color.White
                    )

                    Image(
                        painter = painterResource(id = localIconNetworkResId),
                        contentDescription = "${asset.networkName} network icon",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(WalletScreenConstants.ASSET_ICON_NETWORK_PADDING),
                        contentScale = ContentScale.Fit,
                        colorFilter = null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(WalletScreenConstants.ASSET_ICON_SPACING))


        // بخش نام و بالانس نمادین (وسط)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = asset.faName ?: asset.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = IranSansBoldBold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
            )

            // انیمیشن موجودی: عدد خارج می‌شود، نماد جای آن را می‌گیرد
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.animateContentSize() // انیمیشن برای تغییر سایز و مکان
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // نمایش Circle Chart برای گروه‌ها (داخل انیمیشن)

                    // نمایش مقدار عددی
                    AnimatedCounter(
                        text = balanceAmount,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.tertiary,
                            fontFamily = IranSansRegularBold
                        ),
                        animationDuration = WalletScreenConstants.ASSET_ANIMATION_DURATION
                    )
                    Spacer(modifier = Modifier.width(WalletScreenConstants.ASSET_BALANCE_SPACING))
                }

                // نمایش نماد ارز (همیشه ثابت)
                // وقتی عدد حذف شود، این متن به سمت چپ (جای عدد) منتقل می‌شود
                Spacer(modifier = Modifier.width(1.dp))
                Text(
                    text = asset.symbol,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontFamily = InterBoldBold
                    )
                )
            }
        }

        // بخش قیمت و درصد تغییرات (چپ)
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.animateContentSize()
        ) {
            // انیمیشن تغییر بین مقدار دلار/تومان و ستاره
            Row(verticalAlignment = Alignment.CenterVertically) {

                AnimatedCounter(
                    text = asset.balanceUsdt.trim(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = InterRegularMedium
                    ),
                    animationDuration = WalletScreenConstants.ASSET_ANIMATION_DURATION
                )
                Text(
                    text = "$",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = WalletScreenConstants.ASSET_PRICE_SYMBOL_FONT_SIZE,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = InterRegularMedium
                    ),
                    modifier = Modifier.padding(start = WalletScreenConstants.ASSET_PRICE_SYMBOL_PADDING_END)
                )


            }
        }
    }


}

/**
 * ساخت لیست دارایی‌های قابل ارسال بر اساس نوع شبکهٔ گیرنده؛ گروه‌های چند-شبکه‌ای
 * تجمیع می‌شوند و بر اساس ارزش دلاری مرتب می‌گردند.
 */
internal fun buildSendableAssetList(
    source: List<AssetItem>,
    networkType: NetworkType,
    networkTypeResolver: (String) -> NetworkType?
): List<AssetItem> {
    return source.mapNotNull { asset ->
        if (asset.isGroupHeader && asset.groupAssets.isNotEmpty()) {
            val matched = asset.groupAssets.filter { sub ->
                sub.balanceRaw > BigDecimal.ZERO && networkTypeResolver(sub.networkId) == networkType
            }
            when {
                matched.isEmpty() -> null
                matched.size == 1 -> matched.first()
                else -> {
                    val first = matched.first()
                    val totalRaw = matched.fold(BigDecimal.ZERO) { acc, sub -> acc + sub.balanceRaw }
                    val totalUsd = matched.fold(BigDecimal.ZERO) { acc, sub -> acc + (sub.balanceRaw * sub.priceUsdRaw) }
                    val avgPrice = if (totalRaw > BigDecimal.ZERO) {
                        totalUsd.divide(totalRaw, 18, RoundingMode.HALF_UP)
                    } else {
                        BigDecimal.ZERO
                    }

                    asset.copy(
                        id = "send_${asset.id}_${networkType.name}",
                        networkId = "GROUP",
                        networkName = "",
                        networkFaName = null,
                        balance = BalanceFormatter.formatBalance(totalRaw, first.decimals),
                        balanceUsdt = "${BalanceFormatter.formatUsdValue(totalUsd, false)} ",
                        balanceRaw = totalRaw,
                        priceUsdRaw = avgPrice,
                        isGroupHeader = true,
                        groupAssets = matched
                    )
                }
            }
        } else {
            if (asset.balanceRaw <= BigDecimal.ZERO) {
                null
            } else {
                val itemNetworkType = networkTypeResolver(asset.networkId)
                if (itemNetworkType == networkType) asset else null
            }
        }
    }.sortedByDescending { it.balanceRaw * it.priceUsdRaw }
}

// ============================================
// Previews
// ============================================

@Preview(name = "TokenList - Light")
@Composable
private fun TokenListLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                TokenList(
                    assets = listOf(
                        sampleConfirmAsset,
                        sampleConfirmAsset.copy(id = "USDT", symbol = "USDT", faName = "تتر", balance = "500 USDT", balanceUsdt = "$500.00")
                    ),
                    selectedAssetId = null,
                    onTokenClick = {}
                )
            }
        }
    }
}
