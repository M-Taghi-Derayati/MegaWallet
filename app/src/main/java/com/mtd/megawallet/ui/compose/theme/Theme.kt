package com.mtd.megawallet.ui.compose.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat

/**
 * تنظیمات تم MegaWallet
 * در اینجا مشخص میکنیم هر فیلد متریال ۳ به کدام رنگ از Color.kt مپ شود.
 */

private val DarkColorScheme = darkColorScheme(
    primary = BrandGreenDark,           // همخوانی با استایل
    onPrimary = White,
    secondary = BrandBlue,
    onSecondary = White,
    tertiary = TextPrimaryDark,    // متون اصلی سفید
    onTertiary = TextSecondaryDark, // متون ثانویه خاکستری ملایم
    background = DarkBackground,   // مشکی مطلق
    onBackground = TextPrimaryDark,
    surface = DarkSurface,         // کارت‌های تیره سیستمی
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkDivider,  // استفاده برای لبه‌ها و خطوط
    onSurfaceVariant = TextSecondaryDark,
    error = BrandRedDark,
    onError = White
)

private val LightColorScheme = lightColorScheme(
    primary = BrandGreen,
    onPrimary = White,
    secondary = BrandBlue,
    onSecondary = White,
    tertiary = TextPrimaryLight,   // متون اصلی مشکی
    onTertiary = TextSecondaryLight, // متون ثانویه خاکستری تیره
    background = LightBackground,   // سفید خالص
    onBackground = TextPrimaryLight,
    surface = LightSurface,         // کارت‌های خاکستری بسیار روشن
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurface,
    onSurfaceVariant = TextSecondaryLight,
    error = BrandRed,
    onError = White
)

@Composable
fun MegaWalletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
