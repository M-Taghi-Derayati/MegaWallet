package com.mtd.megawallet.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.mtd.domain.model.ThemeMode

/**
 * تبدیلِ ترجیحِ کاربر به «تاریک باشد یا نه».
 *
 * این تصمیم عمداً در لایهٔ UI گرفته می‌شود و نه در provider: تنها چیزی که [ThemeMode.SYSTEM] را
 * به یک پاسخِ درست تبدیل می‌کند پیکربندیِ فعلیِ دستگاه است، و آن را فقط از داخلِ composition
 * می‌شود خواند. اگر پاسخ در لایهٔ داده حساب می‌شد، تغییرِ پوستهٔ گوشی وسطِ اجرا دیده نمی‌شد.
 */
@Composable
fun ThemeMode.resolveIsDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
