package com.mtd.megawallet.ui.compose.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.common_ui.theme.icons.AnimatedApertureIcon
import com.mtd.common_ui.theme.icons.AnimatedClockIcon
import com.mtd.common_ui.theme.icons.AnimatedWalletIcon
import com.mtd.megawallet.ui.compose.animations.constants.MainScreenConstants

/**
 * Navigation Bottom Sheet - طراحی شده دقیقاً بر اساس عکس ارسالی
 */
@Composable
fun MainBottomNavigation(
    selectedTab: MainTab,
    showHistoryPendingIndicator: Boolean = false,
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

                Box(modifier = Modifier) {
                    AnimatedApertureIcon(
                        isFilled = selectedTab == MainTab.EXPLORE,
                        iconSize = MainScreenConstants.BOTTOM_NAV_ICON_SIZE,
                        onClick = onExploreClick
                    )
                }
                Box(modifier = Modifier) {
                    AnimatedWalletIcon(
                        isFilled = selectedTab == MainTab.WALLET,
                        iconSize = MainScreenConstants.BOTTOM_NAV_ICON_SIZE,
                        onClick = onWalletClick
                    )
                }



                Box(
                    modifier = Modifier

                        .size(MainScreenConstants.BOTTOM_NAV_ITEM_SIZE)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onHistoryClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (showHistoryPendingIndicator) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(MainScreenConstants.BOTTOM_NAV_ICON_SIZE-3.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-7).dp, y = 7.dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary)
                        )
                    }
                    AnimatedClockIcon(
                        isFilled = selectedTab == MainTab.HISTORY,
                        iconSize = MainScreenConstants.BOTTOM_NAV_ICON_SIZE,
                        onClick = onHistoryClick
                    )
                }
            }
        }
    }
}

// ============================================
// Previews
// ============================================

@Preview(name = "MainBottomNavigation - Light")
@Composable
private fun MainBottomNavigationLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        MainBottomNavigation(
            selectedTab = MainTab.WALLET,
            showHistoryPendingIndicator = false,
            onWalletClick = {},
            onHistoryClick = {},
            onExploreClick = {}
        )
    }
}

@Preview(name = "MainBottomNavigation - Dark (pending)")
@Composable
private fun MainBottomNavigationDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        MainBottomNavigation(
            selectedTab = MainTab.HISTORY,
            showHistoryPendingIndicator = true,
            onWalletClick = {},
            onHistoryClick = {},
            onExploreClick = {}
        )
    }
}
