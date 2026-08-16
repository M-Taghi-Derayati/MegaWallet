package com.mtd.megawallet.ui.compose.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtd.domain.model.BlockchainConnectionMode
import com.mtd.domain.model.CloudWalletItem
import com.mtd.domain.model.FiatCurrency
import com.mtd.domain.model.HomeUiState
import com.mtd.domain.model.ImportData
import com.mtd.megawallet.ui.compose.animations.constants.MainScreenConstants
import com.mtd.megawallet.ui.compose.screens.addexistingwallet.AddExistingWalletScreen
import com.mtd.megawallet.ui.compose.screens.createwallet.CreateWalletScreen
import com.mtd.megawallet.ui.compose.screens.explore.ExploreScreen
import com.mtd.megawallet.ui.compose.screens.history.TransactionHistoryScreen
import com.mtd.megawallet.ui.compose.screens.history.components.TransactionDetailsBottomSheet
import com.mtd.megawallet.ui.compose.screens.send.SendScreen
import com.mtd.megawallet.ui.compose.screens.swap.SwapFlowScreen
import com.mtd.megawallet.ui.compose.screens.tokens.ManageTokensSheet
import com.mtd.megawallet.ui.compose.screens.wallet.AssetDetailScreen
import com.mtd.megawallet.ui.compose.screens.wallet.MultiWalletScreen
import com.mtd.megawallet.ui.compose.screens.wallet.ReceiveScreen
import com.mtd.megawallet.ui.compose.screens.wallet.WalletScreens
import com.mtd.megawallet.viewmodel.CreateWalletViewModel
import com.mtd.megawallet.viewmodel.HomeViewModel
import com.mtd.megawallet.viewmodel.MainScreenViewModel
import com.mtd.megawallet.viewmodel.history.TransactionHistoryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


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
    onMoreOptionsClick: () -> Unit = {},
    onFabClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onExploreClick: () -> Unit = {}
) {
    val activeWallet by homeViewModel.activeWallet.collectAsStateWithLifecycle()

    val selectedAssetId by mainViewModel.selectedAssetId.collectAsStateWithLifecycle()

    // KAN-9 / KAN-19 — DIRECT/PROXY transport, toggled from the header search icon.
    val connectionMode by mainViewModel.connectionMode.collectAsStateWithLifecycle()

    // TASK-56 — واحد نمایش (دلار/تومان)؛ هدر هم آن را نشان می‌دهد و هم عوض می‌کند.
    val fiatCurrency by mainViewModel.fiatCurrency.collectAsStateWithLifecycle()

    // مدیریت دکمه Back: اگر در صفحه جزئیات هستیم، به لیست برگرد
    BackHandler(enabled = selectedAssetId != null) {
        mainViewModel.onNavigateBack()
    }

    MainDashboardContent(
        walletName = activeWallet?.name ?: "کیف پول",
        walletColor = activeWallet?.color?.let { Color(it) }
            ?: MaterialTheme.colorScheme.primary,
        onNavigateToWalletManagement = onNavigateToWalletManagement,
        onMoreOptionsClick = onMoreOptionsClick,
        onHistoryClick = onHistoryClick,
        onExploreClick = onExploreClick,
        onCurrencyToggle = { mainViewModel.toggleFiatCurrency() },
        mainViewModel = mainViewModel,
        homeViewModel = homeViewModel,
        selectedAssetId = selectedAssetId,
        connectionMode = connectionMode,
        fiatCurrency = fiatCurrency
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
    onMoreOptionsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onExploreClick: () -> Unit,
    onCurrencyToggle: () -> Unit,
    selectedAssetId: String?,
    connectionMode: BlockchainConnectionMode,
    fiatCurrency: FiatCurrency,
) {
    val density = LocalDensity.current
    var fullScreenRect by remember { mutableStateOf(Rect.Zero) }
    var fullHeightPx by remember { mutableStateOf(0) } // ذخیره ارتفاع کل صفحه

    // History detail sheet state is hoisted here (rather than inside the History tab block) so the sheet
    // can be rendered as a root-level, full-screen overlay further down. That is what lets it (1) cover
    // the bottom navigation and (2) use the full screen height when expanded — both regressed while the
    // sheet lived inside the Scaffold content, where innerPadding clipped it above the bottom bar.
    val historyViewModel: TransactionHistoryViewModel = hiltViewModel()
    val selectedHistoryTransaction by historyViewModel.selectedTransaction.collectAsStateWithLifecycle()

    // Item 6 — همان instanceِ SendViewModel که SendScreen استفاده می‌کند (Activity-scoped)، تا اسکنِ QR
    // بتواند آدرسِ خوانده‌شده را مستقیم روی آن بنشاند و بعد صفحهٔ Send باز شود.
    val sendViewModel: com.mtd.megawallet.viewmodel.SendViewModel = hiltViewModel()

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
    // TASK-15 — the full-screen overlays are navigation state, so they survive process death via
    // rememberSaveable: a low-memory kill/restore lands back on the same screen instead of the
    // dashboard. (Purely visual affordances like header/FAB expansion stay in plain remember.)
    var showSendScreen by rememberSaveable { mutableStateOf(false) }
    var sendInitialAssetId by rememberSaveable { mutableStateOf<String?>(null) }
    var showReceiveScreen by rememberSaveable { mutableStateOf(false) }
    var showSwapScreen by rememberSaveable { mutableStateOf(false) }
    var showMultiWalletScreen by rememberSaveable { mutableStateOf(false) }
    // شیتِ مدیریتِ توکن (ذره‌بینِ هدر). مثل بقیهٔ اورلی‌ها ناوبری است، پس از مرگِ پروسه جان می‌برد.
    var showManageTokens by rememberSaveable { mutableStateOf(false) }

    // Item 6 — اورلیِ اسکنرِ QR (transient، پس remember ساده کافی است).
    var showScanner by remember { mutableStateOf(false) }

    // حالات مدیریت ساخت و وارد کردن کیف پول
    var showCreateWalletScreen by rememberSaveable { mutableStateOf(false) }
    var showImportWalletScreen by rememberSaveable { mutableStateOf(false) }
    var pendingImportData by remember { mutableStateOf<ImportData?>(null) }
    var pendingCloudRestore by remember { mutableStateOf<CloudWalletItem?>(null) }

    // فلگ برای تشخیص اینکه آیا از MultiWallet وارد فلوی ساخت/ایمپورت شده‌ایم
    var isFromMultiWallet by remember { mutableStateOf(false) }
    val selectedTab by mainViewModel.selectedTab.collectAsStateWithLifecycle()

    // Load transaction history lazily — only once the History tab is actually selected — and let the
    // ViewModel know when it's hidden so a wallet switch defers its refetch (items 1 & 6).
    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.HISTORY) historyViewModel.onScreenShown() else historyViewModel.onScreenHidden()
        // Item 4 note: the wallet screen is intentionally NOT gated on visibility. It writes the shared
        // per-asset balance cache every other screen reads, so its balance signals must be processed live
        // (see HomeViewModel.listenToGlobalEvents); deferring them would leave other screens showing a
        // stale, pre-transaction balance.
    }

    val shouldShowMainFab = selectedTab == MainTab.WALLET &&
        selectedAssetId == null &&
        !showSendScreen &&
        !showReceiveScreen &&
        !showSwapScreen &&
        !showMultiWalletScreen &&
        !showCreateWalletScreen &&
        !showImportWalletScreen

    LaunchedEffect(shouldShowMainFab) {
        if (!shouldShowMainFab) {
            isFabExpanded = false
        }
    }

    // مدیریت دکمه Back برای بستن لایه‌های مختلف
    BackHandler(enabled = showManageTokens || showSendScreen || showSwapScreen || showMultiWalletScreen || showReceiveScreen || showCreateWalletScreen || showImportWalletScreen || selectedAssetId != null) {
        when {
            // بالاترین اولویت: شیت روی همه‌چیز باز می‌شود، پس Back باید اول همان را ببندد.
            showManageTokens -> showManageTokens = false
            showSendScreen -> showSendScreen = false
            // پشتیبان: تا وقتی فلوی تبدیل کامپوز است، BackHandlerِ خودش (که بین فازها عقب می‌رود)
            // اولویت دارد؛ این شاخه فقط برای بازهٔ انیمیشنِ خروج است.
            showSwapScreen -> showSwapScreen = false
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
                    delay(60.milliseconds)
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
            .semantics { testTagsAsResourceId = true } // expose Compose testTags to UiAutomator (PERF-10)
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
                        onScanClick = { showScanner = true },
                        // ضربه: مدیریت توکن. نگه‌داشتن: تعویضِ DIRECT/PROXY — که کارِ قبلیِ همین
                        // آیکون بود و حذف نشده، فقط از ضربه به نگه‌داشتن منتقل شده.
                        onSearchClick = { showManageTokens = true },
                        onToggleConnectionMode = { mainViewModel.toggleConnectionMode() },
                        onMoreOptionsClick = onMoreOptionsClick,
                        onCurrencyToggle = onCurrencyToggle,
                        connectionMode = connectionMode,
                        fiatCurrency = fiatCurrency
                    )
                },
                bottomBar = {
                    val hasPendingHistoryActivity by mainViewModel.hasPendingHistoryActivity.collectAsStateWithLifecycle()
                    MainBottomNavigation(
                        selectedTab = selectedTab,
                        showHistoryPendingIndicator = hasPendingHistoryActivity && selectedTab != MainTab.HISTORY,
                        onWalletClick = { mainViewModel.selectTab(MainTab.WALLET) },
                        onHistoryClick = { mainViewModel.selectTab(MainTab.HISTORY) },
                        onExploreClick = { mainViewModel.selectTab(MainTab.EXPLORE) }
                    )
                },
                floatingActionButton = {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = shouldShowMainFab,
                        enter = fadeIn(animationSpec = tween(180)) +
                                scaleIn(initialScale = 0.86f, animationSpec = tween(220)) +
                                slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(220)),
                        exit = fadeOut(animationSpec = tween(140)) +
                                androidx.compose.animation.scaleOut(targetScale = 0.86f, animationSpec = tween(160)) +
                                slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = tween(160))
                    ) {
                        MorphingFabMenu(
                            isExpanded = isFabExpanded,
                            onToggle = { isFabExpanded = !isFabExpanded },
                            onSendClick = {
                                sendInitialAssetId = null
                                showSendScreen = true
                                isFabExpanded = false
                            },
                            onReceiveClick = {
                                showReceiveScreen = true
                                isFabExpanded = false
                            },
                            onSwapClick = {
                                showSwapScreen = true
                                isFabExpanded = false
                            }
                        )
                    }
                },
                contentWindowInsets = WindowInsets.statusBars
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (selectedTab == MainTab.WALLET || selectedAssetId != null) {
                        MainTabLayer(
                            active = selectedTab == MainTab.WALLET,
                            label = "WalletTabLayer"
                        ) {
                            MainScreenContent(
                                padding = PaddingValues(0.dp),
                                mainViewModel = mainViewModel,
                                homeViewModel = homeViewModel,
                                selectedAssetId = selectedAssetId,
                                isTransitioning = selectedAssetId != null || selectedTab != MainTab.WALLET,
                                isActive = selectedTab == MainTab.WALLET
                            )
                        }
                    }

                    if (selectedTab == MainTab.HISTORY) {
                        val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()

                        LaunchedEffect(homeUiState) {
                            val success = homeUiState as? HomeUiState.Success ?: return@LaunchedEffect
                            historyViewModel.syncAssetPricesFromHomeAssets(success.assets)
                        }

                        MainTabLayer(
                            active = true,
                            label = "HistoryTabLayer"
                        ) {
                            TransactionHistoryScreen(
                                viewModel = historyViewModel,
                                homeViewModel = homeViewModel,
                                showDetailsBottomSheet = false,
                                contentEnabled = true
                            )
                        }
                        // The details sheet itself is rendered as a root-level overlay (see below), not here,
                        // so it can draw over the bottom navigation and expand to the full screen height.
                    }

                    if (selectedTab == MainTab.EXPLORE) {
                        MainTabLayer(
                            active = true,
                            label = "ExploreTabLayer"
                        ) {
                            ExploreScreen()
                        }
                    }
                }
            }
        }


        val cornerRadiusDp = with(density) { cornerRadius.value.toDp() }

        val isDetailVisible = selectedAssetId != null
        val containerAlpha by animateFloatAsState(
            // به جای isAnimating، مستقیماً به selectedAssetId گوش بده
            targetValue = if (selectedAssetId != null) 1f else 0f,
            animationSpec = tween(
                durationMillis = 150,
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
                    .graphicsLayer { alpha = containerAlpha }
                    .clip(RoundedCornerShape(cornerRadiusDp))
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(enabled = isDetailVisible, onClick = {})
                    .zIndex(MainScreenConstants.ZLayer.ASSET_DETAIL) // بالا نگه داشتن لایه شناور
            ) {
                // Opacity (آلفا) محتوا را جداگانه انیمیت می‌کنیم

                val displayId = selectedAssetId ?: mainViewModel.lastSelectedId
                displayId?.let { id ->
                    AssetDetailScreen(
                        assetId = id,
                        onNavigateBack = { mainViewModel.onNavigateBack() },
                        onSendClick = { asset ->
                            sendInitialAssetId = asset.id
                            showSendScreen = true
                        },
                        isExpandedStable = isHeaderExpanded,
                        homeViewModel = homeViewModel
                    )
                }
            }
        }

        // --- لایه 3: Send Screen Overlay ---
        androidx.compose.animation.AnimatedVisibility(
            visible = showSendScreen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.zIndex(MainScreenConstants.ZLayer.SEND)
        ) {
            SendScreen(
                homeViewModel = homeViewModel,
                initialSelectedAssetId = sendInitialAssetId,
                onDismiss = {
                    showSendScreen = false
                    sendInitialAssetId = null
                },
                onScanClick = { showScanner = true },
                sendViewModel = sendViewModel
            )
        }

        // --- لایه ۲.۵: Swap Screen Overlay ---
        androidx.compose.animation.AnimatedVisibility(
            visible = showSwapScreen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.zIndex(MainScreenConstants.ZLayer.SWAP)
        ) {
            SwapFlowScreen(
                homeViewModel = homeViewModel,
                walletName = walletName,
                onDismiss = { showSwapScreen = false }
            )
        }

        // --- لایه ۶: QR Scanner Overlay (بالاترین لایه) — آدرس را می‌خواند و Send را با همان آدرس باز می‌کند ---
        androidx.compose.animation.AnimatedVisibility(
            visible = showScanner,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.zIndex(6000f)
        ) {
            com.mtd.megawallet.ui.compose.screens.scanner.QrScannerScreen(
                onClose = { showScanner = false },
                onAddressScanned = { address ->
                    sendViewModel.setRecipient(address)
                    sendInitialAssetId = null
                    showSendScreen = true
                    showScanner = false
                }
            )
        }


        ManageTokensSheet(
            visible = showManageTokens,
            onDismiss = { showManageTokens = false }
        )


        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(MainScreenConstants.ZLayer.HISTORY_DETAIL_SHEET)
        ) {
            TransactionDetailsBottomSheet(
                visible = selectedTab == MainTab.HISTORY && selectedHistoryTransaction != null,
                transaction = selectedHistoryTransaction,
                viewModel = historyViewModel,
                onDismiss = { historyViewModel.selectTransaction(null) }
            )
        }

        // --- لایه ۴: Receive Screen Overlay ---
        androidx.compose.animation.AnimatedVisibility(
            visible = showReceiveScreen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.zIndex(MainScreenConstants.ZLayer.RECEIVE)
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
            modifier = Modifier.zIndex(MainScreenConstants.ZLayer.MULTI_WALLET)
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
            modifier = Modifier.zIndex(MainScreenConstants.ZLayer.ADD_EXISTING_WALLET)
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
                    pendingCloudRestore = walletItem
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
            modifier = Modifier.zIndex(MainScreenConstants.ZLayer.CREATE_WALLET)
        ) {
            val createWalletViewModel: CreateWalletViewModel = hiltViewModel()

            // این بلوک هر بار که overlay باز می‌شود یک‌بار اجرا می‌شود (محتوای AnimatedVisibility با هر باز
            // شدن دوباره compose می‌شود). استیتِ باقی‌مانده از دفعهٔ قبل را قطعی مقداردهی می‌کند تا فلوِ
            // ساخت/بازیابی همیشه از ابتدا باز شود و «از جای قبلی» باز نشود.
            LaunchedEffect(Unit) {
                val restore = pendingCloudRestore
                when {
                    restore != null -> {
                        createWalletViewModel.startRestoreFromCloud(restore)
                        pendingCloudRestore = null
                    }
                    pendingImportData != null -> {
                        createWalletViewModel.resetToInitialState()
                        createWalletViewModel.setPendingImportData(pendingImportData)
                    }
                    else -> createWalletViewModel.resetToInitialState()
                }
            }

            CreateWalletScreen(
                viewModel = createWalletViewModel,
                importData = pendingImportData,
                onBack = {
                    if (pendingImportData != null) {
                        pendingImportData = null
                        showImportWalletScreen = true
                    }
                    pendingCloudRestore = null
                    showCreateWalletScreen = false
                    isFromMultiWallet = false
                },
                onNavigateToHome = {
                    // اگر از MultiWallet آمده بودیم، هر دو صفحه را ببند و به خانه برو
                    if (isFromMultiWallet) {
                        showCreateWalletScreen = false
                        showMultiWalletScreen = false
                        isFromMultiWallet = false
                        pendingCloudRestore = null
                        homeViewModel.refreshData()
                    } else {
                        // روال عادی: فقط صفحه ساخت را ببند
                        showCreateWalletScreen = false
                        pendingCloudRestore = null
                        homeViewModel.refreshData()
                    }
                }
            )
        }
    }
}

@Composable
private fun MainTabLayer(
    active: Boolean,
    label: String,
    content: @Composable () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = label
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (active) 1f else 0f)
            .graphicsLayer {
                this.alpha = alpha
            }
    ) {
        content()
    }
}

@Composable
fun MainScreenContent(
    padding: PaddingValues,
    mainViewModel: MainScreenViewModel,
    homeViewModel: HomeViewModel,
    selectedAssetId: String?,
    isTransitioning: Boolean = false,
    isActive: Boolean = true
) {
    WalletScreens(
        modifier = Modifier.padding(padding),
        onAssetClick = { asset, bounds ->
            if (isActive) {
                mainViewModel.onAssetClicked(asset.id, bounds)
            }
        },
        viewModel = homeViewModel,
        userScrollEnabled = !isTransitioning,
        captureItemBounds = isActive,
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


enum class MainTab {
    WALLET, HISTORY, EXPLORE
}
