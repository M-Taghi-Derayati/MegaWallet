package com.mtd.megawallet.ui.compose.screens.swap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.AssetIcon
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.NetworkIcon
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.components.AssetIconWithNetworkBadge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * آیکونِ ارز در فلوی تبدیل.
 *
 * فقط اندازه را می‌بندد و بقیه را به [AssetIcon] می‌سپارد: ترتیبِ منابع (drawableِ لوکال ← `iconUrl`
 * ← آواتارِ حرفی) و شش‌ضلعیِ قاب همان چیزی می‌ماند که لیستِ دارایی و صفحهٔ ارسال دارند. پیش از این
 * این‌جا یک `AsyncImage`ِ دایره‌ایِ جدا بود، پس یک توکن در دو صفحه دو شکلِ متفاوت داشت.
 */
@Composable
fun SwapAssetLogo(
    iconUrl: String?,
    symbol: String,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    AssetIcon(
        iconUrl = iconUrl,
        symbol = symbol,
        contentDescription = contentDescription,
        modifier = modifier.size(size)
    )
}

/** آیکونِ شبکه؛ همتای [SwapAssetLogo] برای `networkIconUrl`. */
@Composable
fun SwapNetworkLogo(
    iconUrl: String?,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    NetworkIcon(
        iconUrl = iconUrl,
        contentDescription = contentDescription,
        modifier = modifier.size(size)
    )
}

// ── عنصرِ اشتراکی و پروازِ FLIP ───────────────────────────────────────────────

/** جایگاه‌هایی که یک آیکونِ اشتراکی می‌تواند در آن‌ها بنشیند. */
enum class SwapMorphSlot { PAY_PILL, RECEIVE_CARD, COIN_PAY, COIN_RECEIVE }

/**
 * یک هویتِ توکن که در کلِ فلو **یک** عنصر است و بین جایگاه‌ها پرواز می‌کند.
 *
 * دلیلِ وجودِ [pendingPlaced]: مقصدِ پرواز تا وقتی جایگاهِ مقصد اندازه‌گیری نشده معلوم نیست. پرواز
 * منتظرِ اولین `onGloballyPositioned`ِ مقصد می‌ماند و بعد از موقعیتِ قبلی به آن اسلاید می‌کند.
 */
class SwapMorphIcon {
    var iconUrl by mutableStateOf<String?>(null)

    /** نمادِ توکن؛ ورودیِ آواتارِ حرفی وقتی نه drawableِ لوکالی هست و نه `iconUrl` جواب می‌دهد. */
    var symbol by mutableStateOf("")

    /** شبکه‌ای که این توکن رویش است؛ بجِ گوشهٔ آیکون از این ساخته می‌شود و با آن پرواز می‌کند. */
    var networkIconUrl by mutableStateOf<String?>(null)
    var slot by mutableStateOf<SwapMorphSlot?>(null)
    var currentRect by mutableStateOf<Rect?>(null)
    val translation = Animatable(Offset.Zero, Offset.VectorConverter)
    val scale = Animatable(1f)
    private var pendingPlaced: CompletableDeferred<Rect>? = null

    internal fun onRect(rect: Rect) {
        currentRect = rect
        pendingPlaced?.takeIf { it.isActive }?.complete(rect)
    }

    /**
     * پروازِ آیکون به [target].
     *
     * اگر جایگاهِ مقصد در [SwapMotion.flightMillis] اندازه‌گیری نشد (یا انیمیشن خاموش است) آیکون
     * فقط جابه‌جا می‌شود؛ فلو هیچ‌وقت پشتِ یک انیمیشنِ ناتمام گیر نمی‌کند.
     */
    suspend fun flyTo(target: SwapMorphSlot, motion: SwapMotion) {
        val from = currentRect
        if (!motion.animated || from == null) {
            slot = target
            translation.snapTo(Offset.Zero)
            scale.snapTo(1f)
            return
        }

        val deferred = CompletableDeferred<Rect>()
        pendingPlaced = deferred
        slot = target
        val to = withTimeoutOrNull(FLIGHT_MEASURE_TIMEOUT_MS) { deferred.await() }
        pendingPlaced = null

        if (to == null) {
            translation.snapTo(Offset.Zero)
            scale.snapTo(1f)
            return
        }

        val startOffset = Offset(from.left - to.left, from.top - to.top)
        val startScale = if (to.width > 0f) from.width / to.width else 1f
        coroutineScope {
            launch {
                translation.snapTo(startOffset)
                translation.animateTo(Offset.Zero, motion.morph())
            }
            launch {
                scale.snapTo(startScale)
                scale.animateTo(1f, motion.morph())
            }
        }
        translation.snapTo(Offset.Zero)
        scale.snapTo(1f)
    }

    private companion object {
        const val FLIGHT_MEASURE_TIMEOUT_MS = 700L
    }
}

/**
 * فضای [slot] را همیشه نگه می‌دارد؛ آیکون را فقط وقتی می‌کشد که مالکِ آن باشد.
 *
 * چیزی که پرواز می‌کند **آیکون به‌علاوهٔ بجِ شبکه** است، نه فقط دایرهٔ ارز: در فلوی تبدیل هر توکن
 * فقط با شبکه‌اش معنی دارد (همان USDT روی سه زنجیره سه چیزِ متفاوت است) و بجْ همان‌جایی می‌نشیند
 * که در فهرستِ دارایی و صفحهٔ ارسال می‌نشیند. چون [AssetIconWithNetworkBadge] یک واحدِ اندازه‌گیری
 * است، محاسبهٔ FLIP هم بدونِ تغییر روی همین قابِ بزرگ‌تر کار می‌کند.
 */
@Composable
fun SwapMorphSlotHost(
    model: SwapMorphIcon,
    slot: SwapMorphSlot,
    size: Dp,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier.size(size * SLOT_CONTAINER_RATIO), contentAlignment = Alignment.Center) {
        if (model.slot == slot) {
            AssetIconWithNetworkBadge(
                iconUrl = model.iconUrl,
                symbol = model.symbol,
                networkIconUrl = model.networkIconUrl,
                contentDescription = contentDescription,
                iconSize = size,
                showBadge = model.networkIconUrl != null,
                modifier = Modifier
                    .onGloballyPositioned { model.onRect(it.boundsInRoot()) }
                    .graphicsLayer {
                        translationX = model.translation.value.x
                        translationY = model.translation.value.y
                        scaleX = model.scale.value
                        scaleY = model.scale.value
                    }
            )
        }
    }
}

/** بجْ از گوشهٔ آیکون بیرون می‌زند؛ همان نسبتی که [AssetIconWithNetworkBadge] برای قابش دارد. */
private val SLOT_CONTAINER_RATIO =
    WalletScreenConstants.ASSET_ICON_SIZE / WalletScreenConstants.ASSET_ICON_MAIN_SIZE

/** سکشنی از ستونِ واحد که بین فازها باز/بسته می‌شود. */
@Composable
fun SwapMorphSection(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val motion = LocalSwapMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(motion.fade(SwapMotion.FADE_MEDIUM, delayMs = 60)) +
            expandVertically(motion.morph(), expandFrom = Alignment.Top),
        exit = fadeOut(motion.fade(SwapMotion.FADE_FAST)) +
            shrinkVertically(motion.morph(SwapMotion.MORPH_EXIT), shrinkTowards = Alignment.Top)
    ) {
        content()
    }
}

// ── نشانگر اعتبارِ استعلام ────────────────────────────────────────────────────

/**
 * حلقهٔ باقی‌ماندهٔ اعتبارِ استعلام. [fraction] از ۱ به ۰ می‌رود؛ محاسبهٔ آن بیرون است تا تیکِ
 * ثانیه‌ای کلِ stateFlow را دوباره منتشر نکند.
 */
@Composable
fun SwapQuoteTimer(
    fraction: Float,
    secondsRemaining: Int,
    modifier: Modifier = Modifier
) {
    val expiring = fraction <= 0.34f
    val color = if (expiring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onTertiary

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(2.dp, color.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp * fraction.coerceIn(0f, 1f) + 2.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            text = " ${secondsRemaining}s",
            color = color,
            fontSize = 12.sp,
            fontFamily = InterMedium,
            modifier = Modifier.offset(y = (-1).dp)
        )
    }
}
