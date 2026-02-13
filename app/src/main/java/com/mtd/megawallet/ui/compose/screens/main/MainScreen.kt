package com.mtd.megawallet.ui.compose.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.mtd.common_ui.R
import com.mtd.megawallet.event.ImportData
import com.mtd.megawallet.ui.compose.animations.constants.MainScreenConstants
import com.mtd.megawallet.ui.compose.screens.addexistingwallet.AddExistingWalletScreen
import com.mtd.megawallet.ui.compose.screens.createwallet.CreateWalletScreen
import com.mtd.megawallet.ui.compose.screens.wallet.AssetDetailScreen
import com.mtd.megawallet.ui.compose.screens.wallet.MultiWalletScreen
import com.mtd.megawallet.ui.compose.screens.wallet.ReceiveScreen
import com.mtd.megawallet.ui.compose.screens.wallet.WalletScreens
import com.mtd.megawallet.viewmodel.news.CreateWalletViewModel
import com.mtd.megawallet.viewmodel.news.HomeViewModel
import com.mtd.megawallet.viewmodel.news.MainScreenViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * صفحه اصلی اپلیکیشن که شامل:
 * - Header (با نام کیف، رنگ، آیکون‌ها)
 * - محتوای داخلی (WalletScreen)
 * - Navigation Bottom Sheet
 * - FAB
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(
    mainViewModel: MainScreenViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    onNavigateToWalletManagement: () -> Unit = {},
    onScanClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onMoreOptionsClick: () -> Unit = {},
    onFabClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onExploreClick: () -> Unit = {}
) {
    val activeWallet by homeViewModel.activeWallet.collectAsState()

    val selectedAssetId by mainViewModel.selectedAssetId.collectAsState()


    // مدیریت دکمه Back: اگر در صفحه جزئیات هستیم، به لیست برگرد
    BackHandler(enabled = selectedAssetId != null) {
        mainViewModel.onNavigateBack()
    }

    MainDashboardContent(
        walletName = activeWallet?.name ?: "کیف پول",
        walletColor = activeWallet?.color?.let { Color(it) }
            ?: MainScreenConstants.DEFAULT_WALLET_COLOR,
        onNavigateToWalletManagement = onNavigateToWalletManagement,
        onScanClick = onScanClick,
        onSearchClick = onSearchClick,
        onMoreOptionsClick = onMoreOptionsClick,
        onHistoryClick = onHistoryClick,
        onExploreClick = onExploreClick,
        onCurrencyToggle = { homeViewModel.toggleDisplayCurrency() },
        mainViewModel = mainViewModel,
        homeViewModel = homeViewModel,
        selectedAssetId = selectedAssetId
    )
}

/**
 * کل محتوای اصلی داشبورد (Scaffold + WalletScreen)
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MainDashboardContent(
    mainViewModel: MainScreenViewModel,
    homeViewModel: HomeViewModel,
    walletName: String,
    walletColor: Color,
    onNavigateToWalletManagement: () -> Unit,
    onScanClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onExploreClick: () -> Unit,
    onCurrencyToggle: () -> Unit,
    selectedAssetId: String?,
) {
    val createWalletViewModel: CreateWalletViewModel = hiltViewModel()
    val density = LocalDensity.current
    var fullScreenRect by remember { mutableStateOf(Rect.Zero) }
    var fullHeightPx by remember { mutableStateOf(0) } // ذخیره ارتفاع کل صفحه

    // ✅✅✅ Rect.VectorConverter برای انیمیشن هماهنگ Rect ✅✅✅
    val rectConverter = remember {
        TwoWayConverter<Rect, AnimationVector4D>(
            convertToVector = { rect ->
                AnimationVector4D(
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom
                )
            },
            convertFromVector = { vector -> Rect(vector.v1, vector.v2, vector.v3, vector.v4) }
        )
    }

    // ✅✅✅ Animatable<Rect> واحد برای انیمیشن هماهنگ ✅✅✅
    val animatedBounds = remember { Animatable(Rect.Zero, rectConverter) }

    // Corner radius animation
    val cornerRadius = remember { Animatable(16f) }

    // Animation stability flag
    var isAnimationStable by remember { mutableStateOf(false) }

    // انیمیشن شفافیت لایه‌ی زیرین (Dimming background)
    val backgroundProgress by animateFloatAsState(
        targetValue = if (selectedAssetId != null) 1f else 0f,
        animationSpec = tween(500),
        label = "BackgroundDim"
    )

    var isHeaderExpanded by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }
    var showReceiveScreen by remember { mutableStateOf(false) }
    var showMultiWalletScreen by remember { mutableStateOf(false) }
    
    // حالات مدیریت ساخت و وارد کردن کیف پول
    var showCreateWalletScreen by remember { mutableStateOf(false) }
    var showImportWalletScreen by remember { mutableStateOf(false) }
    var pendingImportData by remember { mutableStateOf<ImportData?>(null) }
    
    // فلگ برای تشخیص اینکه آیا از MultiWallet وارد فلوی ساخت/ایمپورت شده‌ایم
    var isFromMultiWallet by remember { mutableStateOf(false) }

    // مدیریت دکمه Back برای بستن لایه‌های مختلف
    BackHandler(enabled = showMultiWalletScreen || showReceiveScreen || showCreateWalletScreen || showImportWalletScreen || selectedAssetId != null) {
        when {
            showCreateWalletScreen -> {
                // اگر دیتای ایمپورت داشتیم و کنسل کردیم، به صفحه ایمپورت برگرد
                if (pendingImportData != null) {
                    pendingImportData = null
                    showImportWalletScreen = true
                }
                showCreateWalletScreen = false
            }
            showImportWalletScreen -> showImportWalletScreen = false
            showMultiWalletScreen -> showMultiWalletScreen = false
            showReceiveScreen -> showReceiveScreen = false
            selectedAssetId != null -> mainViewModel.onNavigateBack()
        }
    }

    // ✅✅✅ LaunchedEffect برای مدیریت انیمیشن morphing ✅✅✅
    LaunchedEffect(selectedAssetId, fullScreenRect) {
        val isExpanding = selectedAssetId != null
        val isNewAsset = selectedAssetId != mainViewModel.lastSelectedId

        // مدیریت انیمیشن هدر: فقط زمانی که ارز جدید انتخاب شده یا صفحه بسته می‌شود
        if (isNewAsset) {
            if (isExpanding) {
                // هنگام باز شدن: ابتدا کوچک باشد، سپس با کمی تاخیر بزرگ شود تا انیمیشن دیده شود
                isHeaderExpanded = false
                launch {
                    delay(50)
                    isHeaderExpanded = true
                }
            } else {
                // هنگام بستن: بلافاصله کوچک شود
                isHeaderExpanded = false
            }
        }

        val startBounds = if (isExpanding) {
            // برای باز شدن: از bounds ذخیره شده در ViewModel شروع کن
            mainViewModel.assetBounds[selectedAssetId] ?: Rect.Zero
        } else {
            // برای بسته شدن: از موقعیت فعلی (تمام صفحه) شروع کن
            val current = animatedBounds.value
            if (current.width > 0f && current.height > 0f) {
                current
            } else {
                fullScreenRect
            }
        }

        val targetBounds = if (isExpanding) {
            // به سمت تمام صفحه برو
            fullScreenRect
        } else {
            // به سمت bounds آخرین آیتم انتخاب شده برگرد
            mainViewModel.lastSelectedId?.let {
                mainViewModel.assetBounds[it]
            } ?: Rect.Zero
        }

        val targetCornerRadius = if (isExpanding) {
            0f // Expanded: no corner radius
        } else {
            16f // Collapsed: rounded corners
        }



        // انیمیشن را اجرا کن
        isAnimationStable = false

        // ابتدا به نقطه شروع snap کن
        animatedBounds.snapTo(startBounds)

        // اجرا انیمیشن‌ها به صورت موازی
        val boundsJob = launch {
            animatedBounds.animateTo(
                targetValue = targetBounds,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        launch {
            cornerRadius.animateTo(
                targetValue = targetCornerRadius,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        // صبر برای اتمام انیمیشن اصلی
        boundsJob.join()

        isAnimationStable = true


    }


    // ✅ Box ریشه‌ای
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { fullScreenRect = it.boundsInWindow() }
            .onSizeChanged { fullHeightPx = it.height } // اندازه‌گیری ارتفاع فیزیکی
    ) {
        // --- لایه ۱: داشبورد (Scaffold) ---
        Box(
            modifier = Modifier.graphicsLayer {
                val progress = backgroundProgress
                scaleX = 1f - (progress * 0.05f)
                scaleY = 1f - (progress * 0.05f)
                alpha = 1f - (progress * 0.6f)
            }
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    MainHeader(
                        walletName = walletName,
                        walletColor = walletColor,
                        onWalletClick = { showMultiWalletScreen = true },
                        onScanClick = onScanClick,
                        onSearchClick = onSearchClick,
                        onMoreOptionsClick = onMoreOptionsClick,
                        onCurrencyToggle = onCurrencyToggle
                    )
                },
                bottomBar = {
                    MainBottomNavigation(
                        selectedTab = MainTab.WALLET,
                        onWalletClick = { /* Already on wallet tab */ },
                        onHistoryClick = onHistoryClick,
                        onExploreClick = onExploreClick
                    )
                },
                floatingActionButton = {
                    MorphingFabMenu(
                        isExpanded = isFabExpanded,
                        onToggle = { isFabExpanded = !isFabExpanded },
                        onReceiveClick = { 
                            showReceiveScreen = true
                            isFabExpanded = false
                        }
                    )
                },
                contentWindowInsets = WindowInsets.statusBars
            ) { innerPadding ->
                // نمایش محتویات اصلی لیست
                MainScreenContent(
                    padding = innerPadding,
                    mainViewModel = mainViewModel,
                    homeViewModel = homeViewModel,
                    selectedAssetId = selectedAssetId,
                    isTransitioning = selectedAssetId != null
                )
            }
        }

        // --- لایه ۲: محتوای مورف شونده (The Morphing Actor) ---
        // ✅✅✅ استفاده از Modifier.offset و Modifier.size برای Layout واقعی ✅✅✅

        val cornerRadiusDp = with(density) { cornerRadius.value.toDp() }
        //val isVisible = bounds.width > 0f && bounds.height > 0f

        // --- لایه رویی: صفحه جزئیات (لایه شناور) ---
        val isDetailVisible = selectedAssetId != null
        val containerAlpha by animateFloatAsState(
            // به جای isAnimating، مستقیماً به selectedAssetId گوش بده
            targetValue = if (selectedAssetId != null) 1f else 0f,
            animationSpec = tween(
                durationMillis = 150, // کل زمان انیمیشن fade
                // مهم‌ترین بخش: انیمیشن fade با تاخیر شروع می‌شود
                // این مقدار را می‌توانید تنظیم کنید تا به حس دلخواه برسید
                delayMillis = if (selectedAssetId != null) 0 else 150
            ),
            label = "containerAlpha"
        )

        if (containerAlpha > 0f) {
            val bounds = animatedBounds.value
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { bounds.left.toDp() },
                        y = with(density) { bounds.top.toDp() }
                    )
                    .size(
                        width = with(density) { bounds.width.toDp() },
                        height = with(density) { bounds.height.toDp() }
                    )
                    .alpha(containerAlpha)
                    .clip(RoundedCornerShape(cornerRadiusDp))
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(enabled = isDetailVisible, onClick = {})
                    .zIndex(1000f) // بالا نگه داشتن لایه شناور
            ) {
                // Opacity (آلفا) محتوا را جداگانه انیمیت می‌کنیم

                val displayId = selectedAssetId ?: mainViewModel.lastSelectedId
                displayId?.let { id ->
                    AssetDetailScreen(
                        assetId = id,
                        onNavigateBack = { mainViewModel.onNavigateBack() },
                        isExpandedStable = isHeaderExpanded,
                        homeViewModel = homeViewModel
                    )
                }
            }
        }

        // --- لایه ۴: Receive Screen Overlay ---
        androidx.compose.animation.AnimatedVisibility(
            visible = showReceiveScreen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.zIndex(2000f)
        ) {
            ReceiveScreen(onDismiss = { showReceiveScreen = false })
        }

        // --- لایه ۵: MultiWallet Screen Overlay ---
        androidx.compose.animation.AnimatedVisibility(
            visible = showMultiWalletScreen,
            enter = fadeIn(animationSpec = tween(400)) +
                    scaleIn(initialScale = 0.9f, animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(300)) +
                   androidx.compose.animation.scaleOut(targetScale = 0.9f, animationSpec = tween(300)),
            modifier = Modifier.zIndex(3000f)
        ) {
           MultiWalletScreen(
                onNavigateBack = { showMultiWalletScreen = false },
                onAddNewWallet = { 
                    // دیگر مولتی‌ولت را فورا نمی‌بندیم تا در پس‌زمینه بماند
                    isFromMultiWallet = true
                    showCreateWalletScreen = true
                    pendingImportData = null
                },
                onImportExisting = {
                    // دیگر مولتی‌ولت را فورا نمی‌بندیم تا در پس‌زمینه بماند
                    isFromMultiWallet = true
                    showImportWalletScreen = true
                }
            )
        }

        // --- لایه ۶: Import Wallet Screen Overlay ---
        androidx.compose.animation.AnimatedVisibility(
            visible = showImportWalletScreen,
            enter = fadeIn(animationSpec =tween(300)) +
                    slideInVertically(
                        initialOffsetY = { it }, 
                        animationSpec =tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ),
            exit = fadeOut(animationSpec = tween(300)) +
                   slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)),
            modifier = Modifier.zIndex(4000f)
        ) {
            AddExistingWalletScreen(
                onBack = { 
                    showImportWalletScreen = false
                    isFromMultiWallet = false
                },
                onImportSuccess = { data ->
                    pendingImportData = data
                    showImportWalletScreen = false
                    showCreateWalletScreen = true
                },
                onRestoreFromCloud = { walletItem ->
                    createWalletViewModel.startRestoreFromCloud(walletItem)
                    showImportWalletScreen = false
                    showCreateWalletScreen = true
                }
            )
        }

        // --- لایه ۷: Create Wallet Screen Overlay ---
        androidx.compose.animation.AnimatedVisibility(
            visible = showCreateWalletScreen,
            enter = fadeIn(animationSpec = tween(500)) +
                    scaleIn(initialScale = 0.8f, animationSpec = spring(0.8f)),
            exit = fadeOut(animationSpec = tween(400)) ,
            modifier = Modifier.zIndex(5000f)
        ) {
            CreateWalletScreen(
                viewModel = createWalletViewModel,
                importData = pendingImportData,
                onBack = { 
                    if (pendingImportData != null) {
                        pendingImportData = null
                        showImportWalletScreen = true
                    }
                    showCreateWalletScreen = false
                    isFromMultiWallet = false
                },
                onNavigateToHome = {
                    // اگر از MultiWallet آمده بودیم، هر دو صفحه را ببند و به خانه برو
                    if (isFromMultiWallet) {
                        showCreateWalletScreen = false
                        showMultiWalletScreen = false
                        isFromMultiWallet = false
                        homeViewModel.refreshData()
                    } else {
                        // روال عادی: فقط صفحه ساخت را ببند
                        showCreateWalletScreen = false
                        homeViewModel.refreshData()
                    }
                }
            )
        }
    }
}

@Composable
fun MainScreenContent(
    padding: PaddingValues,
    mainViewModel: MainScreenViewModel,
    homeViewModel: HomeViewModel,
    selectedAssetId: String?,
    isTransitioning: Boolean = false
) {
    WalletScreens(
        modifier = Modifier.padding(padding),
        onAssetClick = { asset, bounds ->
            mainViewModel.onAssetClicked(asset.id, bounds)
        },
        viewModel = homeViewModel,
        userScrollEnabled = !isTransitioning,
        listItemModifier = { assetId ->
            Modifier.graphicsLayer {
                val isThisSelected = selectedAssetId == assetId
                // اگر یک آیتم انتخاب شده، و این آیتم، آن آیتمِ انتخاب شده نیست، محوش کن
                // آیتم انتخاب شده همیشه alpha = 1f باقی می‌ماند
                alpha = if (selectedAssetId != null && !isThisSelected) {
                    0f // آیتم‌های دیگر محو می‌شوند
                } else {
                    1f // آیتم انتخاب شده و یا وقتی هیچ آیتمی انتخاب نشده
                }
            }
        }
    )
}


/**
 * Header صفحه اصلی - شبیه عکس اول
 * شامل: emoji در دایره صورتی، نام کیف، و سه آیکون در سمت راست
 */
@Composable
private fun MainHeader(
    walletName: String,
    walletColor: Color,
    onWalletClick: () -> Unit,
    onScanClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    onCurrencyToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(), // اضافه کردن padding برای statusbar
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxWidth()
                .padding(
                    horizontal = MainScreenConstants.HEADER_PADDING_HORIZONTAL,
                    vertical = MainScreenConstants.HEADER_PADDING_VERTICAL
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // سمت راست: دایره رنگی با emoji و نام کیف
            Row(
                modifier = Modifier.clickable { onWalletClick() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MainScreenConstants.WALLET_NAME_SPACING)
            ) {

                Box(
                    modifier = Modifier
                        .size(MainScreenConstants.WALLET_AVATAR_SIZE)
                        .clip(CircleShape)
                        .background(walletColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_wallet),
                        contentDescription = "Wallet",
                        modifier = Modifier.size(MainScreenConstants.WALLET_ICON_SIZE)
                    )
                }

                Text(
                    text = walletName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Bold)),
                    fontSize = MainScreenConstants.WALLET_NAME_FONT_SIZE
                )

            }

            // سمت چپ: سه آیکون (سه نقطه، سرچ، و یک آیکون دیگر)
            Row(
                horizontalArrangement = Arrangement.spacedBy(MainScreenConstants.HEADER_SPACING),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // آیکون سه نقطه
                IconButton(
                    onClick = onMoreOptionsClick,
                    modifier = Modifier.size(MainScreenConstants.HEADER_ICON_SIZE)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(MainScreenConstants.HEADER_ICON_ICON_SIZE)
                    )
                }
                // آیکون سرچ
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.size(MainScreenConstants.HEADER_ICON_SIZE)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(MainScreenConstants.HEADER_ICON_ICON_SIZE)
                    )
                }
                // آیکون تغییر واحد ارز (یا اسکن)
                IconButton(
                    onClick = onCurrencyToggle,
                    modifier = Modifier.size(MainScreenConstants.HEADER_ICON_SIZE)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_scan),
                        contentDescription = "Currency Toggle",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(MainScreenConstants.HEADER_ICON_ICON_SIZE)
                    )
                }



            }



        }
    }
}


/**
 * Navigation Bottom Sheet - طراحی شده دقیقاً بر اساس عکس ارسالی
 */
@Composable
fun MainBottomNavigation(
    selectedTab: MainTab,
    onWalletClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onExploreClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(bottom = MainScreenConstants.BOTTOM_NAV_PADDING_BOTTOM)) {
            // خط جداکننده بسیار ظریف در بالا
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MainScreenConstants.BOTTOM_NAV_DIVIDER_HEIGHT)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = MainScreenConstants.BOTTOM_NAV_PADDING_VERTICAL,
                        horizontal = MainScreenConstants.BOTTOM_NAV_PADDING_HORIZONTAL
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                // آیکون کاوشگری (راست)
                BottomNavItem(
                    filledIconRes = R.drawable.ic_discover_fill,
                    outlinedIconRes = R.drawable.ic_discover,
                    isSelected = selectedTab == MainTab.EXPLORE,
                    onClick = onExploreClick
                )

                // آیکون کیف پول (وسط)
                BottomNavItem(
                    filledIconRes = R.drawable.ic_wallet_fill,
                    outlinedIconRes = R.drawable.ic_wallet,
                    isSelected = selectedTab == MainTab.WALLET,
                    onClick = onWalletClick
                )

                // آیکون سابقه (چپ)
                BottomNavItem(
                    filledIconRes = R.drawable.ic_history_fill,
                    outlinedIconRes = R.drawable.ic_history,
                    isSelected = selectedTab == MainTab.HISTORY,
                    onClick = onHistoryClick
                )
            }
        }
    }
}

@Composable
fun BottomNavItem(
    filledIconRes: Int,
    outlinedIconRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .size(MainScreenConstants.BOTTOM_NAV_ITEM_SIZE)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(
                id = if (isSelected) filledIconRes else outlinedIconRes
            ),
            contentDescription = null,
            modifier = Modifier.size(MainScreenConstants.BOTTOM_NAV_ICON_SIZE),
            tint = if (isSelected) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            }
        )
    }
}



@Composable
fun MorphingFabMenu(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onReceiveClick: () -> Unit = {},
    onSendClick: () -> Unit = {},
    onSwapClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()

    // استفاده از remember برای محاسبات configuration
    val configuration = LocalConfiguration.current
    val screenWidth = remember(configuration) {
        configuration.screenWidthDp.dp
    }
    val targetExpandedWidth = remember(screenWidth) {
        screenWidth - MainScreenConstants.FAB_EXPANDED_PADDING
    }

    // ۱. انیمیشن ابعاد و شکل
    val width by animateDpAsState(
        targetValue = if (isExpanded) targetExpandedWidth else MainScreenConstants.FAB_SIZE,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "Width"
    )
    val height by animateDpAsState(
        targetValue = if (isExpanded) MainScreenConstants.FAB_EXPANDED_HEIGHT else MainScreenConstants.FAB_SIZE,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "Height"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) MainScreenConstants.FAB_CORNER_RADIUS_EXPANDED else MainScreenConstants.FAB_CORNER_RADIUS_COLLAPSED,
        label = "Corners"
    )

    // ۲. انیمیشن آلفا برای محتوا
    val contentAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isExpanded) MainScreenConstants.FAB_ANIMATION_DURATION_EXPAND else MainScreenConstants.FAB_ANIMATION_DURATION_COLLAPSE
        ),
        label = "ContentAlpha"
    )


        Box(modifier = Modifier.fillMaxSize()) {
            // لایه تعاملی شفاف برای بستن منو
            if (isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onToggle() }
                )
            }

            // باکس اصلی (FAB که بزرگ می‌شود)
            val fabBackgroundColor = remember(isExpanded, isDark) {
                if (isExpanded) {
                    MainScreenConstants.FAB_EXPANDED_BACKGROUND
                } else {
                    if (isDark) MainScreenConstants.FAB_COLLAPSED_DARK else Color.Black
                }
            }

            Box(
                modifier = Modifier
                    .padding(
                        start =30.dp,
                    )
                    .align(Alignment.BottomStart)
                    .size(width, height)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(fabBackgroundColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { if (!isExpanded) onToggle() },
                contentAlignment = if (isExpanded) Alignment.TopStart else Alignment.Center
            ) {

                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier
                            .size(MainScreenConstants.FAB_ADD_ICON_SIZE)
                            .graphicsLayer { alpha = if (isExpanded) 0f else (1f-(contentAlpha)) },
                        tint = Color.White
                    )


                // محتویات منو (فقط وقتی باز است)
                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .align (Alignment.BottomEnd)
                            .padding(MainScreenConstants.FAB_CONTENT_PADDING)
                            .graphicsLayer { alpha = contentAlpha },
                        verticalArrangement = Arrangement.spacedBy(MainScreenConstants.FAB_ITEM_SPACING,
                            Alignment.CenterVertically)
                    ) {
                        FabMenuItem(
                            painter = painterResource(id = R.drawable.ic_send),
                            iconBgColor = MainScreenConstants.FAB_SEND_COLOR,
                            title = "ارسال",
                            description = "ارز های خود را به هر آدرسی ارسال کنید",
                            onClick = { onToggle() }
                        )
                        FabMenuItem(
                            painter = painterResource(id = R.drawable.ic_swap),
                            iconBgColor = MainScreenConstants.FAB_SWAP_COLOR,
                            title = "تبدیل",
                            description = "ارز های خود را بدون نیاز به خروج از کیف پول، تبدیل کنید",
                            onClick = { onToggle() }
                        )
                        FabMenuItem(
                            painter = painterResource(id = R.drawable.ic_download),
                            iconBgColor = MainScreenConstants.FAB_RECEIVE_COLOR,
                            title = "دریافت",
                            description = "دارایی های دیجیتال را از طریق آدرس منحصر به فرد خود دریافت کنید",
                            onClick = onReceiveClick
                        )
                    }
                }
            }
        }

}

@Composable
fun FabMenuItem(
    painter: Painter,
    iconBgColor: Color,
    title: String,
    description: String,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MainScreenConstants.FAB_MENU_ITEM_CORNER_RADIUS))
            .background(MainScreenConstants.FAB_MENU_ITEM_BACKGROUND)
            .clickable { onClick() }
            .padding(MainScreenConstants.FAB_MENU_ITEM_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(MainScreenConstants.FAB_MENU_ITEM_ICON_SIZE)
                .clip(CircleShape)
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painter,
                contentDescription = title,
                modifier = Modifier.size(MainScreenConstants.FAB_MENU_ITEM_ICON_ICON_SIZE),
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.width(MainScreenConstants.FAB_MENU_ITEM_SPACING))

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Medium)),
                color = Color.White,
                fontSize = 16.sp
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_light, FontWeight.Light)),
                lineHeight = MainScreenConstants.FAB_MENU_ITEM_DESCRIPTION_LINE_HEIGHT,
                fontSize = 13.sp
            )
        }
    }
}

@Preview
@Composable
fun FabPreview(){
    MaterialTheme(lightColorScheme()) {
//        MorphingFabMenu(true, {})

//        MainBottomNavigation(MainTab.WALLET,{},{},{})

        MainHeader("تست",Color.Red,{},{},{},{},{})
    }
}



enum class MainTab {
    WALLET, HISTORY, EXPLORE
}
