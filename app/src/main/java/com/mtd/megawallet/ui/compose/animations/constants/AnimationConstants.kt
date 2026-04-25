package com.mtd.megawallet.ui.compose.animations.constants

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Constants for animations used throughout the app.
 * This helps avoid magic numbers and makes animations consistent.
 */
object AnimationConstants {
    
    // ========== Durations ==========
    
    /** Duration for generating wallet animation in milliseconds */
    const val GENERATING_ANIMATION_DURATION = 600
    
    /** Duration for card flip animation in milliseconds */
    const val CARD_FLIP_DURATION = 800
    
    /** Duration for fade animations in milliseconds */
    const val FADE_DURATION = 500
    
    /** Duration for line drawing animation in milliseconds */
    const val LINE_DRAW_DURATION = 1500
    
    /** Delay before starting line drawing animation in milliseconds */
    const val LINE_DRAW_DELAY = 400
    
    /** Duration for border animation in milliseconds */
    const val BORDER_ANIMATION_DURATION = 1000
    
    /** Duration for reveal animation in milliseconds */
    const val REVEAL_ANIMATION_DURATION = 1500
    
    /** Delay before starting reveal animation in milliseconds */
    const val REVEAL_ANIMATION_DELAY = 2000
    
    /** Duration for content alpha animation in milliseconds */
    const val CONTENT_ALPHA_DURATION = 500
    
    /** Duration for rainbow shift animation in milliseconds */
    const val RAINBOW_SHIFT_DURATION = 4000
    
    /** Duration for particle animation in milliseconds */
    const val PARTICLE_ANIMATION_DURATION = 2500
    
    /** Duration for dash phase animation in milliseconds */
    const val DASH_PHASE_DURATION = 5500

    /** Ratio for reveal radius multiplier */
    const val REVEAL_RADIUS_MULTIPLIER = 1.4f
    
    /** Ratio for pulse scale minimum */
    const val PULSE_SCALE_MIN = 1f
    
    /** Ratio for pulse scale maximum */
    const val PULSE_SCALE_MAX = 1.08f
    
    /** Alpha threshold for content visibility */
    const val CONTENT_ALPHA_THRESHOLD = 0.95f
    
    /** Alpha threshold for reveal progress */
    const val REVEAL_PROGRESS_THRESHOLD = 0.9f
    
    /** Alpha threshold for border visibility */
    const val BORDER_ALPHA_THRESHOLD = 0.8f
    
    // ========== Sizes ==========
    
    /** Grid step size in dp */
    val GRID_STEP_DP = 40.dp
    
    /** Card corner radius in dp */
    val CARD_CORNER_RADIUS = 21.dp
    
    /** Card width in dp */
    val CARD_WIDTH = 300.dp
    
    /** Card height in dp (initial) */
    val CARD_HEIGHT_INITIAL = 0.dp
    
    /** Card height in dp (final) */
    val CARD_HEIGHT_FINAL = 250.dp
    
    /** Button height in dp */
    val BUTTON_HEIGHT = 56.dp
    

    
    // ========== Colors ==========
    
    /** Grid color alpha */
    const val GRID_COLOR_ALPHA = 0.2f

    
    /** Vertical offset when card is flipped (in dp, converted to pixels) */
    const val VERTICAL_OFFSET_FLIPPED = -170f
    
    /** Rainbow gradient offset for animated text */
    const val RAINBOW_GRADIENT_OFFSET = 400f
    
    /** Button border expansion in dp */
    val BUTTON_BORDER_EXPANSION = 4.dp
    
    /** Delay for vertical offset animation when flipping */
    const val VERTICAL_OFFSET_ANIMATION_DELAY = 800
    
    /** Duration for vertical offset animation */
    const val VERTICAL_OFFSET_ANIMATION_DURATION = 800
    
    /** Delay for backup options content animation */
    const val BACKUP_OPTIONS_ANIMATION_DELAY = 1000
    
    /** Duration for backup options content animation */
    const val BACKUP_OPTIONS_ANIMATION_DURATION = 800
    
    /** Backup tip chip background color (dark gray) */
    const val BACKUP_TIP_CHIP_COLOR = 0xFF252525
    
    /** Backup tip chip arrow width */
    val BACKUP_TIP_ARROW_WIDTH = 20.dp
    
    /** Backup tip chip arrow height */
    val BACKUP_TIP_ARROW_HEIGHT = 12.dp
    
    /** Backup button height */
    val BACKUP_BUTTON_HEIGHT = 34.dp
    
    /** Backup button horizontal padding */
    val BACKUP_BUTTON_HORIZONTAL_PADDING = 19.dp
    
    /** Backup button vertical padding */
    val BACKUP_BUTTON_VERTICAL_PADDING = 4.dp
    
    /** Backup button icon size */
    val BACKUP_BUTTON_ICON_SIZE = 19.dp
    
    /** Backup button border width */
    val BACKUP_BUTTON_BORDER_WIDTH = 1.2.dp
    
    /** Backup button stroke width for success state */
    val BACKUP_BUTTON_SUCCESS_STROKE_WIDTH = 1.2.dp
    
    /** Backup button stroke width for normal state */
    val BACKUP_BUTTON_NORMAL_STROKE_WIDTH = 1.5.dp
    
    /** Dash path effect intervals for button border */
    val DASH_PATH_INTERVAL_1 = 40f
    val DASH_PATH_INTERVAL_2 = 25f
    
    /** Backup tip chip padding */
    val BACKUP_TIP_CHIP_PADDING = 20.dp
    
    /** Backup tip chip spacing */
    val BACKUP_TIP_CHIP_SPACING = 8.dp
    
    /** Backup options spacing */
    val BACKUP_OPTIONS_SPACING = 16.dp
    val BACKUP_OPTIONS_BOTTOM_SPACING = 24.dp
    
    /** Backup options text sizes */
    val BACKUP_OPTIONS_DESCRIPTION_SIZE = 12.sp
    val BACKUP_OPTIONS_WARNING_SIZE = 10.sp
    
    /** Backup option item padding */
    val BACKUP_OPTION_ITEM_PADDING = 16.dp
    
    /** Backup option item icon size */
    val BACKUP_OPTION_ITEM_ICON_SIZE = 44.dp
    
    /** Backup option item border width for popular */
    val BACKUP_OPTION_POPULAR_BORDER_WIDTH = 2.5.dp
    
    /** Backup option item border width for normal */
    val BACKUP_OPTION_NORMAL_BORDER_WIDTH = 1.dp
    
    /** Backup option badge padding */
    val BACKUP_OPTION_BADGE_HORIZONTAL_PADDING = 8.dp
    val BACKUP_OPTION_BADGE_VERTICAL_PADDING = 2.dp
    
    /** Backup option badge text size */
    val BACKUP_OPTION_BADGE_TEXT_SIZE = 10.sp
    
    /** Backup option description text size */
    val BACKUP_OPTION_DESCRIPTION_TEXT_SIZE = 12.sp
    
    /** Light blue color for cloud backup */
    const val CLOUD_BACKUP_COLOR = 0xFF00BFFF
    
    /** Standard spacing between text elements */
    val TEXT_SPACING = 8.dp
}

