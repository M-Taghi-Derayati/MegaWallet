package com.mtd.megawallet.ui.compose.screens.wallet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil.compose.AsyncImage
import com.mtd.common_ui.R
import com.mtd.megawallet.event.AssetItem
import com.mtd.megawallet.event.HomeUiState
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.components.AnimatedCounter
import com.mtd.megawallet.viewmodel.news.HomeViewModel




/**
 * صفحه کیف پول که شامل:
 * - موجودی تجمیعی
 * - لیست دارایی‌ها
 */
@Composable
fun WalletScreen(
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTabIndex by viewModel.selectedTab.collectAsState()
    val tabs = listOf("توکن‌ها", "کلکسیون‌ها")

    // وضعیت مخفی بودن موجودی (State Hoisting)
    var isBalanceHidden by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                ShimmerWalletScreen()
            }

            is HomeUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is HomeUiState.Success -> {
                // وضعیت نمایان بودن برای انیمیشن ورود
                var isVisible by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    isVisible = true
                }

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(animationSpec = tween(500)) +
                            slideInVertically(animationSpec = tween(500)){100},
                    // انیمیشن خروج نیاز نیست چون معمولاً به Loading میرویم که خودش Shimmer است
                ) {
                    // Pull to Refresh State
                    var isRefreshing by remember { mutableStateOf(false) }

                    LaunchedEffect(state.isUpdating) {
                         if (!state.isUpdating) {
                             isRefreshing = false
                         }
                    }

                    @OptIn(ExperimentalMaterial3Api::class)
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            viewModel.refreshData()
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // موجودی تجمیعی (بخش بالایی)
                            item {
                                TotalBalanceSection(
                                    totalBalance = when (state.displayCurrency) {
                                        HomeUiState.DisplayCurrency.USDT -> state.totalBalanceUsdt
                                        HomeUiState.DisplayCurrency.IRR -> state.totalBalanceIrr
                                    },
                                    displayCurrency = state.displayCurrency,
                                    isUpdating = state.isUpdating,
                                    isBalanceHidden = isBalanceHidden,
                                    onToggleHidden = { isBalanceHidden = !isBalanceHidden }
                                )
                            }

                            // تب‌ها و قیمت تتر
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // قیمت تتر (سمت چپ)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 0.dp)
                                    ) {
                                        Text(
                                            text = "تتر: ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_light))
                                        )
                                        Text(
                                            text = "${state.tetherPriceIrr} تومان",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
                                        )
                                    }

                                    // تب‌ها (سمت راست - با جهت راست به چپ)
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
                                                    onClick = { viewModel.onTabSelected(index) },
                                                    text = {
                                                        Text(
                                                            text = title,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // خط خاکستری زیر تب‌ها (سرتاسری)
                                HorizontalDivider(
                                    modifier = Modifier.padding(top = WalletScreenConstants.DIVIDER_SPACING_TOP),
                                    thickness = WalletScreenConstants.DIVIDER_THICKNESS,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = WalletScreenConstants.DIVIDER_ALPHA)
                                )

                                Spacer(modifier = Modifier.height(WalletScreenConstants.TABS_SPACING_BOTTOM))
                            }

                            // لیست دارایی‌ها (فقط برای تب Tokens)
                            if (selectedTabIndex == 0) {
                                items(
                                    items = state.assets,
                                    key = { it.id }
                                ) { asset ->
                                    Box(modifier = Modifier.animateItem()) {
                                        AssetListItem(
                                            asset = asset,
                                            displayCurrency = state.displayCurrency,
                                            isBalanceHidden = isBalanceHidden
                                        )
                                    }
                                }
                            } else {
                                // تب Collectibles (فعلاً خالی)
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

                            // فاصله انتهایی برای اسکرول بهتر
                            item { Spacer(modifier = Modifier.height(WalletScreenConstants.ASSET_LIST_BOTTOM_SPACING)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * بخش نمایش موجودی تجمیعی
 */
@Composable
private fun TotalBalanceSection(
    totalBalance: String,
    displayCurrency: HomeUiState.DisplayCurrency,
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
                                color = MaterialTheme.colorScheme.onBackground,
                                fontFamily = FontFamily(Font(R.font.inter_regular, FontWeight.Medium)),
                                letterSpacing = WalletScreenConstants.TOTAL_BALANCE_HIDDEN_LETTER_SPACING
                            ),
                            modifier = Modifier.fillMaxWidth()
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
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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


/**
 * آیتم لیست دارایی
 */
@Composable
private fun AssetListItem(
    asset: AssetItem,
    displayCurrency: HomeUiState.DisplayCurrency,
    isBalanceHidden: Boolean
) {
    val isDark = isSystemInDarkTheme()
    
    // انتخاب مقدار نمایشی بر اساس displayCurrency
    val displayBalance = remember(displayCurrency, asset.balanceUsdt, asset.balanceIrr) {
        when (displayCurrency) {
            HomeUiState.DisplayCurrency.USDT -> asset.balanceUsdt
            HomeUiState.DisplayCurrency.IRR -> asset.balanceIrr
        }
    }

    // تلاش برای پیدا کردن آیکون لوکال با فرمت ic_symbol (مثلا ic_btc)
    val localIconResId = remember(asset.symbol) {
        getLocalIconResId(asset.symbol)
    }
    val localIconNetworkResId = remember(asset.networkId) {
        getNetworkIconResId(asset.networkId)
    }

    // جداسازی مقدار و نماد برای انیمیشن
    val balanceAmount = remember(asset.balance, asset.symbol) {
        asset.balance.replace(asset.symbol, "", ignoreCase = true).trim()
    }
    
    // استفاده از remember برای ImageLoader
    val context = LocalContext.current
    val imageLoader = remember { coil.ImageLoader(context) }
    
    // استفاده از remember برای priceChangeText
    val priceChangeText = remember(asset.priceChange24h) {
        formatPriceChange(asset.priceChange24h)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = WalletScreenConstants.ASSET_ITEM_PADDING_HORIZONTAL,
                vertical = WalletScreenConstants.ASSET_ITEM_PADDING_VERTICAL
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // بخش آیکون‌ها (اصلی + بج شبکه)
        Box(
            modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_SIZE)
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
            } else {
                val placeholderResId = remember { getPlaceholderIconResId() }
                Box(
                    modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = asset.iconUrl,
                        contentDescription = "${asset.name} icon",
                        imageLoader = imageLoader,
                        modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                        contentScale = ContentScale.Fit,
                        placeholder = painterResource(id = placeholderResId),
                        error = painterResource(id = placeholderResId),
                        fallback = painterResource(id = placeholderResId)
                    )
                }
            }
            
            // بج شبکه (پایین سمت راست)
            if (asset.networkName.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(WalletScreenConstants.ASSET_ICON_NETWORK_SIZE)
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
                text = asset.faName?:asset.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily =FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Bold)) ,
                color = MaterialTheme.colorScheme.onSurface
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
                        // نمایش Circle Chart برای گروه‌ها (داخل انیمیشن)
                        if (asset.isGroupHeader && asset.networkDistribution.isNotEmpty()) {
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Bold))
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily(Font(R.font.inter_bold, FontWeight.Bold))
                    )
                )
            }
        }
        
        // بخش قیمت و درصد تغییرات (راست)
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
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 2.sp
                        )
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (displayCurrency == HomeUiState.DisplayCurrency.USDT) {
                            Text(
                                text = "$",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = WalletScreenConstants.ASSET_PRICE_SYMBOL_FONT_SIZE,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily(Font(R.font.inter_regular, FontWeight.Medium))
                                ),
                                modifier = Modifier.padding(end = WalletScreenConstants.ASSET_PRICE_SYMBOL_PADDING_END)
                            )
                        }
                        if (displayCurrency == HomeUiState.DisplayCurrency.IRR) {
                            Text(
                                text = " تومان",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = WalletScreenConstants.ASSET_PRICE_SYMBOL_FONT_SIZE,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily(Font(R.font.vazirmatn_regular, FontWeight.Medium))
                                ),
                                modifier = Modifier.padding(end = WalletScreenConstants.ASSET_PRICE_SYMBOL_PADDING_END)
                            )
                        }
                        AnimatedCounter(
                            text = displayBalance.trim(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = when (displayCurrency) {
                                    HomeUiState.DisplayCurrency.USDT -> FontFamily(Font(R.font.inter_regular, FontWeight.Medium))
                                    HomeUiState.DisplayCurrency.IRR -> FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium))
                                }
                            ),
                                        animationDuration = WalletScreenConstants.ASSET_ANIMATION_DURATION
                        )


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
                fontFamily = FontFamily(Font(R.font.vazirmatn_regular, FontWeight.Medium))
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
    displayCurrency: HomeUiState.DisplayCurrency,
    animationDuration: Int,
    minTextSize: TextUnit = 16.sp
) {
    var textSize by remember(displayCurrency) {
        mutableStateOf(WalletScreenConstants.TOTAL_BALANCE_FONT_SIZE)
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val maxWidth = constraints.maxWidth.toFloat()
        val currencyText = when (displayCurrency) {
            HomeUiState.DisplayCurrency.USDT -> "$"
            HomeUiState.DisplayCurrency.IRR -> "تومان"
        }

        // اندازه‌گیری با Text مخفی برای محاسبه اندازه فونت
        // انتخاب فونت متناسب با ارز برای اندازه‌گیری دقیق
        val measurementFont = if (displayCurrency == HomeUiState.DisplayCurrency.IRR) {
            FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium))
        } else {
            FontFamily(Font(R.font.inter_regular, FontWeight.Medium))
        }

        Text(
            text = currencyText + totalBalance,
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = textSize,
                fontFamily = measurementFont,
                letterSpacing = WalletScreenConstants.TOTAL_BALANCE_LETTER_SPACING
            ),
            maxLines = 1,
            softWrap = false,
            onTextLayout = { textLayoutResult ->
                val textWidth = textLayoutResult.size.width
                if (textWidth > maxWidth && textSize.value > minTextSize.value) {
                    // اگر متن بزرگتر از فضا بود، فونت را کوچک می‌کنیم
                    val newSizeValue = (textSize.value * 0.95f).coerceAtLeast(minTextSize.value)
                    textSize = newSizeValue.sp
                }
            },
            modifier = Modifier.alpha(0f)
        )

        // نمایش واقعی Row با اندازه فونت تنظیم شده
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.wrapContentWidth()
        ) {
            when (displayCurrency) {
                // حالت دلار: علامت $ در سمت چپ و بالا
                HomeUiState.DisplayCurrency.USDT -> {
                    Text(
                        text = "$",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = WalletScreenConstants.CURRENCY_SYMBOL_FONT_SIZE,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontFamily = FontFamily(
                                Font(
                                    R.font.inter_regular,
                                    FontWeight.Medium
                                )
                            )
                        ),
                        modifier = Modifier.padding(
                            top = WalletScreenConstants.CURRENCY_SYMBOL_PADDING_TOP
                        )
                    )
                    Spacer(modifier = Modifier.width(3.dp))

                    AnimatedCounter(
                        text = totalBalance,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = textSize,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontFamily = FontFamily(Font(R.font.inter_regular, FontWeight.Medium)),
                            letterSpacing = WalletScreenConstants.TOTAL_BALANCE_LETTER_SPACING
                        ),
                        animationDuration = animationDuration,
                        modifier = Modifier.wrapContentWidth()
                    )
                }

                // حالت تومان: "تومان" در سمت راست و بالا
                HomeUiState.DisplayCurrency.IRR -> {
                    Text(
                        text = "تومان",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = WalletScreenConstants.TOMAN_FONT_SIZE,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily(
                                Font(
                                    R.font.vazirmatn_regular,
                                    FontWeight.Medium
                                )
                            )
                        ),
                        modifier = Modifier.padding(
                            top = WalletScreenConstants.TOMAN_PADDING_TOP
                        )
                    )
                    Spacer(modifier = Modifier.width(3.dp))

                    AnimatedCounter(
                        text = totalBalance,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = textSize,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium)),
                            letterSpacing = WalletScreenConstants.TOTAL_BALANCE_LETTER_SPACING
                        ),
                        animationDuration = animationDuration,
                        modifier = Modifier.wrapContentWidth()
                    )
                }
            }
        }
    }
}

/**
 * تبدیل symbol ارز به resource ID آیکون لوکال
 */
private fun getLocalIconResId(symbol: String): Int {
    return when (symbol.uppercase()) {
        "BTC" -> R.drawable.ic_btc
        "ETH" -> R.drawable.ic_eth
        "POL" -> R.drawable.ic_pol
        "USDT" -> R.drawable.ic_usdt
        "BNB" -> R.drawable.ic_bnb
        "USDC" -> R.drawable.ic_usdc
        // می‌توانید آیکون‌های دیگر را هم اضافه کنید
        else -> 0 // اگر آیکون پیدا نشد، 0 برمی‌گرداند
    }
}

/**
 * تبدیل ID شبکه به resource ID آیکون لوکال شبکه
 */
private fun getNetworkIconResId(networkId: String): Int {
    return when (networkId.lowercase()) {
        "bitcoin_mainnet", "bitcoin_testnet" -> R.drawable.ic_btc
        "sepolia", "ethereum_mainnet" -> R.drawable.ic_eth
        "bsc_testnet", "bsc_mainnet" -> R.drawable.ic_bnb
        "polygon_testnet", "polygon_mainnet" -> R.drawable.ic_pol
        else -> R.drawable.ic_wallet // پیش‌فرض
    }
}

/**
 * نمودار دایره‌ای (حلقه‌ای) کوچک برای نمایش توزیع موجودی در شبکه‌های مختلف
 * طراحی شده شبیه به تصویر نمونه (Donut Chart با گوشه‌های گرد و فواصل)
 */
@Composable
private fun NetworkDistributionChart(
    distribution: List<com.mtd.megawallet.event.NetworkShare>,
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

/**
 * دریافت resource ID برای placeholder آیکون
 * اگر ic_placeholder وجود نداشت، از ic_wallet به عنوان fallback استفاده می‌شود
 */
private fun getPlaceholderIconResId(): Int {
    return R.drawable.ic_wallet
}

