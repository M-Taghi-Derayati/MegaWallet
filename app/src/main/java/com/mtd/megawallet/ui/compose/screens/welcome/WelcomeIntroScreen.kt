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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.mtd.common_ui.theme.IranSansRegularMedium
import com.mtd.common_ui.theme.MegaWalletTheme
import kotlinx.coroutines.delay

/* ============================ زمان‌بندی ============================ */

/** کلِ ورودِ میدان. */
private const val FIELD_MS = 1_100f

/** مدتِ پروازِ هر المان از مرکز به جای خودش. */
private const val FLIGHT_MS = 620f

/** فاصلهٔ شروعِ المان‌های پشتِ سرِ هم. */
private const val STAGGER_MS = 26f

/** متن و دکمه‌ها. عمداً زودتر از پایانِ میدان می‌آیند تا دکمه‌ها معطل نمانند. */
private const val CONTENT_START_MS = 620L

/** مرکزی که همه از آن بیرون می‌زنند — کمی بالاتر از وسطِ صفحه. */
private const val ORIGIN_Y = 0.42f

private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val t = x - 1f
    return 1f + c3 * t * t * t + c1 * t * t
}

/**
 * صفحهٔ خوش‌آمد — میدانی از المان‌های برداری، و زیرش عنوان و دو راهِ ورود.
 *
 * ### حرکت
 * همه از یک نقطه در وسطِ بالا بیرون می‌زنند و با [easeOutBack] سرِ جایشان می‌نشینند، با
 * تأخیرِ پله‌ای تا پشتِ سرِ هم بیایند نه همه با هم. لایهٔ پشتی زودتر و آرام‌تر می‌آید تا
 * پس‌زمینه پیش از شکل‌ها جا بیفتد.
 */
@Composable
fun WelcomeIntroScreen(
    onCreateWallet: () -> Unit,
    onConnectWallet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clock = remember { Animatable(0f) }
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        clock.animateTo(1f, tween(FIELD_MS.toInt(), easing = LinearEasing))
    }
    LaunchedEffect(Unit) {
        delay(CONTENT_START_MS)
        contentVisible = true
    }

    val contentAnim by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(600),
        label = "intro_content"
    )
    val slidePx = with(LocalDensity.current) { 28.dp.toPx() }

    // پوستهٔ فعال، نه پوستهٔ سیستم — کاربر می‌تواند خلافِ گوشی انتخاب کرده باشد.
    val isLight = MaterialTheme.colorScheme.background.luminance() >= 0.5f

    // ترتیبِ رسم = ترتیبِ لایه‌ها: لکه‌ها پشت، ستاره‌ها بینشان، شکل‌ها رو.
    val ordered = remember { INTRO_BACKDROP + INTRO_SPARKS + INTRO_ELEMENTS }
    val painters = ordered.map { element ->
        val res = if (isLight) element.lightRes ?: element.res else element.res
        rememberVectorPainter(ImageVector.vectorResource(res))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = clock.value * FIELD_MS
            val ox = size.width * 0.5f
            val oy = size.height * ORIGIN_Y

            ordered.forEachIndexed { index, element ->
                val local = ((t - index * STAGGER_MS) / FLIGHT_MS).coerceIn(0f, 1f)
                if (local <= 0f) return@forEachIndexed

                val e = easeOutBack(local)
                // شفافیت چند برابر سریع‌تر از حرکت بالا می‌آید، وگرنه المان نیمه‌محو تا آخرِ
                // مسیر کشیده می‌شود و میدان کدر به نظر می‌رسد.
                val appear = (local * 4f).coerceIn(0f, 1f)

                drawElement(
                    painter = painters[index],
                    element = element,
                    x = lerp(ox, size.width * element.x, e),
                    y = lerp(oy, size.height * element.y, e),
                    // از چرخشِ بیشتر به چرخشِ نهایی می‌رسد، پس در مسیر می‌چرخد.
                    rotation = lerp(element.rotation - 40f, element.rotation, e),
                    scale = appear,
                    alpha = appear
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = contentAnim
                        translationY = (1f - contentAnim) * slidePx
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "رمز ارز ها\nتحت کنترل تو",
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = IranSansRegularMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "یک کیف پول جدید بسازید یا کیف پول موجود خود را اضافه کنید",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = IranSansRegularMedium
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = { if (contentVisible) onCreateWallet() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = CircleShape
                ) {
                    Text(
                        text = "ساخت کیف پول جدید",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontFamily = IranSansRegularMedium
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { if (contentVisible) onConnectWallet() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = CircleShape
                ) {
                    Text(
                        text = "من کیف پول دارم",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = IranSansRegularMedium
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * یک المان را حولِ مرکزِ خودش می‌کشد.
 *
 * `VectorPainter` از مبدأِ [DrawScope] شروع می‌کند، پس نیمِ اندازه جابه‌جا می‌شویم؛ بدونِ آن
 * `x`/`y` گوشهٔ بالا-چپ می‌شد و چرخش هم حولِ همان گوشه انجام می‌گرفت.
 */
private fun DrawScope.drawElement(
    painter: Painter,
    element: IntroElement,
    x: Float,
    y: Float,
    rotation: Float,
    scale: Float,
    alpha: Float
) {
    val h = element.sizeDp.dp.toPx()
    val w = h * painter.intrinsicSize.width / painter.intrinsicSize.height
    withTransform({
        translate(x, y)
        rotate(rotation, pivot = Offset.Zero)
        scale(scale, scale, pivot = Offset.Zero)
        translate(-w / 2f, -h / 2f)
    }) {
        with(painter) {
            draw(
                size = Size(w, h),
                alpha = alpha,
                colorFilter = element.tint?.let { ColorFilter.tint(it) }
            )
        }
    }
}

@Preview(name = "Welcome Intro — Light", showBackground = true, heightDp = 800)
@Composable
private fun WelcomeIntroLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        WelcomeIntroScreen(onCreateWallet = {}, onConnectWallet = {})
    }
}

@Preview(name = "Welcome Intro — Dark", showBackground = true, heightDp = 800)
@Composable
private fun WelcomeIntroDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        WelcomeIntroScreen(onCreateWallet = {}, onConnectWallet = {})
    }
}
