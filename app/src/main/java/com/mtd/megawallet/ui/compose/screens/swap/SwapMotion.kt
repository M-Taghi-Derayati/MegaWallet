package com.mtd.megawallet.ui.compose.screens.swap

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * تنها جایی که حرکتِ فلوی تبدیل تعریف می‌شود.
 *
 * دو قاعده:
 *  - هر چیزی که کاربر می‌تواند وسطش دخالت کند spring است، نه tween با مدتِ ثابت؛ spring از سرعتِ
 *    فعلی ادامه می‌دهد و پرش نمی‌کند. فقط پروازِ FLIP و fadeهای غیرقابل‌قطع tween هستند.
 *  - وقتی [animated] خاموش است (`ANIMATOR_DURATION_SCALE = 0`) همه چیز [snap] می‌شود. فلو باید
 *    بدون هیچ انیمیشنی کاملاً قابل استفاده بماند، پس هیچ محتوایی پشتِ یک انیمیشن قفل نیست.
 */
@Immutable
class SwapMotion(val animated: Boolean) {

    fun <T> screen(): FiniteAnimationSpec<T> =
        if (!animated) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f)

    fun <T> sheet(): FiniteAnimationSpec<T> =
        if (!animated) snap() else spring(dampingRatio = 0.88f, stiffness = 300f)

    fun <T> sheetExit(): FiniteAnimationSpec<T> =
        if (!animated) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f)

    /** تأکید: پاپِ موفقیت، ظاهر شدنِ آیکونِ توکنِ دریافت. */
    fun <T> emphasis(): FiniteAnimationSpec<T> =
        if (!animated) snap() else spring(dampingRatio = 0.62f, stiffness = 360f)

    /** میکرو-تعامل: فشردنِ کلید، هایلایت. */
    fun <T> snapping(): FiniteAnimationSpec<T> =
        if (!animated) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f)

    fun <T> fade(durationMs: Int = FADE_MEDIUM, delayMs: Int = 0): FiniteAnimationSpec<T> =
        if (!animated) snap() else tween(durationMs, delayMillis = delayMs, easing = EaseInOut)

    /** پروازِ FLIP و باز/بستهٔ ارتفاعِ سکشن‌ها؛ مسیرِ مشخص، نه spring. */
    fun <T> morph(durationMs: Int = MORPH, delayMs: Int = 0): FiniteAnimationSpec<T> =
        if (!animated) snap() else tween(durationMs, delayMillis = delayMs, easing = EaseInOut)

    /** مدتِ پروازِ اشتراکیِ آیکون؛ صفر یعنی جهش، بدون انتظار. */
    val flightMillis: Int get() = if (animated) MORPH else 0

    companion object {
        const val FADE_FAST = 150
        const val FADE_MEDIUM = 240
        const val MORPH = 380
        const val MORPH_EXIT = 300

        val EaseInOut: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    }
}

val LocalSwapMotion: ProvidableCompositionLocal<SwapMotion> =
    compositionLocalOf { SwapMotion(animated = true) }

/**
 * حرکت را از تنظیمِ سیستم می‌خواند. `ANIMATOR_DURATION_SCALE = 0` یعنی کاربر (یا حالتِ
 * صرفه‌جویی باتری، یا کاهشِ حرکت) انیمیشن را خاموش کرده است.
 */
@Composable
fun rememberSwapMotion(): SwapMotion {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    return remember(context, inspecting) {
        val scale = if (inspecting) {
            1f
        } else {
            runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                )
            }.getOrDefault(1f)
        }
        SwapMotion(animated = scale > 0f)
    }
}

/**
 * پنجرهٔ [start]..[end] از یک پیشرفتِ ۰..۱، نرمال‌شده به ۰..۱.
 *
 * برای staggerِ اجزای یک سکشن روی **یک** مقدارِ انیمیشن است، به‌جای چند Animatable مستقل که باید
 * با تأخیر هماهنگ نگه داشته شوند.
 */
fun segment(progress: Float, start: Float, end: Float): Float {
    if (end <= start) return if (progress >= end) 1f else 0f
    return ((progress - start) / (end - start)).coerceIn(0f, 1f)
}
