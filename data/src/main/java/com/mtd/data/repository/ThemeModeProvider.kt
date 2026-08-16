package com.mtd.data.repository

import com.mtd.domain.interfaceRepository.IThemeModeProvider
import com.mtd.domain.interfaceRepository.IUserPreferencesRepository
import com.mtd.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * همان الگوی [FiatCurrencyProvider]، برای پوسته.
 *
 * `@Singleton` است تا هر دو Activity یک مقدار را ببینند؛ اگر هر کدام نمونهٔ خودش را می‌گرفت،
 * تعویضِ پوسته در صفحهٔ تنظیمات روی صفحهٔ خوش‌آمد نمی‌نشست.
 *
 * مقدارِ اولیه [ThemeMode.DEFAULT] است نه یک خواندنِ مسدودکنندهٔ دیسک: خواندن در سازنده روی
 * هر تردی می‌افتاد که اول این را inject می‌کند — که معمولاً ترد اصلیِ وسطِ composition است.
 */
@Singleton
class ThemeModeProvider @Inject constructor(
    private val userPreferencesRepository: IUserPreferencesRepository
) : IThemeModeProvider {

    private val _themeMode = MutableStateFlow(ThemeMode.DEFAULT)
    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val primeMutex = Mutex()

    @Volatile
    private var primed = false

    override suspend fun ensurePrimed() {
        if (primed) return
        primeMutex.withLock {
            if (primed) return
            _themeMode.value = userPreferencesRepository.getThemeMode()
            primed = true
        }
    }

    /**
     * همان قفلِ [ensurePrimed] را می‌گیرد: بدونِ آن، یک `set` که وسطِ خواندنِ استارتِ سرد برسد
     * با نتیجهٔ کهنهٔ همان خواندن بازنویسی می‌شد و انتخابِ کاربر بی‌صدا برمی‌گشت.
     */
    override suspend fun set(mode: ThemeMode) = primeMutex.withLock {
        userPreferencesRepository.setThemeMode(mode)
        // از این‌جا به بعد مقدارِ حافظه معتبر است، پس ensurePrimedِ بعدی کاری نمی‌کند.
        primed = true
        _themeMode.value = mode
    }
}
