package com.mtd.megawallet.ui.compose.animations.constants

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Constants برای MainScreen
 */
 object MainScreenConstants {
    // Header
    val HEADER_ICON_SIZE = 40.dp
    val HEADER_ICON_ICON_SIZE = 24.dp
    val WALLET_AVATAR_SIZE = 44.dp
    val WALLET_ICON_SIZE = 24.dp
    val HEADER_PADDING_HORIZONTAL = 16.dp
    val HEADER_PADDING_VERTICAL = 12.dp
    val HEADER_SPACING = 4.dp
    val WALLET_NAME_SPACING = 12.dp
    val WALLET_NAME_FONT_SIZE = 18.sp
    
    // Bottom Navigation
    val BOTTOM_NAV_ITEM_SIZE = 40.dp
    val BOTTOM_NAV_ICON_SIZE = 28.dp
    val BOTTOM_NAV_PADDING_HORIZONTAL = 16.dp
    val BOTTOM_NAV_PADDING_VERTICAL = 12.dp
    val BOTTOM_NAV_PADDING_BOTTOM = 12.dp
    val BOTTOM_NAV_DIVIDER_HEIGHT = 0.4.dp
    val BOTTOM_NAV_DIVIDER_ALPHA = 0.1f
    
    // FAB
    val FAB_SIZE = 56.dp
    val FAB_EXPANDED_HEIGHT = 270.dp
    val FAB_CORNER_RADIUS_EXPANDED = 16.dp
    val FAB_CORNER_RADIUS_COLLAPSED = 28.dp
    val FAB_HORIZONTAL_PADDING = 5.dp
    val FAB_EXPANDED_PADDING = FAB_HORIZONTAL_PADDING * 2
    val FAB_CONTENT_PADDING = 8.dp
    val FAB_ITEM_SPACING = 4.dp
    val FAB_ADD_ICON_SIZE = 30.dp
    val FAB_ANIMATION_DURATION_EXPAND = 250
    val FAB_ANIMATION_DURATION_COLLAPSE = 100
    
    // FAB Menu Item
    val FAB_MENU_ITEM_ICON_SIZE = 44.dp
    val FAB_MENU_ITEM_ICON_ICON_SIZE = 24.dp
    val FAB_MENU_ITEM_CORNER_RADIUS = 10.dp
    val FAB_MENU_ITEM_PADDING = 12.dp
    val FAB_MENU_ITEM_SPACING = 16.dp
    val FAB_MENU_ITEM_DESCRIPTION_LINE_HEIGHT = 16.sp
    
    // Colors
    val FAB_EXPANDED_BACKGROUND = Color(0xFF0B0B0B)
    val FAB_COLLAPSED_DARK = Color(0xFF222222)
    val FAB_MENU_ITEM_BACKGROUND = Color(0xFF2D2D2D)

    val FAB_SWAP_COLOR = Color(0xFF444444)

    /**
     * Z-index scale for the root-level, full-screen overlays stacked above the dashboard [Scaffold].
     * Named so the stacking order is self-documenting and accidental collisions are obvious at a glance.
     * Larger value = drawn on top. The dashboard/Scaffold itself sits at the implicit base layer (z = 0).
     * Values are spaced by 100 to leave head-room for inserting a layer between two existing ones.
     */
    object ZLayer {
        const val ASSET_DETAIL = 1000f          // morphing asset-detail screen
        const val SWAP = 1700f                  // swap flow
        const val SEND = 1800f                  // send flow
        const val HISTORY_DETAIL_SHEET = 1900f  // transaction-details bottom sheet (history tab)
        const val RECEIVE = 2000f               // receive flow
        const val MULTI_WALLET = 3000f          // wallet switcher
        const val ADD_EXISTING_WALLET = 4000f   // import existing wallet
        const val CREATE_WALLET = 5000f         // create new wallet
    }
}