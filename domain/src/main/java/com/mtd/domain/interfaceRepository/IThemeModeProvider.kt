package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/**
 * تنها مالکِ قابلِ مشاهدهٔ پوستهٔ انتخاب‌شدهٔ کاربر.
 *
 * عمداً هم‌شکلِ [IFiatCurrencyProvider] است و نه [IBlockchainConnectionModeProvider]: حالتِ اتصال
 * را فقط لایهٔ داده سرِ ساختِ منبع می‌خواند و یک خواندنِ همگام کافی است، ولی پوسته باید در همان
 * لحظهٔ ضربهٔ کاربر روی کلِ درختِ Compose بنشیند — و آن فقط با یک جریانِ قابلِ مشاهده ممکن است.
 */
interface IThemeModeProvider {

    /** پوستهٔ انتخاب‌شده. تا کامل‌شدنِ [ensurePrimed] مقدارش [ThemeMode.DEFAULT] است. */
    val themeMode: StateFlow<ThemeMode>

    /**
     * خواندنِ مقدارِ ماندگار از دیسک. suspend است تا خواندنِ دیسک روی ترد اصلی نیفتد، و
     * idempotent است تا هر مصرف‌کننده بتواند بی‌هماهنگی صدایش بزند.
     *
     * تا وقتی تمام نشود [themeMode] برابرِ [ThemeMode.SYSTEM] گزارش می‌شود. این خودش را اصلاح
     * می‌کند و غلط نیست: SYSTEM همان چیزی است که برنامه پیش از هر انتخابی نشان می‌داد.
     */
    suspend fun ensurePrimed()

    /** ذخیره و سپس انتشار — به همین ترتیب. */
    suspend fun set(mode: ThemeMode)
}
