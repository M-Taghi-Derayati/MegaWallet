package com.mtd.megawallet.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mtd.common_ui.R
import com.mtd.common_ui.theme.AssetIcon
import com.mtd.core.utils.BalanceFormatter
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.FiatCurrency
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.core.NetworkType
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.screens.send.sampleConfirmAsset
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode
import com.mtd.common_ui.theme.InterBoldBold
import com.mtd.common_ui.theme.InterRegularMedium
import com.mtd.common_ui.theme.IranSansBoldBold
import com.mtd.common_ui.theme.IranSansRegularBold
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.common_ui.theme.NetworkIcon

/**
 * لیست دارایی‌های قابل ارسال با ورود مرحله‌ای (staggered) هر آیتم.
 */
@Composable
internal fun TokenList(
    fiatCurrency: FiatCurrency,
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

                AssetListItems(fiatCurrency = fiatCurrency, modifier = Modifier, asset = asset, onClick = { onTokenClick(asset) })
            }
        }
    }
}



/**
 * نشانِ **منشأ** کنارِ نامِ توکن.
 *
 * ⚠️ `true` فقط یعنی «فهرستی که به آن اتکا می‌کنیم این قرارداد را روی این شبکه منتشر کرده» — نه
 * ممیزی، نه تأییدِ مالی، نه توصیهٔ سرمایه‌گذاری؛ متنِ دسترس‌پذیری هم عمداً همین را می‌گوید و از
 * «تأییدشده» پرهیز می‌کند.
 *
 * `false` فقط یک هشدارِ خنثاست و هیچ‌وقت ردیف را پنهان یا غیرفعال نمی‌کند: یک توکنِ واقعیِ تازه
 * دقیقاً همین‌طور به نظر می‌رسد. `null` (همهٔ دارایی‌های باندل و صفحهٔ ارسال) یعنی چیزی اعلام نشده
 * و هیچ نشانی گرفته نمی‌شود.
 */
@Composable
private fun AssetProvenanceMark(verified: Boolean?) {
    if (verified == null) return
    Spacer(modifier = Modifier.width(4.dp))
    Text(
        text = if (verified) "•" else "!",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (verified) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        modifier = Modifier.semantics {
            contentDescription = if (verified) {
                "ثبت‌شده در فهرست‌های معتبر"
            } else {
                "در فهرست‌های معتبر ثبت نشده"
            }
        }
    )
}

@Composable
private fun AssetListItems(
    fiatCurrency: FiatCurrency,
    modifier: Modifier = Modifier,
    asset: AssetItem,
    onClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()

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
            // آیکون اصلی ارز
            Box(
                modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                contentAlignment = Alignment.Center
            ) {
                AssetIcon(
                    iconUrl = asset.iconUrl,
                    symbol = asset.symbol,
                    contentDescription = "${asset.name} icon",
                    modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE)
                )
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

                    NetworkIcon(
                        iconUrl = asset.networkIconUrl,
                        contentDescription = "${asset.networkName} network icon",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(WalletScreenConstants.ASSET_ICON_NETWORK_PADDING)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(WalletScreenConstants.ASSET_ICON_SPACING))


        // بخش نام و بالانس نمادین (وسط)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = asset.faName ?: asset.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IranSansBoldBold,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                )
                AssetProvenanceMark(verified = asset.verified)
            }

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

                // TASK-56 — [AssetItem.formattedDisplayBalance] already holds the value in the
                // selected currency (HomeViewModel keeps both strings and picks one), so the send
                // picker cannot show dollars while the wallet list behind it shows تومان.
                AnimatedCounter(
                    text = asset.formattedDisplayBalance.trim(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = InterRegularMedium
                    ),
                    animationDuration = WalletScreenConstants.ASSET_ANIMATION_DURATION
                )
                Text(
                    text = BalanceFormatter.fiatSymbol(fiatCurrency),
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
 *
 * [networkType] برابر `null` یعنی هیچ فیلتری روی نوع شبکه اعمال نشود؛ صفحهٔ تبدیل مقصدی ندارد
 * که آدرسش باید با شبکه سازگار باشد، پس فقط «موجودی بزرگ‌تر از صفر» برایش معیار است.
 */
internal fun buildSendableAssetList(
    fiatCurrency: FiatCurrency,
    usdToIrrRate: CurrencyRate?,
    source: List<AssetItem>,
    networkType: NetworkType?,
    networkTypeResolver: (String) -> NetworkType?
): List<AssetItem> {
    return source.mapNotNull { asset ->
        if (asset.isGroupHeader && asset.groupAssets.isNotEmpty()) {
            val matched = asset.groupAssets.filter { sub ->
                sub.balanceRaw > BigDecimal.ZERO &&
                    (networkType == null || networkTypeResolver(sub.networkId) == networkType)
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
                        id = "send_${asset.id}_${networkType?.name ?: "ANY"}",
                        networkId = "GROUP",
                        networkName = "",
                        networkFaName = null,
                        balance = BalanceFormatter.formatBalance(totalRaw, first.decimals),
                        // A merged group is a new item, so its display strings are rebuilt here in
                        // both currencies, the same way HomeViewModel does for the wallet list.
                        balanceUsdt = "${BalanceFormatter.formatFiatValue(totalUsd, FiatCurrency.USD, null, withSymbol = false)} ",
                        balanceIrr = "${BalanceFormatter.formatFiatValue(totalUsd, FiatCurrency.TOMAN, usdToIrrRate, withSymbol = false)} ",
                        formattedDisplayBalance =
                            "${BalanceFormatter.formatFiatValue(totalUsd, fiatCurrency, usdToIrrRate, withSymbol = false)} ",
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
                if (networkType == null || itemNetworkType == networkType) asset else null
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
                    fiatCurrency = FiatCurrency.USD,
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
