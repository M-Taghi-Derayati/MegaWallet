package com.mtd.megawallet.ui.compose.screens.welcome

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * پالت و رندرِ «دودل»‌های صفحهٔ خوش‌آمد.
 * تمام المان‌ها با Canvas و به‌صورت کدنویسی‌شده (بدون اسستِ PNG/SVG و بدون PathParser) کشیده می‌شوند
 * تا هر مختصاتی قابلِ پیش‌بینی و درست باشد. پنج آیکونی که طراح داده بود (سکهٔ کج، نمودار،
 * چرخ‌دنده، هواپیما، چیپ) اینجا با bezier/arc/roundRectِ تمیز و با رنگِ ثابتِ خودِ طراح بازسازی شده‌اند.
 * هر تابع حولِ مبدأ (0,0) می‌کشد؛ جای‌گذاری/چرخش/مقیاس در [WelcomeIntroScreen] با withTransform انجام می‌شود.
 */
object IntroPalette {
    val Gold = Color(0xFFF5B942)
    val GoldLight = Color(0xFFFFD37A)
    val Red = Color(0xFFFF3B30)
    val Blue = Color(0xFF007AFF)
    val SkyBlue = Color(0xFF54C7FC)
    val Green = Color(0xFF34C759)
    val Orange = Color(0xFFFF9F45)
    val Yellow = Color(0xFFFFD60A)
    val Pink = Color(0xFFFF6B9D)
    val Cyan = Color(0xFF32ADE6)
    val Ink = Color(0xFF1C1C1E)
    val Cream = Color(0xFFFFFFFF)
    // رنگ‌های ثابتِ آیکون‌های طراح
    val White = Color(0xFFFFFFFF)
    val BrandYellow = Color(0xFFFACC15)
    val ShadowAmber = Color(0xFFD97706)
}

enum class DoodleType {
    Coin, Heart, Star, Sparkle, Flower, Blob, Dot, Ring, Chat, Lock, Rocket, Leaf, Gem, Arrow, Mascot,
    Plane, Chip, CoinTilt, Chart, Gear
}

/* ============================ کمک‌توابعِ مسیر ============================ */
private fun starPath(points: Int, outer: Float, inner: Float): Path {
    val p = Path()
    val step = (PI / points).toFloat()
    var a = -(PI.toFloat() / 2f)
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        val x = cos(a) * r; val y = sin(a) * r
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        a += step
    }
    p.close(); return p
}
private fun heartPath(s: Float): Path {
    val h = s / 2f
    return Path().apply {
        moveTo(0f, h * 0.85f)
        cubicTo(-h * 1.25f, h * 0.05f, -h * 0.85f, -h * 0.95f, 0f, -h * 0.28f)
        cubicTo(h * 0.85f, -h * 0.95f, h * 1.25f, h * 0.05f, 0f, h * 0.85f)
        close()
    }
}
private fun leafPath(s: Float): Path {
    val h = s / 2f
    return Path().apply {
        moveTo(0f, -h); quadraticTo(h, 0f, 0f, h); quadraticTo(-h, 0f, 0f, -h); close()
    }
}
private fun gemPath(s: Float): Path {
    val h = s / 2f
    return Path().apply {
        moveTo(0f, -h * 0.9f); lineTo(h * 0.9f, -h * 0.15f)
        lineTo(0f, h * 0.95f); lineTo(-h * 0.9f, -h * 0.15f); close()
    }
}
private fun arrowPath(s: Float): Path {
    val h = s / 2f
    return Path().apply {
        moveTo(0f, -h)
        lineTo(h * 0.7f, -h * 0.05f); lineTo(h * 0.3f, -h * 0.05f)
        lineTo(h * 0.3f, h * 0.9f); lineTo(-h * 0.3f, h * 0.9f)
        lineTo(-h * 0.3f, -h * 0.05f); lineTo(-h * 0.7f, -h * 0.05f)
        close()
    }
}
/** یک درخششِ چهارپرِ کوچک حولِ یک نقطه. */
private fun DrawScope.sparkle(center: Offset, r: Float, color: Color, alpha: Float) {
    withTransform({ translate(center.x, center.y) }) { drawPath(starPath(4, r, r * 0.3f), color, alpha = alpha) }
}

/* ============================ رندرِ دودل ============================ */
fun DrawScope.drawDoodle(type: DoodleType, color: Color, sizePx: Float, alpha: Float) {
    val s = sizePx; val h = s / 2f
    when (type) {
        DoodleType.Coin -> {
            drawCircle(color, radius = h, center = Offset.Zero, alpha = alpha)
            drawCircle(IntroPalette.GoldLight, radius = h * 0.68f, center = Offset(-h * 0.14f, -h * 0.14f), alpha = alpha * 0.9f)
        }
        DoodleType.Heart -> drawPath(heartPath(s), color, alpha = alpha)
        DoodleType.Star -> drawPath(starPath(5, h, h * 0.42f), color, alpha = alpha)
        DoodleType.Sparkle -> drawPath(starPath(4, h, h * 0.28f), color, alpha = alpha)
        DoodleType.Flower -> {
            val pr = h * 0.42f; val dist = h * 0.52f
            for (i in 0 until 5) {
                val a = (i * (2f * PI.toFloat() / 5f)) - PI.toFloat() / 2f
                drawCircle(color, radius = pr, center = Offset(cos(a) * dist, sin(a) * dist), alpha = alpha)
            }
            drawCircle(IntroPalette.Yellow, radius = h * 0.34f, center = Offset.Zero, alpha = alpha)
        }
        DoodleType.Blob -> drawRoundRect(color, topLeft = Offset(-h, -h), size = Size(s, s), cornerRadius = CornerRadius(s * 0.44f, s * 0.42f), alpha = alpha)
        DoodleType.Dot -> drawCircle(color, radius = h, center = Offset.Zero, alpha = alpha)
        DoodleType.Ring -> drawCircle(color, radius = h * 0.82f, center = Offset.Zero, alpha = alpha, style = Stroke(width = s * 0.18f))
        DoodleType.Chat -> {
            drawRoundRect(color, topLeft = Offset(-h, -h * 0.75f), size = Size(s, s * 0.62f), cornerRadius = CornerRadius(s * 0.3f, s * 0.3f), alpha = alpha)
            drawPath(Path().apply { moveTo(-h * 0.32f, -h * 0.14f); lineTo(-h * 0.62f, h * 0.42f); lineTo(-h * 0.02f, -h * 0.14f); close() }, color, alpha = alpha)
            for (i in -1..1) drawCircle(IntroPalette.Cream, radius = s * 0.06f, center = Offset(i * s * 0.22f, -h * 0.44f), alpha = alpha)
        }
        DoodleType.Lock -> {
            drawArc(color, 180f, 180f, false, topLeft = Offset(-h * 0.42f, -h * 0.62f), size = Size(s * 0.42f, s * 0.42f), style = Stroke(width = s * 0.12f), alpha = alpha)
            drawRoundRect(color, topLeft = Offset(-h * 0.55f, -h * 0.12f), size = Size(s * 0.55f, s * 0.6f), cornerRadius = CornerRadius(s * 0.14f, s * 0.14f), alpha = alpha)
            drawCircle(IntroPalette.Ink, radius = s * 0.06f, center = Offset(-h * 0.27f, h * 0.16f), alpha = alpha * 0.7f)
        }
        DoodleType.Rocket -> {
            drawRoundRect(IntroPalette.Cream, topLeft = Offset(-h * 0.42f, -h * 0.7f), size = Size(s * 0.42f, s * 0.9f), cornerRadius = CornerRadius(s * 0.21f, s * 0.21f), alpha = alpha)
            drawPath(Path().apply { moveTo(0f, -h); lineTo(h * 0.21f, -h * 0.5f); lineTo(-h * 0.21f, -h * 0.5f); close() }, color, alpha = alpha)
            drawCircle(IntroPalette.SkyBlue, radius = s * 0.13f, center = Offset(-h * 0.21f, -h * 0.14f), alpha = alpha)
            drawPath(Path().apply { moveTo(-h * 0.42f, h * 0.05f); lineTo(-h * 0.72f, h * 0.4f); lineTo(-h * 0.42f, h * 0.35f); close() }, color, alpha = alpha)
            drawPath(Path().apply { moveTo(0f, h * 0.05f); lineTo(h * 0.3f, h * 0.4f); lineTo(0f, h * 0.35f); close() }, color, alpha = alpha)
        }
        DoodleType.Leaf -> {
            drawPath(leafPath(s), color, alpha = alpha)
            drawLine(IntroPalette.Cream, Offset(0f, -h * 0.8f), Offset(0f, h * 0.8f), strokeWidth = s * 0.05f, alpha = alpha * 0.6f)
        }
        DoodleType.Gem -> {
            drawPath(gemPath(s), color, alpha = alpha)
            drawLine(IntroPalette.Cream, Offset(-h * 0.9f, -h * 0.15f), Offset(h * 0.9f, -h * 0.15f), strokeWidth = s * 0.04f, alpha = alpha * 0.5f)
        }
        DoodleType.Arrow -> drawPath(arrowPath(s), color, alpha = alpha)

        // ---- ماسکات (فعلاً استفاده نمی‌شود؛ طبق درخواست از چیدمان حذف شد) ----
        DoodleType.Mascot -> {
            val fuzz = 18; val fr = h * 0.9f
            for (i in 0 until fuzz) {
                val a = i * (2f * PI.toFloat() / fuzz)
                drawCircle(color, radius = h * 0.14f, center = Offset(cos(a) * fr, sin(a) * fr), alpha = alpha)
            }
            drawRoundRect(color, topLeft = Offset(-h * 0.82f, -h * 0.82f), size = Size(s * 0.82f, s * 0.82f), cornerRadius = CornerRadius(s * 0.3f, s * 0.3f), alpha = alpha)
            for (sgn in intArrayOf(-1, 1)) drawRoundRect(IntroPalette.Ink, topLeft = Offset(sgn * h * 0.28f - h * 0.09f, -h * 0.18f), size = Size(h * 0.18f, h * 0.3f), cornerRadius = CornerRadius(h * 0.09f, h * 0.09f), alpha = alpha)
        }

        // ---- پنج آیکونِ طراح، بازنویسی‌شده با مختصاتِ تمیز و رنگِ ثابت ----
        DoodleType.CoinTilt -> withTransform({ rotate(-20f) }) {
            val w = h * 0.82f; val hh = h * 0.62f
            drawOval(IntroPalette.ShadowAmber, topLeft = Offset(-w, -hh + h * 0.16f), size = Size(w * 2f, hh * 2f), alpha = alpha) // ضخامت/سایهٔ لبه
            drawOval(IntroPalette.BrandYellow, topLeft = Offset(-w, -hh), size = Size(w * 2f, hh * 2f), alpha = alpha)            // رویِ سکه
            clipPath(Path().apply { addOval(Rect(-w, -hh, w, hh)) }) {
                drawLine(IntroPalette.White, Offset(-w * 0.7f, -hh * 0.1f), Offset(w * 0.7f, hh * 0.7f), strokeWidth = hh * 0.55f, cap = StrokeCap.Round, alpha = alpha * 0.25f) // برقِ مورب
            }
        }
        DoodleType.Chart -> {
            val rr = h * 0.62f; val sw = h * 0.42f; val d = rr * 2f; val tl = Offset(-rr, -rr)
            drawArc(IntroPalette.BrandYellow, -90f, 250f, false, topLeft = tl, size = Size(d, d), style = Stroke(sw, cap = StrokeCap.Round), alpha = alpha) // بخشِ بزرگ
            drawArc(IntroPalette.BrandYellow, 180f, 60f, false, topLeft = tl, size = Size(d, d), style = Stroke(sw, cap = StrokeCap.Round), alpha = alpha)  // قطاعِ جدا
            drawArc(IntroPalette.White, -50f, 70f, false, topLeft = tl, size = Size(d, d), style = Stroke(sw * 0.45f, cap = StrokeCap.Round), alpha = alpha * 0.25f) // برق
        }
        DoodleType.Gear -> {
            for (i in 0 until 8) withTransform({ rotate(i * 45f) }) {
                drawRoundRect(IntroPalette.BrandYellow, topLeft = Offset(-h * 0.17f, -h * 0.95f), size = Size(h * 0.34f, h * 0.46f), cornerRadius = CornerRadius(h * 0.13f), alpha = alpha) // دندانه
            }
            drawCircle(IntroPalette.BrandYellow, radius = h * 0.66f, center = Offset.Zero, alpha = alpha) // بدنه
            drawCircle(IntroPalette.ShadowAmber, radius = h * 0.26f, center = Offset.Zero, alpha = alpha) // سوراخِ وسط (مستقل از تم)
            sparkle(Offset(h * 0.92f, -h * 0.82f), h * 0.24f, IntroPalette.White, alpha)
            sparkle(Offset(-h * 0.88f, -h * 0.66f), h * 0.15f, IntroPalette.White, alpha)
        }
        DoodleType.Plane -> withTransform({ rotate(-15f) }) {
            drawPath(Path().apply { moveTo(-h * 0.9f, h * 0.15f); lineTo(h * 0.9f, -h * 0.55f); lineTo(h * 0.05f, h * 0.9f); lineTo(-h * 0.05f, h * 0.1f); close() }, IntroPalette.BrandYellow, alpha = alpha) // بدنه
            drawPath(Path().apply { moveTo(-h * 0.05f, h * 0.1f); lineTo(h * 0.05f, h * 0.9f); lineTo(h * 0.2f, h * 0.15f); close() }, IntroPalette.ShadowAmber, alpha = alpha) // بالِ سایه
            drawLine(IntroPalette.ShadowAmber, Offset(-h * 0.05f, h * 0.1f), Offset(h * 0.9f, -h * 0.55f), strokeWidth = h * 0.05f, alpha = alpha * 0.45f) // خطِ تا
        }
        DoodleType.Chip -> {
            listOf(Offset(-h * 0.45f, -h * 0.5f), Offset(h * 0.42f, -h * 0.55f), Offset(-h * 0.25f, h * 0.5f)).forEach { c ->
                withTransform({ translate(c.x, c.y); rotate(-12f) }) {
                    drawRoundRect(IntroPalette.BrandYellow, topLeft = Offset(-h * 0.34f, -h * 0.22f), size = Size(h * 0.68f, h * 0.44f), cornerRadius = CornerRadius(h * 0.18f), alpha = alpha) // بلوک
                }
            }
            listOf(Offset(h * 0.32f, h * 0.02f), Offset(h * 0.5f, h * 0.5f), Offset(-h * 0.02f, -h * 0.02f)).forEach { drawCircle(IntroPalette.BrandYellow, h * 0.1f, it, alpha = alpha) } // خال‌ها
        }
    }
}