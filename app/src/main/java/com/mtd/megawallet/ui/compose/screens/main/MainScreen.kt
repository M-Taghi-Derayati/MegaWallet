package com.mtd.megawallet.ui.compose.screens.main

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mtd.common_ui.R
import com.mtd.megawallet.ui.compose.animations.constants.MainScreenConstants
import com.mtd.megawallet.ui.compose.screens.wallet.WalletScreen
import com.mtd.megawallet.viewmodel.news.HomeViewModel


/**
 * صفحه اصلی اپلیکیشن که شامل:
 * - Header (با نام کیف، رنگ، آیکون‌ها)
 * - محتوای داخلی (WalletScreen)
 * - Navigation Bottom Sheet
 * - FAB
 */
@Composable
fun MainScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToWalletManagement: () -> Unit = {},
    onScanClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onMoreOptionsClick: () -> Unit = {},
    onFabClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onExploreClick: () -> Unit = {}
) {
    val activeWallet by viewModel.activeWallet.collectAsState()

    MainScreenContent(
        walletName = activeWallet?.name ?: "کیف پول",
        walletColor = activeWallet?.color?.let { Color(it) } ?: MainScreenConstants.DEFAULT_WALLET_COLOR,
        onNavigateToWalletManagement = onNavigateToWalletManagement,
        onScanClick = onScanClick,
        onSearchClick = onSearchClick,
        onMoreOptionsClick = onMoreOptionsClick,
        onHistoryClick = onHistoryClick,
        onExploreClick = onExploreClick,
        onCurrencyToggle = { viewModel.toggleDisplayCurrency() }
    ) {
        WalletScreen(viewModel = viewModel)
    }
}

/**
 * نسخه Stateless برای Preview و تست
 */
@Composable
fun MainScreenContent(
    walletName: String,
    walletColor: Color,
    onNavigateToWalletManagement: () -> Unit = {},
    onScanClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onMoreOptionsClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onExploreClick: () -> Unit = {},
    onCurrencyToggle: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var isFabExpanded by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                MainHeader(
                    walletName = walletName,
                    walletColor = walletColor,
                    onWalletClick = onNavigateToWalletManagement,
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
            contentWindowInsets = WindowInsets.statusBars
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                content()

                // منوی مورف شونده (Morphing FAB)
                MorphingFabMenu(
                    isExpanded = isFabExpanded,
                    onToggle = { isFabExpanded = !isFabExpanded }
                )
            }
        }
    }
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
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MainScreenConstants.HEADER_PADDING_HORIZONTAL,
                    vertical = MainScreenConstants.HEADER_PADDING_VERTICAL
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // سمت چپ: سه آیکون (سه نقطه، سرچ، و یک آیکون دیگر)
            Row(
                horizontalArrangement = Arrangement.spacedBy(MainScreenConstants.HEADER_SPACING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // آیکون تغییر واحد ارز (یا اسکن)
                IconButton(
                    onClick = onCurrencyToggle,
                    modifier = Modifier.size(MainScreenConstants.HEADER_ICON_SIZE)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_scan),
                        contentDescription = "Currency Toggle",
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
                        modifier = Modifier.size(MainScreenConstants.HEADER_ICON_ICON_SIZE)
                    )
                }

                // آیکون سه نقطه
                IconButton(
                    onClick = onMoreOptionsClick,
                    modifier = Modifier.size(MainScreenConstants.HEADER_ICON_SIZE)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        modifier = Modifier.size(MainScreenConstants.HEADER_ICON_ICON_SIZE)
                    )
                }
            }

            // سمت راست: دایره رنگی با emoji و نام کیف
            Row(
                modifier = Modifier.clickable { onWalletClick() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MainScreenConstants.WALLET_NAME_SPACING)
            ) {
                Text(
                    text = walletName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Bold)),
                    fontSize = MainScreenConstants.WALLET_NAME_FONT_SIZE
                )

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
            }


        }
    }
}


/**
 * Navigation Bottom Sheet - طراحی شده دقیقاً بر اساس عکس ارسالی
 */
@Composable
private fun MainBottomNavigation(
    selectedTab: MainTab,
    onWalletClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onExploreClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(bottom = MainScreenConstants.BOTTOM_NAV_PADDING_BOTTOM)) {
            // خط جداکننده بسیار ظریف در بالا
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MainScreenConstants.BOTTOM_NAV_DIVIDER_HEIGHT)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(
                            alpha = MainScreenConstants.BOTTOM_NAV_DIVIDER_ALPHA
                        )
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
                // آیکون سابقه (چپ)
                BottomNavItem(
                    filledIconRes = R.drawable.ic_history_fill,
                    outlinedIconRes = R.drawable.ic_history,
                    isSelected = selectedTab == MainTab.HISTORY,
                    onClick = onHistoryClick
                )

                // آیکون کیف پول (وسط)
                BottomNavItem(
                    filledIconRes = R.drawable.ic_wallet_fill,
                    outlinedIconRes = R.drawable.ic_wallet,
                    isSelected = selectedTab == MainTab.WALLET,
                    onClick = onWalletClick
                )

                // آیکون کاوشگری (راست)
                BottomNavItem(
                    filledIconRes = R.drawable.ic_discover_fill,
                    outlinedIconRes = R.drawable.ic_discover,
                    isSelected = selectedTab == MainTab.EXPLORE,
                    onClick = onExploreClick
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
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
                if (isDark) Color.White else Color.Black
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            }
        )
    }
}



@Composable
private fun MorphingFabMenu(
    isExpanded: Boolean,
    onToggle: () -> Unit
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

    // استفاده از CompositionLocalProvider در سطح بالاتر برای FabMenuItem
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
                        start = MainScreenConstants.FAB_HORIZONTAL_PADDING,
                        bottom = MainScreenConstants.FAB_HORIZONTAL_PADDING
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
                if (contentAlpha < 0.5f) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier
                            .size(MainScreenConstants.FAB_ADD_ICON_SIZE)
                            .graphicsLayer { alpha = 1f - (contentAlpha * 2f).coerceIn(0f, 1f) },
                        tint = Color.White
                    )
                }

                // محتویات منو (فقط وقتی باز است)
                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(MainScreenConstants.FAB_CONTENT_PADDING)
                            .graphicsLayer { alpha = contentAlpha },
                        verticalArrangement = Arrangement.spacedBy(MainScreenConstants.FAB_ITEM_SPACING)
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
                            onClick = { onToggle() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FabMenuItem(
    painter: Painter,
    iconBgColor: Color,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    // CompositionLocalProvider در MorphingFabMenu قرار گرفته است
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(MainScreenConstants.FAB_MENU_ITEM_CORNER_RADIUS))
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
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium)),
                color = Color.White
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_light, FontWeight.Light)),
                lineHeight = MainScreenConstants.FAB_MENU_ITEM_DESCRIPTION_LINE_HEIGHT
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MaterialTheme(darkColorScheme()) {
        MainScreenContent(
            walletName = "کیف پول من",
            walletColor = MainScreenConstants.PREVIEW_WALLET_COLOR,
            content = {
                // محتوای تستی...
            }
        )
    }
}



enum class MainTab {
    WALLET, HISTORY, EXPLORE
}

