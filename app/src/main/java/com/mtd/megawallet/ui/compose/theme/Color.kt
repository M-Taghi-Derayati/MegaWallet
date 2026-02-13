package com.mtd.megawallet.ui.compose.theme

import androidx.compose.ui.graphics.Color

/**
 * MegaWallet Color Palette
 * این فایل شامل تمام رنگ‌های پایه اپلیکیشن است.
 * برای استفاده در تم، از نام‌های متغیرها استفاده کنید.
 */

// --- Brand Colors (رنگ‌های برند - استایل فمیلی) ---
val BrandBlue = Color(0xFF007AFF) // آبی سیستمی iOS
val BrandGreen = Color(0xFF34C759) // سبز سیستمی (Light)
val BrandRed = Color(0xFFFF3B30)   // قرمز سیستمی (Light)

val BrandGreenDark = Color(0xFF30D158) // سبز سیستمی مخصوص حالت تیره
val BrandRedDark = Color(0xFFFF453A)   // قرمز سیستمی مخصوص حالت تیره

// --- Neutral Colors (رنگ‌های خنثی - حالت تیره فمیلی) ---
val DarkBackground = Color(0xFF000000) // مشکی مطلق (Pure Black)
val DarkSurface = Color(0xFF1C1C1E)    // خاکستری تیره برای کارت‌ها
val DarkDivider = Color(0xFF38383A)     // خطوط جداکننده در حالت تیره

// --- Neutral Colors (رنگ‌های خنثی - حالت روشن فمیلی) ---
val LightBackground = Color(0xFFFFFFFF) // سفید خالص
val LightSurface = Color(0xFFF2F2F7)    // خاکستری بسیار روشن و نرم برای کارت‌ها
val LightDivider = Color(0xFFC6C6C8)    // خطوط جداکننده در حالت روشن

// --- Text Colors (رنگ‌های متن) ---
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFF8E8E93) // خاکستری ملایم فمیلی

val TextPrimaryLight = Color(0xFF000000)
val TextSecondaryLight = Color(0xFF636366)

// --- Legacy Symbols (برای سازگاری کدها) ---
val Black0 = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val Green = BrandGreen
val Red = BrandRed
val Grey50 = Color(0xFF323232)
val Grey25 = Color(0xFF191919)
val DarkGray = Color(0xFF1D1D1D)
