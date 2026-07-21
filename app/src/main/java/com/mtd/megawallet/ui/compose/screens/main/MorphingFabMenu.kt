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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.common_ui.theme.IranSansBoldMedium
import com.mtd.common_ui.theme.IranSansLightLight
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.megawallet.ui.compose.animations.constants.MainScreenConstants

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
                        start = 30.dp,
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
                            .graphicsLayer { alpha = if (isExpanded) 0f else (1f - (contentAlpha)) },
                        tint = Color.White
                    )


                // محتویات منو (فقط وقتی باز است)
                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.BottomEnd)
                            .padding(MainScreenConstants.FAB_CONTENT_PADDING)
                            .graphicsLayer { alpha = contentAlpha },
                        verticalArrangement = Arrangement.spacedBy(MainScreenConstants.FAB_ITEM_SPACING,
                            Alignment.CenterVertically)
                    ) {
                        FabMenuItem(
                            painter = painterResource(id = R.drawable.ic_send),
                            iconBgColor = MaterialTheme.colorScheme.secondary,
                            title = "ارسال",
                            description = "ارز های خود را به هر آدرسی ارسال کنید",
                            onClick = onSendClick
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
                            iconBgColor = MaterialTheme.colorScheme.primary,
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
                fontFamily = IranSansBoldMedium,
                color = Color.White,
                fontSize = 16.sp
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                fontFamily = IranSansLightLight,
                lineHeight = MainScreenConstants.FAB_MENU_ITEM_DESCRIPTION_LINE_HEIGHT,
                fontSize = 13.sp
            )
        }
    }
}

// ============================================
// Previews
// ============================================

@Preview(name = "FabMenuItem - Dark")
@Composable
private fun FabMenuItemDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            FabMenuItem(
                painter = painterResource(id = R.drawable.ic_send),
                iconBgColor = MaterialTheme.colorScheme.secondary,
                title = "ارسال",
                description = "ارز های خود را به هر آدرسی ارسال کنید",
                onClick = {}
            )
        }
    }
}
