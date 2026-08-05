package com.mtd.megawallet.ui.compose.screens.wallet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.platform.testTag
import com.mtd.megawallet.ui.compose.TestTags
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.mtd.common_ui.R
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.FiatCurrency
import com.mtd.domain.model.HomeUiState
import com.mtd.domain.model.NetworkShare
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.components.AnimatedCounter
import com.mtd.common_ui.theme.AssetIcon
import com.mtd.common_ui.theme.NetworkIcon
import com.mtd.megawallet.viewmodel.HomeViewModel
import java.math.BigDecimal
import com.mtd.common_ui.theme.InterBoldBold
import com.mtd.common_ui.theme.InterRegularMedium
import com.mtd.common_ui.theme.IranSansBoldBold
import com.mtd.common_ui.theme.IranSansLight
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.IranSansRegularBold
import com.mtd.common_ui.theme.IranSansRegularMedium


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreens(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    onAssetClick: (AssetItem, Rect) -> Unit,
    listItemModifier: (String) -> Modifier,
    userScrollEnabled: Boolean = true,
    captureItemBounds: Boolean = true,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTabIndex by viewModel.selectedTab.collectAsStateWithLifecycle()
    val tabs = listOf("توکن‌ها", "کلکسیون‌ها")

    // وضعیت مخفی بودن موجودی
    var isBalanceHidden by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        when (val state = uiState) {
            is HomeUiState.Success -> {
                var isVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { isVisible = true }

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(animationSpec = tween(500)),
                ) {
                    var isRefreshing by remember { mutableStateOf(false) }
                    LaunchedEffect(state.isUpdating) {
                        if (!state.isUpdating) {
                            isRefreshing = false
                        }
                    }

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            viewModel.refreshData()
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag(TestTags.WALLET_LIST),
                            userScrollEnabled = userScrollEnabled
                        ) {
                            // بخش موجودی کل
                            item {
                                TotalBalanceSection(
                                    totalBalance = when (state.displayCurrency) {
                                        FiatCurrency.USD -> state.totalBalanceUsdt
                                        FiatCurrency.TOMAN -> state.totalBalanceIrr
                                    },
                                    displayCurrency = state.displayCurrency,
                                    isUpdating = state.isUpdating,
                                    isBalanceHidden = isBalanceHidden,
                                    onToggleHidden = { isBalanceHidden = !isBalanceHidden }
                                )
                            }

                            // تب‌ها و قیمت تتر
                            // تب‌ها و قیمت تتر
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // تب‌ها (سمت راست)
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        SecondaryScrollableTabRow(
                                            selectedTabIndex = selectedTabIndex,
                                            modifier = Modifier.wrapContentWidth(),
                                            containerColor = Color.Transparent,
                                            edgePadding = 0.dp,
                                            divider = {} // جداکننده زیر تب‌ها را خودمان جدا می‌زنیم
                                        ) {
                                            tabs.forEachIndexed { index, title ->
                                                Tab(
                                                    selected = selectedTabIndex == index,
                                                    onClick = {/* viewModel.onTabSelected(index)*/ },//todo اینو درست کنم
                                                    text = {
                                                        Text(
                                                            text = title,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            color = MaterialTheme.colorScheme.tertiary,
                                                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                                            fontFamily = IranSansRegular
                                                        )
                                                    })
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // قیمت تتر (سمت چپ)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "تتر: ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiary,
                                            fontFamily = IranSansLight
                                        )
                                        Text(
                                            text = "${state.tetherPriceIrr} تومان",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontFamily = IranSansRegular
                                        )
                                    }
                                }

                                // خط خاکستری زیر تب‌ها (سرتاسری)
                                HorizontalDivider(
                                    modifier = Modifier.padding(top = WalletScreenConstants.DIVIDER_SPACING_TOP),
                                    thickness = WalletScreenConstants.DIVIDER_THICKNESS,
                                    color =  MaterialTheme.colorScheme.surfaceVariant
                                )

                                Spacer(modifier = Modifier.height(WalletScreenConstants.TABS_SPACING_BOTTOM))
                            }

                            // لیست دارایی‌ها (فقط برای تب Tokens)
                            if (selectedTabIndex == 0) {
                                items(
                                    items = state.assets,
                                    key = { it.id }
                                ) { asset ->
                                    var itemBounds by remember { mutableStateOf(Rect.Zero) }
                                    val boundsModifier = if (captureItemBounds) {
                                        Modifier.onGloballyPositioned {
                                            itemBounds = it.boundsInWindow()
                                        }
                                    } else {
                                        Modifier
                                    }

                                    AssetListItems(
                                        modifier = listItemModifier(asset.id)
                                            .then(boundsModifier),
                                        asset = asset,
                                        displayCurrency = state.displayCurrency,
                                        isBalanceHidden = isBalanceHidden,
                                        onClick = { onAssetClick(asset, itemBounds) }
                                    )
                                }
                            } else {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No collectibles found",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(WalletScreenConstants.ASSET_LIST_BOTTOM_SPACING)) }
                        }
                    }
                }
            }
            // خطا قبلاً به همین شاخهٔ shimmer می‌افتاد، پس هر شکستی به‌شکلِ «هنوز در حال بارگذاری»
            // دیده می‌شد: بدون پیام، بدون راهِ خروج، برای همیشه.
            is HomeUiState.Error -> {
                WalletErrorState(
                    message = state.message,
                    onRetry = { viewModel.retry() }
                )
            }

            HomeUiState.Loading -> {
                ShimmerWalletScreen()
            }
        }
    }
}

/**
 * حالتِ خطای صفحهٔ کیف پول. تنها جایی که [HomeUiState.Error] دیده می‌شود؛ بدون این، خطا از
 * شاخهٔ پیش‌فرض رد می‌شد و کاربر یک shimmerِ بی‌پایان می‌دید.
 */
@Composable
private fun WalletErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WalletScreenConstants.ERROR_STATE_PADDING),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            fontFamily = IranSansRegularMedium,
            fontSize = WalletScreenConstants.ERROR_STATE_MESSAGE_SIZE,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(WalletScreenConstants.ERROR_STATE_SPACING))

        Button(onClick = onRetry) {
            Text(
                text = "تلاش دوباره",
                fontFamily = IranSansRegularMedium,
                fontSize = WalletScreenConstants.ERROR_STATE_ACTION_SIZE
            )
        }
    }
}

/**
 * بخش نمایش موجودی تجمیعی
 */
@Composable
private fun TotalBalanceSection(
    totalBalance: String,
    displayCurrency: FiatCurrency,
    isUpdating: Boolean,
    isBalanceHidden: Boolean,
    onToggleHidden: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = WalletScreenConstants.TOTAL_BALANCE_PADDING_TOP,
                bottom = WalletScreenConstants.TOTAL_BALANCE_PADDING_BOTTOM
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ردیف موجودی کل با چیدمان خاص (Superscript طور)
        // قابلیت کلیک برای مخفی کردن/نمایش
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleHidden
                )
                .animateContentSize()
        ) {
            Crossfade(
                targetState = isBalanceHidden,
                animationSpec = tween(durationMillis = WalletScreenConstants.CROSSFADE_DURATION),
                label = "TotalBalanceCrossfade"
            ) { hidden ->
                // استفاده از Box برای اینکه Crossfade سایز رو درست هندل کنه و پرش نداشته باشه
                Box(contentAlignment = Alignment.Center) {
                    if (hidden) {
                        // حالت مخفی: ****
                        Text(
                            text = "*****",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = WalletScreenConstants.TOTAL_BALANCE_FONT_SIZE,
                                color = MaterialTheme.colorScheme.onTertiary,
                                fontFamily = InterRegularMedium,
                                letterSpacing = WalletScreenConstants.TOTAL_BALANCE_HIDDEN_LETTER_SPACING
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center)
                                .padding(top = WalletScreenConstants.TOTAL_BALANCE_HIDDEN_PADDING_TOP)
                        )
                    } else {
                        // حالت نمایش عادی - با auto-resize برای کل Row
                        AutoResizeBalanceRow(
                            totalBalance = totalBalance.trim(),
                            displayCurrency = displayCurrency,
                            animationDuration = WalletScreenConstants.ANIMATION_DURATION_TOTAL_BALANCE
                        )
                    }
                }
            }
        }
    }

        // زیرنویس (تغییرات 24 ساعته) + لودینگ
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isUpdating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(WalletScreenConstants.LOADING_INDICATOR_SIZE),
                    strokeWidth = WalletScreenConstants.LOADING_INDICATOR_STROKE_WIDTH,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }


@Composable
private fun AssetListItems(
    modifier: Modifier = Modifier,
    asset: AssetItem,
    displayCurrency: FiatCurrency,
    isBalanceHidden: Boolean,
    onClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val displayBalance = remember(displayCurrency, asset.balanceUsdt, asset.balanceIrr) {
        when (displayCurrency) {
            FiatCurrency.USD -> asset.balanceUsdt
            FiatCurrency.TOMAN -> asset.balanceIrr
        }
    }

    // جداسازی مقدار و نماد برای انیمیشن
    val balanceAmount = remember(asset.balance, asset.symbol) {
        asset.balance
    }

    // استفاده از priceChangeText
    val priceChangeText = remember(asset.priceChange24h) {
        formatPriceChange(asset.priceChange24h)
    }


        Row(
            modifier = modifier // ✅ modifier از بیرون اعمال می‌شود
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // بخش آیکون‌ها (اصلی + بج شبکه)
            Box(
                modifier = Modifier
                    .size(WalletScreenConstants.ASSET_ICON_SIZE)
            ) {
                // آیکون اصلی ارز. اولویت با iconUrlِ کانفیگ است، نه drawableِ لوکال: پیش از این
                // شرطْ وارونه بود و برای هر نمادی که در getLocalIconResId شاخه داشت (BTC/ETH/USDT/…)
                // اصلاً سراغ URL نمی‌رفت، پس آیکونی که سرور در باندل می‌فرستاد هیچ‌وقت دیده نمی‌شد.
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
                    AnimatedVisibility(
                        visible = !isBalanceHidden,
                        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it }) +
                                androidx.compose.animation.expandHorizontally(expandFrom = Alignment.Start) +
                                fadeIn(),
                        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it }) +
                                androidx.compose.animation.shrinkHorizontally(shrinkTowards = Alignment.Start) +
                                fadeOut(),
                        label = "AssetBalanceAmountVisibility"
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // نمایش Circle Chart برای گروه‌ها (داخل انیمیشن).
                            // چارت فقط وقتی معنا دارد که موجودی واقعاً بین بیش از یک شبکه پخش شده
                            // باشد؛ با یک سهم، حلقه یک دایرهٔ تک‌رنگِ ۱۰۰٪ است که هیچ اطلاعاتی
                            // نمی‌دهد. تا پیش از TASK-53 این حالت خودبه‌خود رخ نمی‌داد چون فقط
                            // تست‌نت‌ها ثبت می‌شدند و بیشتر ارزها اصلاً گروه نمی‌شدند؛ حالا که همهٔ
                            // زنجیره‌ها ثبت می‌شوند، گروهی با تنها یک شبکهٔ دارای موجودی عادی است.
                            if (asset.isGroupHeader && asset.networkDistribution.size > 1) {
                                NetworkDistributionChart(
                                    distribution = asset.networkDistribution,
                                    size = WalletScreenConstants.ASSET_NETWORK_CHART_SIZE
                                )
                                Spacer(modifier = Modifier.width(WalletScreenConstants.ASSET_NETWORK_CHART_SPACING))
                            }

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
                Crossfade(
                    targetState = isBalanceHidden,
                    animationSpec = tween(300),
                    label = "AssetValueCrossfade"
                ) { hidden ->
                    if (hidden) {
                        Text(
                            text = "*****",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onTertiary,
                                letterSpacing = 2.sp
                            )
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {

                            AnimatedCounter(
                                text = displayBalance.trim(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontFamily = when (displayCurrency) {
                                        FiatCurrency.USD -> InterRegularMedium

                                        FiatCurrency.TOMAN -> IranSansRegularMedium
                                    }
                                ),
                                animationDuration = WalletScreenConstants.ASSET_ANIMATION_DURATION,
                                styleVariantKey = displayCurrency
                            )
                            if (displayCurrency == FiatCurrency.USD) {
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
                            if (displayCurrency == FiatCurrency.TOMAN) {
                                Text(
                                    text = " تومان",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = WalletScreenConstants.ASSET_PRICE_SYMBOL_FONT_SIZE,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontFamily = IranSansRegularMedium
                                    ),
                                    modifier = Modifier.padding(start = WalletScreenConstants.ASSET_PRICE_SYMBOL_PADDING_END)
                                )
                            }

                        }
                    }
                }

                Text(
                    text = priceChangeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (asset.priceChange24h >= 0) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    },
                    fontFamily = IranSansRegularMedium
                )
            }
        }


}

/**
 * Auto-resizing Row که شامل "$"/"تومان" و AnimatedCounter است
 * فونت را به صورت خودکار کوچک می‌کند تا در یک خط بماند
 */
@Composable
private fun AutoResizeBalanceRow(
    totalBalance: String,
    displayCurrency: FiatCurrency,
    animationDuration: Int,
    minTextSize: TextUnit = 16.sp
) {
    var textSize by remember(displayCurrency) {
        mutableStateOf(WalletScreenConstants.TOTAL_BALANCE_FONT_SIZE)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {

        // نمایش واقعی Row با اندازه فونت تنظیم شده
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.wrapContentWidth()
        ) {


            AnimatedCounter(
                text = totalBalance,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = textSize,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = when (displayCurrency) {
                        FiatCurrency.USD -> InterRegularMedium

                        FiatCurrency.TOMAN -> IranSansRegularMedium
                    },
                    letterSpacing = WalletScreenConstants.TOTAL_BALANCE_LETTER_SPACING
                ),
                animationDuration = animationDuration,
                styleVariantKey = displayCurrency,
                modifier = Modifier.wrapContentWidth()
            )
            if (displayCurrency == FiatCurrency.USD) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "$",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = WalletScreenConstants.CURRENCY_SYMBOL_FONT_SIZE,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = InterRegularMedium
                    ),
                    modifier = Modifier.padding(top = WalletScreenConstants.CURRENCY_SYMBOL_PADDING_TOP)
                )

            }
            if (displayCurrency == FiatCurrency.TOMAN) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "تومان",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = WalletScreenConstants.TOMAN_FONT_SIZE,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = IranSansRegularMedium
                    ),
                    modifier = Modifier.padding(top = WalletScreenConstants.TOMAN_PADDING_TOP)
                )
            }
        }
    }
}

/**
 * نمودار دایره‌ای (حلقه‌ای) کوچک برای نمایش توزیع موجودی در شبکه‌های مختلف
 * طراحی شده شبیه به تصویر نمونه (Donut Chart با گوشه‌های گرد و فواصل)
 */
@Composable
private fun NetworkDistributionChart(
    distribution: List<NetworkShare>,
    size: Dp = WalletScreenConstants.ASSET_NETWORK_CHART_SIZE
) {
    Canvas(
        modifier = Modifier.size(size)
    ) {
        // محاسبات داخل Canvas که Density را دارد
        val strokeWidth = WalletScreenConstants.CHART_STROKE_WIDTH.toPx()
        val canvasSize = size.toPx()
        val arcSize = canvasSize - strokeWidth
        val topLeftOffset = Offset(strokeWidth / 2f, strokeWidth / 2f)
        val arcSizeObj = Size(arcSize, arcSize)
        
        // ۱. رسم پس‌زمینه (حلقه تیره رنگ پایه)
        drawArc(
            color = WalletScreenConstants.CHART_BACKGROUND_COLOR,
            startAngle = 0f,
            sweepAngle = WalletScreenConstants.CHART_FULL_CIRCLE,
            useCenter = false,
            topLeft = topLeftOffset,
            size = arcSizeObj,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        // ۲. رسم سهم هر شبکه به صورت سگمنت‌های رنگی
        var currentAngle = WalletScreenConstants.CHART_START_ANGLE
        val gapAngle = WalletScreenConstants.CHART_GAP_ANGLE
        
        distribution.forEach { share ->
            val sweepAngle = (share.percentage / 100f) * WalletScreenConstants.CHART_FULL_CIRCLE
            
            if (sweepAngle > gapAngle) {
                val color = share.colorHex.toColorOrGray()
                
                drawArc(
                    color = color,
                    startAngle = currentAngle + (gapAngle / 2f),
                    sweepAngle = sweepAngle - gapAngle,
                    useCenter = false,
                    topLeft = topLeftOffset,
                    size = arcSizeObj,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            currentAngle += sweepAngle
        }
    }
}

/**
 * Helper functions برای WalletScreen
 */
private fun formatPriceChange(priceChange24h: Double): String {
    return if (priceChange24h >= 0) {
        String.format("+%.3f%%", priceChange24h)
    } else {
        String.format("%.3f%%", priceChange24h)
    }
}

private fun String.toColorOrGray(): Color {
    return try {
        Color(this.toColorInt())
    } catch (e: Exception) {
        Color.Gray
    }
}

@Preview
@Composable
fun PreviewAssetsDetails() {
    MaterialTheme {
        var item = AssetItem(
            id = "BNB-BSC_MAINNET",
            networkId = "bsc_mainnet",
            name = "Binance Coin",
            faName = "بایننس کوین",
            symbol = "BNB",
            networkName = "on BSC",
            networkFaName = "در BSC",
            iconUrl = null,
            balance = "0.006469 BNB",
            balanceUsdt = "$5.85",
            balanceIrr = "250,000 تومان",
            formattedDisplayBalance = "$5.85",
            priceChange24h = 0.0,
            balanceRaw = BigDecimal("0.006469"),
            priceUsdRaw = BigDecimal("904.52"),
            decimals = 18,
            contractAddress = null,
            isNativeToken = true
        )
//        AssetDetailHeader(
//            onBackClick = {},
//            asset = item
//        )        AssetListItems(asset = item, isBalanceHidden = false, onClick = {}, displayCurrency = FiatCurrency.USD)    }
    }
}
