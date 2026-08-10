package com.mtd.common_ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import com.mtd.common_ui.R
import kotlin.math.abs


fun getLocalIconResId(symbol: String): Int {
    return when (symbol.uppercase()) {
        "BTC" -> R.drawable.ic_btc
        "ETH" -> R.drawable.ic_eth
        "BASE" -> R.drawable.ic_base
        "ARB" -> R.drawable.ic_arb
        "POL" -> R.drawable.ic_pol
        "USDT" -> R.drawable.ic_usdt
        "BNB", "tBNB" -> R.drawable.ic_bnb
        "USDC" -> R.drawable.ic_usdc
        "XRP" -> R.drawable.ic_xrp
        "DOGE" -> R.drawable.ic_doge
        "TRX" -> R.drawable.ic_trx
        else -> 0
    }
}

/** آخرین fallback وقتی نه URL جواب داد و نه drawableِ نماد وجود داشت. */
fun getPlaceholderIconResId(): Int = R.drawable.ic_wallet

/**
 * آیکونِ شبکه از `NetworkInfo.iconUrl` (networks.json یا باندلِ امضاشده).
 *
 * جایگزینِ `getNetworkIconResId(networkId)`ِ قدیمی که یک `when` روی فهرستِ هاردکدِ networkIdها بود
 * و شاخهٔ `else` آن یعنی هر زنجیرهٔ تازه‌ای آیکونِ عمومیِ کیف‌پول می‌گرفت.
 */
@Composable
fun NetworkIcon(
    iconUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val placeholder = painterResource(id = getPlaceholderIconResId())
    AsyncImage(
        model = iconUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
        imageLoader = LocalContext.current.imageLoader
    )
}

/**
 * آیکونِ ارز از `AssetItem.iconUrl` — همتای [NetworkIcon].
 *
 * سه لایه، به ترتیب:
 *  1. drawableِ لوکالِ نمادهای شناخته‌شده ([getLocalIconResId])؛
 *  2. خودِ `iconUrl`؛
 *  3. **آواتارِ حرفیِ ساخته‌شده از نماد** ([SymbolAvatar]).
 *
 * لایهٔ سوم دائمی است، نه موقتی: هیچ منبعی کلِ توکن‌ها را پوشش نمی‌دهد و بخشی از آن‌ها هرگز آیکون
 * نخواهند داشت (سنجشِ زنده: ۸٪ روی BSC، ۱۹٪ آربیتروم، ۲۸٪ اتریوم). قبلاً همهٔ این‌ها یک
 * drawableِ عمومیِ کیف‌پول می‌گرفتند، یعنی ده‌ها ردیفِ متفاوت که از هم قابلِ تشخیص نبودند.
 */
@Composable
fun AssetIcon(
    iconUrl: String?,
    symbol: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val localResId = remember(symbol) { getLocalIconResId(symbol) }

    if (localResId != 0) {
        val local = painterResource(id = localResId)
        AsyncImage(
            model = iconUrl,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            placeholder = local,
            error = local,
            fallback = local,
            imageLoader = LocalContext.current.imageLoader
        )
        return
    }

    // `iconUrl` می‌تواند **دائماً** خالی باشد؛ در آن حالت اصلاً درخواستی زده نمی‌شود.
    if (iconUrl.isNullOrBlank()) {
        SymbolAvatar(symbol = symbol, contentDescription = contentDescription, modifier = modifier)
        return
    }

    SubcomposeAsyncImage(
        model = iconUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        imageLoader = LocalContext.current.imageLoader,
        loading = { SymbolAvatar(symbol = symbol, contentDescription = null) },
        error = { SymbolAvatar(symbol = symbol, contentDescription = null) }
    )
}

/**
 * آواتارِ دایره‌ایِ حرفی از نماد — جایگزینِ آیکونِ نبوده.
 *
 * رنگ از خودِ نماد مشتق می‌شود، پس برای یک توکن همیشه یکسان است و دو توکنِ متفاوت معمولاً دو رنگ
 * می‌گیرند؛ همین چیزی است که ردیف‌های بی‌آیکون را از هم قابلِ تشخیص می‌کند. پالت ثابت و از قبل
 * سنجیده است تا در هر دو تمِ روشن و تیره خوانا بماند — رنگِ تصادفیِ کامل گاهی متنِ ناخوانا می‌سازد.
 *
 * اندازهٔ قلم از عرضِ واقعیِ کامپوز مشتق می‌شود، چون این کامپوزبل از ۱۲dp (کنارِ نامِ شبکه) تا ۴۰dp
 * (لیستِ کیف پول) استفاده می‌شود و اندازهٔ ثابت در یک سرِ این بازه خراب می‌شود.
 */
@Composable
fun SymbolAvatar(
    symbol: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val initials = remember(symbol) { symbol.toAvatarInitials() }
    val background = remember(symbol) { avatarColorFor(symbol) }

    BoxWithConstraints(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // ۰٫۴ عرض ⇒ دو حرف با حاشیه جا می‌شود؛ زیر ~۱۴dp متن ناخواناست و فقط دایرهٔ رنگی می‌ماند،
        // که در آن اندازه‌ها (آیکونِ کنارِ نامِ شبکه) هم دقیقاً کارِ لازم را می‌کند.
        if (maxWidth.value >= MIN_WIDTH_FOR_TEXT_DP) {
            Text(
                text = initials,
                color = AVATAR_TEXT_COLOR,
                fontFamily = InterRegular,
                fontSize = (maxWidth.value * 0.4f).sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

private const val MIN_WIDTH_FOR_TEXT_DP = 14f

/** روی هر هشت رنگِ پالت کنتراستِ کافی دارد. */
private val AVATAR_TEXT_COLOR = Color(0xFFFFFFFF)

/**
 * پالتِ ثابتِ آواتار. اشباع و روشناییِ همه در یک بازه است تا هیچ‌کدام در تمِ روشن محو نشوند و
 * هیچ‌کدام در تمِ تیره نزنند.
 */
private val AVATAR_PALETTE = listOf(
    Color(0xFF5B7FDB),
    Color(0xFF3FA796),
    Color(0xFFB4795B),
    Color(0xFF8C6BC8),
    Color(0xFFC26B8A),
    Color(0xFF4E9A5B),
    Color(0xFFCC8A3D),
    Color(0xFF5E8CA8)
)

/**
 * `hashCode` نمادِ نرمال‌شده. `abs` روی [Int.MIN_VALUE] خودش منفی می‌ماند، پس اول
 * `toLong` — وگرنه یک نمادِ خاص باعثِ ایندکسِ منفی و کرش می‌شد.
 */
private fun avatarColorFor(symbol: String): Color {
    val key = symbol.trim().uppercase().ifEmpty { "?" }
    val index = (abs(key.hashCode().toLong()) % AVATAR_PALETTE.size).toInt()
    return AVATAR_PALETTE[index]
}

/**
 * تا دو حرفِ اولِ **حرف-عددیِ** نماد. کاراکترهای تزیینی (`$`، `.`، فاصله) کنار می‌روند، چون
 * نمادهای دنبالهٔ بلند پر از آن‌ها هستند و یک آواتارِ «$» چیزی را از چیزی جدا نمی‌کند.
 */
private fun String.toAvatarInitials(): String {
    val letters = trim().uppercase().filter { it.isLetterOrDigit() }
    return when {
        letters.isEmpty() -> "?"
        else -> letters.take(2)
    }
}
