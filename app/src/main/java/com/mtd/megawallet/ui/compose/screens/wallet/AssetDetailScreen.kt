package com.mtd.megawallet.ui.compose.screens.wallet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.imageLoader
import com.mtd.common_ui.R
import com.mtd.core.utils.formatWithSeparator
import com.mtd.megawallet.event.AssetItem
import com.mtd.megawallet.event.HomeUiState
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.components.AnimatedCounter
import com.mtd.megawallet.viewmodel.news.AssetDetailViewModel
import com.mtd.megawallet.viewmodel.news.HomeViewModel
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssetDetailScreen(
    assetId: String,
    onNavigateBack: () -> Unit,
    isExpandedStable: Boolean = false,
    viewModel: AssetDetailViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel? = null
) {
    val asset by viewModel.asset.collectAsState()
    val chartData by viewModel.chartData.collectAsState()
    val isLoadingChart by viewModel.isLoadingChart.collectAsState()
    var isContentVisible by remember { mutableStateOf(false) }

    // State برای تعامل با نمودار (Scrubbing)
    var scrubbedPoint by remember { mutableStateOf<Pair<Long, Double>?>(null) }

    // ✅ ابتدا asset را از HomeViewModel بگیر (اگر موجود باشد)
    LaunchedEffect(assetId, homeViewModel) {
        val assetFromList = homeViewModel?.getAssetById(assetId)
        if (assetFromList != null) {
            // اگر asset در لیست موجود است، از آن استفاده کن
            viewModel.setAsset(assetFromList)
        } else {
            // در غیر این صورت از API لود کن
            viewModel.loadAsset(assetId)
        }
    }


    // کنترل نمایش محتوا با تاخیر ثابت ۱ ثانیه‌ای (جدا از انیمیشن هدر)
    LaunchedEffect(Unit) {
        delay(300)
        isContentVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {  }
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // ۱. هدر با دکمه Back
            item {
                AssetDetailHeader(
                    asset = asset,
                    onBackClick = onNavigateBack,
                    isExpanded = isExpandedStable
                )
            }

            // ۲. محتوا (پس از تثبیت انیمیشن)
            item {
                AnimatedVisibility(
                    visible = isContentVisible && asset != null,
                    enter = fadeIn(animationSpec = tween(150)) +
                            slideInVertically(
                                initialOffsetY = { it / 8 },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ),
                    label = "ContentReveal"
                ) {
                    asset?.let { assetData ->
                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                            Spacer(modifier = Modifier.height(10.dp))

                            // بخش بالایی: قیمت و تغییرات
                            AssetDetailTopSection(
                                asset = assetData, 
                                homeViewModel = homeViewModel,
                                scrubbedPoint = scrubbedPoint
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // نمودار تعاملی (Scrubbing)
                            AssetDetailChartSection(
                                chartData = chartData, 
                                isLoading = isLoadingChart,
                                onScrubbing = { scrubbedPoint = it }
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Timeframe Selector
                            AssetDetailTimeframeSelector(
                                selectedTimeframe = viewModel.selectedTimeFrame.collectAsState().value,
                                onTimeframeSelected = { viewModel.onTimeFrameSelected(it) }
                            )

                            // نمایش برای تمامی ارزها (چه تک شبکه و چه چند شبکه)
                            Spacer(modifier = Modifier.height(20.dp))
                            AssetDetailBreakdownSection(asset = assetData)

                            Spacer(modifier = Modifier.height(20.dp))

                            // بخش پایینی: Balance و Value
                            AssetDetailBottomSection(asset = assetData)


                            // Spacer to ensure content doesn't get hidden under fixed buttons
                            Spacer(modifier = Modifier.height(150.dp))
                        }
                    }
                }
            }
        }

        // --- Fixed Bottom Buttons ---
        AnimatedVisibility(
            visible = isContentVisible && asset != null,
            enter = fadeIn(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp, top = 20.dp)
                        .fillMaxWidth()
                ) {

                    Button(
                        onClick = {},
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = CircleShape
                    ) {
                        Text(
                            "ارسال",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium))
                        )
                    }



                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {},
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        shape = CircleShape
                    ) {
                        Text(
                            "تبدیل",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetDetailHeader(
    asset: AssetItem?,
    onBackClick: () -> Unit,
    isExpanded: Boolean = false
) {
    val imageLoader = LocalContext.current.imageLoader
    val isDark = isSystemInDarkTheme()

    // ✅ استفاده از همان توابع helper برای آیکون
    val localIconResId = remember(asset?.symbol) {
        asset?.symbol?.let { getLocalIconResId(it) } ?: 0
    }
    val localIconNetworkResId = remember(asset?.networkId) {
        asset?.networkId?.let { getNetworkIconResId(it) } ?: 0
    }
    val placeholderResId = remember { getPlaceholderIconResId() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .statusBarsPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // آیکون و نام در سمت راست
        if (asset != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {


                val iconSize by animateDpAsState(
                    targetValue = if (isExpanded) WalletScreenConstants.ASSET_ICON_MAIN_SIZE_LARGE else WalletScreenConstants.ASSET_ICON_MAIN_SIZE,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "IconSizeAnimation"
                )
                val boxSize by animateDpAsState(
                    targetValue = if (isExpanded) 60.dp else WalletScreenConstants.ASSET_ICON_SIZE,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "BoxSizeAnimation"
                )

                Box(
                    modifier = Modifier.size(boxSize)
                ) {
                    // آیکون اصلی ارز
                    if (localIconResId != 0) {
                        Box(
                            modifier = Modifier.size(iconSize),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = localIconResId),
                                contentDescription = "${asset.name} icon",
                                modifier = Modifier.size(iconSize),
                                contentScale = ContentScale.Fit,
                                colorFilter = null
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.size(iconSize),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = asset.iconUrl,
                                contentDescription = "${asset.name} icon",
                                modifier = Modifier.size(iconSize),
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


                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.Start) {

                    Text(
                        text = asset.faName ?: asset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Bold)),
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 20.sp
                    )

                    Text(
                        text = asset.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        fontFamily = FontFamily(Font(R.font.inter_regular, FontWeight.Bold)),
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontSize = 18.sp
                    )
                }


            }
        }

        // دکمه Back در سمت چپ
        IconButton(onClick = {
            onBackClick.invoke()
        }) {
            Icon(imageVector = Icons.Default.ArrowBackIosNew, tint = MaterialTheme.colorScheme.tertiary, contentDescription = "Back")
        }
    }
}

@Composable
private fun AssetDetailTopSection(
    asset: AssetItem,
    homeViewModel: HomeViewModel? = null,
    scrubbedPoint: Pair<Long, Double>? = null
) {
    val priceChangeText = remember(asset.priceChange24h) {
        formatPriceChange(asset.priceChange24h)
    }

    val isPositive = asset.priceChange24h >= 0
    val changeColor = if (isPositive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
    }

    // دریافت نرخ تتر به تومان از HomeViewModel
    var irrRate by remember { mutableStateOf(BigDecimal("0")) } // مقدار پیش‌فرض

    // دریافت نرخ از HomeViewModel
    LaunchedEffect(homeViewModel) {
        homeViewModel?.let {
            irrRate = it.getUsdToIrrRate()
        }
    }

    // فرمت کردن قیمت نمایش داده شده (اگر scrubbing فعال باشد، قیمت لحظه‌ای چارت، در غیر این صورت قیمت فعلی ارز)
    val currentPriceUsd = remember(asset.priceUsdRaw, scrubbedPoint) {
        scrubbedPoint?.second?.let { BigDecimal.valueOf(it) } ?: asset.priceUsdRaw
    }

    val priceIrr = remember(currentPriceUsd, irrRate) {
        (currentPriceUsd * irrRate)
            .setScale(0, RoundingMode.HALF_UP)
            .formatWithSeparator(usePersianSeparator = false, minFractionDigits = 0, maxFractionDigits = 0)
    }

    val priceUsdFormatted = remember(currentPriceUsd) {
        currentPriceUsd
            .setScale(2, RoundingMode.HALF_UP)
            .formatWithSeparator(usePersianSeparator = false, minFractionDigits = 2, maxFractionDigits = 2)
    }

    val formattedDate = remember(scrubbedPoint) {
        scrubbedPoint?.first?.let { timestamp ->
            val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp * 1000))
        }
    }

    // Row با SpaceBetween: درصد تغییرات در سمت چپ، قیمت در سمت راست
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // ✅ درصد تغییرات یا تاریخ در هنگام Scrubbing (سمت چپ)
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                AnimatedContent(
                    targetState = formattedDate,
                    transitionSpec = {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    },
                    label = "LabelScale"
                ) { date ->
                    if (date != null) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = changeColor
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = priceChangeText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = changeColor,
                                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Medium))
                            )
                        }
                    }
                }
            }

            // ✅ قیمت‌ها در سمت راست
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // قیمت دلار با Label
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AutoResizeBalanceRows(
                        totalBalance = priceUsdFormatted,
                        displayCurrency = HomeUiState.DisplayCurrency.USDT,
                        animationDuration = 200,
                        textSize = 25.sp,
                        USDTFontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // قیمت تومان با Label
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AutoResizeBalanceRows(
                        totalBalance = priceIrr,
                        displayCurrency = HomeUiState.DisplayCurrency.IRR,
                        animationDuration = 200,
                        textSize = 25.sp,
                        TMNFontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetDetailChartSection(
    chartData: List<Pair<Long, Double>>,
    isLoading: Boolean,
    onScrubbing: (Pair<Long, Double>?) -> Unit
) {
    var touchX by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .height(200.dp)
            .pointerInput(chartData) {
                detectDragGestures(
                    onDragStart = { offset ->
                        touchX = offset.x
                    },
                    onDrag = { change, _ ->
                        touchX = change.position.x
                    },
                    onDragEnd = {
                        touchX = null
                        onScrubbing(null)
                    },
                    onDragCancel = {
                        touchX = null
                        onScrubbing(null)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            androidx.compose.material3.CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        } else if (chartData.isNotEmpty()) {
            val maxPrice = chartData.maxBy { it.second }.second
            val minPrice = chartData.minBy { it.second }.second
            val priceRange = (maxPrice - minPrice).coerceAtLeast(0.0001)

            val isPositive = chartData.last().second >= chartData.first().second
            val chartColor = if (isPositive) Color(0xFF22C55E) else Color(0xFFEF4444)

            val primaryColor = MaterialTheme.colorScheme.primary
            val tertiaryColor = MaterialTheme.colorScheme.tertiary

            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val path = Path()

                var scrubbedIndex = -1
                touchX?.let { xPos ->
                    val normalizedX = (xPos / width).coerceIn(0f, 1f)
                    scrubbedIndex = (normalizedX * (chartData.size - 1)).toInt().coerceIn(0, chartData.size - 1)
                }

                chartData.forEachIndexed { index, pair ->
                    val x = index * (width / (chartData.size - 1))
                    val y = height - ((pair.second - minPrice) / priceRange * height).toFloat()

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                // Draw line
                drawPath(
                    path = path,
                    color = chartColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw fill gradient
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(chartColor.copy(alpha = 0.2f), Color.Transparent)
                    ),
                    style = Fill
                )

                // Draw Scrubbing Indicator
                if (scrubbedIndex != -1) {
                    val selectedPoint = chartData[scrubbedIndex]
                    onScrubbing(selectedPoint)

                    val x = scrubbedIndex * (width / (chartData.size - 1))
                    val y = height - ((selectedPoint.second - minPrice) / priceRange * height).toFloat()

                    // Vertical Line
                    drawLine(
                        color = primaryColor.copy(alpha = 0.5f),
                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, height),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Point Circle
                    drawCircle(
                        color = tertiaryColor,
                        radius = 6.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )

                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                }
            }
        } else {
            Text(
                "خطا در دریافت اطلاعات نمودار",
                color = MaterialTheme.colorScheme.onTertiary,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AssetDetailTimeframeSelector(
    selectedTimeframe: String,
    onTimeframeSelected: (String) -> Unit
) {
    val options = listOf(
        "1" to "1 روز",
        "7" to "1 هفته",
        "30" to "1 ماه",
        "90" to "3 ماه",
        "365" to "1 سال"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        options.forEach { (value, label) ->
            val isSelected = selectedTimeframe == value
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTimeframeSelected(value) }
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            Color.Transparent
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onTertiary
                    },
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium))
                )
            }
        }
    }
}

@Composable
private fun AssetDetailBottomSection(asset: AssetItem) {
    Column(modifier = Modifier.fillMaxWidth()) {

        // ۲. کارت‌های ارزش دارایی (USDT و تومان)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth().height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {


                // کارت ارزش دلاری (تتری)
                Surface(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "ارزش دارایی (تتری)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AutoResizeBalanceRows(
                            totalBalance = asset.balanceUsdt.replace("$", "").replace("USDT", "")
                                .trim(),
                            displayCurrency = HomeUiState.DisplayCurrency.USDT,
                            animationDuration = 200,
                            USDTFontSize = 12.sp,
                            textSize = 22.sp,
                            alignment = Alignment.Center
                        )
                    }
                }

                // کارت ارزش تومانی
                Surface(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "ارزش دارایی (تومان)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AutoResizeBalanceRows(
                            totalBalance = asset.balanceIrr.replace("تومان", "").trim(),
                            displayCurrency = HomeUiState.DisplayCurrency.IRR,
                            animationDuration = 200,
                            TMNFontSize = 10.sp,
                            textSize = 22.sp,
                            alignment = Alignment.Center
                        )
                    }
                }


            }
        }
    }
}

@Composable
private fun AssetDetailBreakdownSection(asset: AssetItem) {
    var isExpanded by remember { mutableStateOf(false) }
    // فیلتر کردن شبکه‌هایی که فقط موجودی دارند
    // اگر هیچ کدام موجودی نداشته باشند، صرفاً اولین شبکه (پیش‌فرض) را نشان می‌دهیم
    val listToDisplay = remember(asset) {
        if (asset.groupAssets.isEmpty()) {
            listOf(asset)
        } else {
            val withBalance = asset.groupAssets.filter { it.balanceRaw > BigDecimal.ZERO }
            withBalance.ifEmpty { listOf(asset.groupAssets.first()) }
        }
    }


        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            // Header Row (موجودی در سمت راست، آیکون‌ها در سمت چپ)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "موجودی کل",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = asset.balance,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = FontFamily(Font(R.font.inter_regular, FontWeight.Bold))
                    )
                }

                // آیکون شبکه‌ها (سمت چپ در RTL)
                val networkIds = listToDisplay.map { it.networkId }
                val iconUrls = listToDisplay.map { it.iconUrl ?: "" }
                SupportedNetworksRow(networkIds = networkIds, iconUrls = iconUrls, MaterialTheme.colorScheme.surface)
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    listToDisplay.forEach { subAsset ->
                        val share = asset.networkDistribution.find { it.networkId == subAsset.networkId }

                        val percentageValue = if (asset.groupAssets.isEmpty()) 100f else (share?.percentage ?: 0f)
                        
                        BreakdownListItem(subAsset = subAsset, percentage = percentageValue)
                        
                        if (subAsset != listToDisplay.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Toggle Button at bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpanded) "عدم نمایش جزئیات" else "مشاهده شبکه‌ها",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onTertiary
                )
            }
        }
    }



@Composable
private fun BreakdownListItem(subAsset: AssetItem, percentage: Float) {
    val localNetworkIcon = getNetworkIconResId(subAsset.networkId)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = localNetworkIcon),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = subAsset.networkFaName ?: subAsset.networkName.removePrefix("on ").replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.tertiary,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium))
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${percentage.toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiary,
                fontFamily = FontFamily(Font(R.font.inter_regular))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = subAsset.balance,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.tertiary,
                fontFamily = FontFamily(Font(R.font.inter_regular))
            )
        }
    }
}


@Composable
private fun AutoResizeBalanceRows(
    totalBalance: String,
    displayCurrency: HomeUiState.DisplayCurrency,
    animationDuration: Int,
    TMNFontSize: TextUnit= WalletScreenConstants.TOMAN_FONT_SIZE,
    USDTFontSize: TextUnit= WalletScreenConstants.CURRENCY_SYMBOL_FONT_SIZE,
    textSize:TextUnit=WalletScreenConstants.TOTAL_BALANCE_FONT_SIZE_DETAIL,
    alignment: Alignment = Alignment.CenterEnd
) {
    var textSize by remember(textSize) {
        mutableStateOf(textSize)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {

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
                            fontSize = USDTFontSize,
                            lineHeight = USDTFontSize, // Fix vertical alignment
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontFamily = FontFamily(
                                Font(
                                    R.font.inter_regular,
                                    FontWeight.Medium
                                )
                            )
                        ),
                        modifier = Modifier.padding(top =WalletScreenConstants.ASSET_PRICE_SYMBOL_PADDING_END)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    AnimatedCounter(
                        text = totalBalance,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = textSize,
                            lineHeight = textSize,
                            color = MaterialTheme.colorScheme.tertiary,
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
                            fontSize = TMNFontSize,
                            fontWeight = FontWeight.Bold,
                            lineHeight = TMNFontSize, // Fix vertical alignment
                            color = MaterialTheme.colorScheme.tertiary,
                            fontFamily = FontFamily(
                                Font(
                                    R.font.iransansmobile_fa_regular,
                                    FontWeight.Medium
                                )
                            )
                        ),
                        modifier = Modifier.padding(
                            top = WalletScreenConstants.ASSET_PRICE_SYMBOL_PADDING_END
                        )
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    AnimatedCounter(
                        text = totalBalance,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = textSize,
                            lineHeight = textSize,
                            color = MaterialTheme.colorScheme.tertiary,
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

// ✅ استفاده از همان توابع helper از WalletScreen
private fun formatPriceChange(priceChange24h: Double): String {
    return if (priceChange24h >= 0) {
        String.format("+%.2f%%", priceChange24h)
    } else {
        String.format("%.2f%%", priceChange24h)
    }
}


@Preview
@Composable
fun PreviewAssetsDetail() {
    MaterialTheme(lightColorScheme()) {

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
            balanceUsdt = "5.85",
            balanceIrr = "250,000",
            formattedDisplayBalance = "5.85",
            priceChange24h = 0.0,
            balanceRaw = BigDecimal("0.006469"),
            priceUsdRaw = BigDecimal("904.52"),
            decimals = 18,
            contractAddress = null,
            isNativeToken = true
        )
        /*AssetDetailHeader(
            onBackClick = {},
            asset = item
        )*/

        AssetDetailTopSection(item, homeViewModel = null)
      //  AssetDetailBottomSection(item)
    }
}
