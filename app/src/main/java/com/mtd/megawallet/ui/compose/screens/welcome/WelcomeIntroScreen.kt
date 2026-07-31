package com.mtd.megawallet.ui.compose.screens.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.mtd.common_ui.theme.IranSansRegularMedium
import com.mtd.common_ui.theme.MegaWalletTheme
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun WelcomeIntroScreen(
    onCreateWallet: () -> Unit,
    onConnectWallet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val doodles = remember { buildDoodles() }
    val coins = remember { buildCoins() }
    val clock = remember { Animatable(0f) }
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { clock.animateTo(1f, tween(TOTAL_MS.toInt(), easing = LinearEasing)) }
    LaunchedEffect(Unit) { delay(CONTENT_START_MS); contentVisible = true }

    val contentAnim by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(600), label = "intro_content"
    )
    val slidePx = with(LocalDensity.current) { 28.dp.toPx() }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = clock.value * TOTAL_MS
            // فاز ۱: ریزشِ سکه‌ها
            val coinStartY = -size.height * 0.12f
            val coinEndY = size.height * 1.18f
            coins.forEach { c ->
                val ct = (t - c.delayMs) / COIN_FALL
                if (ct in 0f..1f) {
                    val cx = size.width * c.startX + size.width * c.drift * ct
                    val cy = lerp(coinStartY, coinEndY, ct * ct)
                    val a = if (ct > 0.82f) lerp(1f, 0f, (ct - 0.82f) / 0.18f) else 1f
                    withTransform({ translate(cx, cy); rotate(c.spin * ct, pivot = Offset.Zero) }) {
                        drawDoodle(DoodleType.Coin, c.color, c.sizeDp.dp.toPx(), a)
                    }
                }
            }
            // فاز ۲: انفجارِ دودل‌ها از مرکز (مرکز بالاتر، پشتِ فضای خالیِ وسط)
            val ox = size.width * 0.5f
            val oy = size.height * 0.46f
            doodles.forEach { d ->
                val local = ((t - BURST_START - d.delayMs) / BURST_FLIGHT).coerceIn(0f, 1f)
                if (local <= 0f) return@forEach
                val e = easeOutBack(local)
                val appear = (local * 4f).coerceIn(0f, 1f)
                val px = lerp(ox, size.width * d.x, e)
                val py = lerp(oy, size.height * d.y, e)
                val rot = lerp(d.spin, d.finalRot, e)
                withTransform({ translate(px, py); rotate(rot, pivot = Offset.Zero); scale(appear, appear, pivot = Offset.Zero) }) {
                    drawDoodle(d.type, d.color, d.sizeDp.dp.toPx(), appear)
                }
            }
        }

        // فاز ۳: محتوای نشسته
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    alpha = contentAnim; translationY = (1f - contentAnim) * slidePx
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("رمز ارز ها\nتحت کنترل تو", style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground, fontFamily = IranSansRegularMedium)
                Spacer(Modifier.height(12.dp))
                Text("یک کیف پول جدید بسازید یا کیف پول موجود خود را اضافه کنید", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.tertiary, fontFamily = IranSansRegularMedium)
                Spacer(Modifier.height(28.dp))
                Button(onClick = { if (contentVisible) onCreateWallet() }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = CircleShape) {
                    Text("ساخت کیف پول جدید", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary, fontFamily = IranSansRegularMedium)
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { if (contentVisible) onConnectWallet() }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface), shape = CircleShape) {
                    Text("من کیف پول دارم", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary, fontFamily = IranSansRegularMedium)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/* ============================ مدلِ ذرات ============================ */
private data class Doodle(val type: DoodleType, val color: Color, val x: Float, val y: Float, val sizeDp: Float, val spin: Float, val delayMs: Float, val finalRot: Float)
private data class FallingCoin(val color: Color, val startX: Float, val drift: Float, val sizeDp: Float, val spin: Float, val delayMs: Float)

private const val TOTAL_MS = 2500f
private const val BURST_START = 520f
private const val BURST_FLIGHT = 720f
private const val COIN_FALL = 780f
private const val CONTENT_START_MS = 1450L

private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f; val c3 = c1 + 1f; val t = x - 1f
    return 1f + c3 * t * t * t + c1 * t * t
}

/**
 * چیدمانِ الهام‌گرفته از عکسِ مرجع: میدانِ پر از لبهٔ بالا تا ~۰.۶۶ ارتفاع، با کراپِ لبه‌ها
 * (xهای نزدیکِ ۰ و ) و قهرمان‌های بزرگ. وسطِ نما (حدودِ ۰.۴۵–۰.۵۲) عمداً خالی گذاشته شده
 * و ماسکاتِ مرکزی طبق درخواست حذف شده است.
 */
private fun buildDoodles(): List<Doodle> {
    val p = IntroPalette
    return listOf(
        // ---- ردیفِ بالا ----
        Doodle(DoodleType.Flower, p.Blue, 0.55f, 0.07f, 64f, 220f, 0f, 8f),
        Doodle(DoodleType.Star, p.GoldLight, 0.39f, 0.06f, 16f, 180f, 8f, 0f),
        Doodle(DoodleType.Star, p.Yellow, 0.93f, 0.07f, 18f, 200f, 16f, 14f),
        Doodle(DoodleType.Heart, p.Red, 0.27f, 0.14f, 58f, -180f, 24f, -8f),
        Doodle(DoodleType.Star, p.Yellow, 0.44f, 0.14f, 24f, 240f, 32f, 10f),
        Doodle(DoodleType.Arrow, p.Green, 0.05f, 0.19f, 42f, 160f, 40f, -22f),
        Doodle(DoodleType.Flower, p.SkyBlue, 0.51f, 0.20f, 18f, -200f, 48f, 0f),
        Doodle(DoodleType.Flower, p.SkyBlue, 0.58f, 0.19f, 18f, 200f, 54f, 0f),
        Doodle(DoodleType.Flower, p.SkyBlue, 0.65f, 0.20f, 18f, -200f, 60f, 0f),
        Doodle(DoodleType.Dot, p.SkyBlue, 0.20f, 0.22f, 18f, 160f, 66f, 0f),
        Doodle(DoodleType.Ring, p.SkyBlue, 0.36f, 0.26f, 44f, 220f, 72f, -10f),
        Doodle(DoodleType.CoinTilt, p.BrandYellow, 0.59f, 0.27f, 46f, -220f, 78f, 12f),
        Doodle(DoodleType.Star, p.GoldLight, 0.77f, 0.24f, 20f, 180f, 84f, 8f),
        Doodle(DoodleType.Dot, p.Green, 0.93f, 0.22f, 14f, 160f, 90f, 0f),
        Doodle(DoodleType.Flower, p.Blue, 0.97f, 0.17f, 42f, -180f, 96f, 16f),
        // ---- ردیفِ میانی (وسط خالی) ----
        Doodle(DoodleType.Blob, p.Green, 0.08f, 0.34f, 80f, 140f, 104f, -6f),
        Doodle(DoodleType.Chip, p.BrandYellow, 0.82f, 0.20f, 34f, -180f, 110f, -8f),
        Doodle(DoodleType.Rocket, p.Orange, 0.30f, 0.37f, 40f, -240f, 118f, 18f),
        Doodle(DoodleType.Gem, p.SkyBlue, 0.70f, 0.38f, 40f, -200f, 126f, 6f),
        Doodle(DoodleType.Blob, p.GoldLight, 0.93f, 0.33f, 78f, 160f, 134f, 8f),
        Doodle(DoodleType.Chart, p.BrandYellow, 0.14f, 0.44f, 34f, 200f, 142f, 10f),
        Doodle(DoodleType.Star, p.Yellow, 0.27f, 0.46f, 30f, -220f, 150f, -12f),
        Doodle(DoodleType.Sparkle, p.GoldLight, 0.46f, 0.43f, 16f, 180f, 156f, 0f),
        // ---- ردیفِ پایین ----
        Doodle(DoodleType.Blob, p.Blue, 0.04f, 0.52f, 52f, 160f, 164f, -10f),
        Doodle(DoodleType.Star, p.Orange, 0.90f, 0.48f, 40f, 220f, 172f, 14f),
        Doodle(DoodleType.Leaf, p.Green, 0.20f, 0.61f, 46f, -200f, 180f, 8f),
        Doodle(DoodleType.Lock, p.GoldLight, 0.80f, 0.55f, 44f, -220f, 188f, 10f),
        Doodle(DoodleType.Flower, p.Yellow, 0.97f, 0.62f, 46f, 180f, 196f, -14f),
        Doodle(DoodleType.Gear, p.BrandYellow, 0.62f, 0.54f, 32f, -200f, 204f, 12f),
        Doodle(DoodleType.Plane, p.BrandYellow, 0.34f, 0.56f, 30f, 200f, 212f, -16f),
        Doodle(DoodleType.Dot, p.SkyBlue, 0.75f, 0.65f, 16f, 160f, 220f, 0f),
        Doodle(DoodleType.Sparkle, p.GoldLight, 0.25f, 0.53f, 14f, 180f, 228f, 0f),
        Doodle(DoodleType.Sparkle, p.GoldLight, 0.74f, 0.42f, 14f, -180f, 236f, 0f)
    )
}

private fun buildCoins(): List<FallingCoin> {
    val p = IntroPalette
    return listOf(
        FallingCoin(p.Gold, 0.52f, 0.02f, 36f, 300f, 0f),
        FallingCoin(p.GoldLight, 0.62f, -0.04f, 30f, -260f, 90f),
        FallingCoin(p.Gold, 0.44f, 0.05f, 26f, 240f, 170f),
        FallingCoin(p.GoldLight, 0.70f, -0.02f, 32f, -300f, 250f),
        FallingCoin(p.Gold, 0.38f, 0.03f, 24f, 220f, 330f)
    )
}


// فقط برای تستِ parse بودنِ pathهای txt — بعد از تأیید، دور انداخته می‌شود
@Composable
private fun ParseProbe() {
    val path = remember {
        runCatching {
            PathParser().parsePathString(
                "M676.707 434C708.581 434.974 708.572 480.83 676.707 481.805" +
                        "C644.833 480.83 644.842 434.974 676.707 434Z"
            ).toPath()
        }.getOrNull()
    }
    Canvas(Modifier.fillMaxSize().background(Color.White)) {
        if (path != null) {
            val b = path.getBounds()
            withTransform({
                // normalize: مرکزِ bounds بیاد روی مرکزِ canvas، مقیاس به ۱۲۰px
                val sc = 120f / max(b.width, b.height)
                scale(sc, sc); translate(-b.center.x, -b.center.y)
                translate(size.width / 2f / sc, size.height / 2f / sc)
            }) { drawPath(path, Color(0xFFFF9F45)) }
        }
    }
}

@Preview(name = "Welcome Intro - Dark", showBackground = true, heightDp = 780)
@Composable
private fun WelcomeIntroDarkPreview() {
    MegaWalletTheme(darkTheme = true) { /*WelcomeIntroScreen(onCreateWallet = {}, onConnectWallet = {})*/
        ParseProbe()
    }
}
@Preview(name = "Welcome Intro - Light", showBackground = true, heightDp = 780)
@Composable
private fun WelcomeIntroLightPreview() {
    MegaWalletTheme(darkTheme = false) { WelcomeIntroScreen(onCreateWallet = {}, onConnectWallet = {}) }
}