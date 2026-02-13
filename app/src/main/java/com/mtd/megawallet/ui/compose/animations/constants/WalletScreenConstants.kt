package com.mtd.megawallet.ui.compose.animations.constants

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Constants برای WalletScreen
 */
 object WalletScreenConstants {
    // Total Balance Section
    val TOTAL_BALANCE_PADDING_TOP = 20.dp
    val TOTAL_BALANCE_PADDING_BOTTOM = 20.dp
    val TOTAL_BALANCE_FONT_SIZE = 56.sp
    val TOTAL_BALANCE_FONT_SIZE_DETAIL = 36.sp
    val TOTAL_BALANCE_LETTER_SPACING = (-1).sp
    val TOTAL_BALANCE_HIDDEN_LETTER_SPACING = 4.sp
    val TOTAL_BALANCE_HIDDEN_PADDING_TOP = 8.dp
    val CURRENCY_SYMBOL_FONT_SIZE = 18.sp
    val CURRENCY_SYMBOL_PADDING_TOP = 8.dp
    val CURRENCY_SYMBOL_PADDING_END = 4.dp
    val TOMAN_FONT_SIZE = 16.sp
    val TOMAN_PADDING_TOP = 12.dp
    val TOMAN_PADDING_END = 6.dp
    val CROSSFADE_DURATION = 300
    val ANIMATION_DURATION_TOTAL_BALANCE = 1000
    val LOADING_INDICATOR_SIZE = 14.dp
    val LOADING_INDICATOR_STROKE_WIDTH = 2.dp
    
    // Tabs Section
    val TABS_PADDING_HORIZONTAL = 16.dp
    val TETHER_PRICE_PADDING_BOTTOM = 0.dp
    val DIVIDER_THICKNESS = 0.4.dp
    val DIVIDER_ALPHA = 0.7f
    val DIVIDER_SPACING_TOP = 0.dp
    val TABS_SPACING_BOTTOM = 16.dp
    
    // Asset List
    val ASSET_ITEM_PADDING_HORIZONTAL = 20.dp
    val ASSET_ITEM_PADDING_VERTICAL = 14.dp
    val ASSET_ICON_SIZE = 48.dp
    val ASSET_ICON_MAIN_SIZE = 44.dp
    val ASSET_ICON_MAIN_SIZE_LARGE = 60.dp
    val ASSET_ICON_NETWORK_SIZE = 24.dp
    val ASSET_ICON_NETWORK_SIZE_LARGE = 26.dp
    val ASSET_ICON_NETWORK_PADDING = 1.1.dp
    val ASSET_ICON_SPACING = 16.dp
    val ASSET_ANIMATION_DURATION = 600
    val ASSET_BALANCE_SPACING = 4.dp
    val ASSET_NETWORK_CHART_SIZE = 14.dp
    val ASSET_NETWORK_CHART_SPACING = 8.dp
    val ASSET_PRICE_SYMBOL_FONT_SIZE = 10.sp
    val ASSET_PRICE_SYMBOL_PADDING_END = 2.dp
    val ASSET_LIST_BOTTOM_SPACING = 80.dp
    
    // Network Distribution Chart
    val CHART_STROKE_WIDTH = 2.5.dp
    val CHART_BACKGROUND_COLOR = Color(0xFF2C2C2E)
    val CHART_GAP_ANGLE = 6f
    val CHART_START_ANGLE = -90f
    val CHART_FULL_CIRCLE = 360f
}