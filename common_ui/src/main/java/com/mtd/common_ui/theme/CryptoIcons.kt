package com.mtd.common_ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import com.mtd.common_ui.R
import kotlin.math.abs
import kotlin.math.min


/**
 * drawableِ باندل‌شدهٔ نمادهای شناخته‌شده.
 *
 * ⚠️ این **fallback** است، نه منبعِ آیکون. منبع همیشه `iconUrl`ِ کاتالوگ است — و کاتالوگ خودش
 * را از باندلِ امضاشدهٔ سرور می‌گیرد، پس عوض‌کردنِ آیکونِ یک ارز نیازی به انتشارِ نسخه ندارد.
 * این فهرست فقط برای وقتی است که URL شکست بخورد یا اصلاً وجود نداشته باشد.
 */
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
fun getPlaceholderIconResId(): Int = R.drawable.ic_pls

/**
 * همان شش‌ضلعیِ `ic_pls`، این بار به‌شکلِ [Shape] تا بشود با آن **برش** زد و نه فقط پشتِ چیزی کشید.
 *
 * تا امروز این فرم فقط زیرِ بجِ شبکه (لیستِ دارایی‌ها) به‌عنوان یک drawable کشیده می‌شد، پس هر جای
 * دیگری که آیکون داشتیم دایره بود — دو زبانِ فرمیِ متفاوت در یک صفحه. با [Shape] شدن، همان مسیرِ
 * برداری هم قابِ آیکونِ ارز می‌شود و هم پس‌زمینهٔ [SymbolAvatar]، و کلِ سیستمِ آیکون یک‌دست می‌شود.
 *
 * مسیر عیناً از `res/drawable/ic_pls.xml` برداشته شده و در viewportِ ۴۵×۴۰ تعریف است؛ این‌جا **با
 * حفظِ نسبت** داخلِ اندازهٔ واقعی fit و وسط‌چین می‌شود — دقیقاً کاری که `Icon` با همان drawable
 * می‌کند، وگرنه در یک باکسِ مربع شش‌ضلعی کشیده و بدریخت می‌شد.
 */
object HexAssetShape : Shape {

    private const val VIEWPORT_WIDTH = 45f
    private const val VIEWPORT_HEIGHT = 40f

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val scale = min(size.width / VIEWPORT_WIDTH, size.height / VIEWPORT_HEIGHT)
        val offsetX = (size.width - VIEWPORT_WIDTH * scale) / 2f
        val offsetY = (size.height - VIEWPORT_HEIGHT * scale) / 2f

        fun x(value: Float) = offsetX + value * scale
        fun y(value: Float) = offsetY + value * scale

        val path = Path().apply {
            moveTo(x(30.067f), y(0.086f))
            cubicTo(x(32.187f), y(0.098f), x(34.145f), y(1.228f), x(35.215f), y(3.058f))
            lineTo(x(43.347f), y(16.972f))
            cubicTo(x(44.435f), y(18.833f), x(44.441f), y(21.134f), x(43.364f), y(23f))
            lineTo(x(35.445f), y(36.715f))
            cubicTo(x(34.368f), y(38.582f), x(32.372f), y(39.726f), x(30.217f), y(39.715f))
            lineTo(x(14.101f), y(39.628f))
            cubicTo(x(11.981f), y(39.616f), x(10.024f), y(38.486f), x(8.953f), y(36.655f))
            lineTo(x(0.82f), y(22.743f))
            cubicTo(x(-0.267f), y(20.882f), x(-0.274f), y(18.581f), x(0.804f), y(16.715f))
            lineTo(x(8.722f), y(3f))
            cubicTo(x(9.8f), y(1.133f), x(11.796f), y(-0.012f), x(13.951f), y(0f))
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * آیکونِ شبکه از `NetworkInfo.iconUrl`.
 *
 * جایگزینِ `getNetworkIconResId(networkId)`ِ قدیمی که یک `when` روی فهرستِ هاردکدِ networkIdها بود
 * و شاخهٔ `else` آن یعنی هر زنجیرهٔ تازه‌ای آیکونِ عمومیِ کیف‌پول می‌گرفت.
 *
 * مثلِ [AssetIcon] منبعِ حقیقت خودِ URL است و `ic_pls` فقط قابِ خنثیِ لحظهٔ بارگذاری و خطاست —
 * عمداً یک آیکونِ *دیگر* نیست، وگرنه در گذار دو تصویرِ متفاوت برای یک شبکه دیده می‌شد.
 *
 * ⚠️ برخلافِ [AssetIcon] لایهٔ drawableِ لوکال را ندارد، چون فراخوان‌ها فقط URL را دارند و نه
 * `currencySymbol`. یعنی اگر URLِ یک شبکه بیفتد این‌جا شش‌ضلعیِ خالی می‌شود ولی همان زنجیره در
 * لیستِ دارایی‌ها drawableِ خودش را نشان می‌دهد. برای رفعش باید `currencySymbol` تا این‌جا
 * برسد — کارِ جدا و پرتماس با ۱۴ فراخوان.
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
 * ### ترتیبِ منابع (یکی، و همیشه همین)
 *  1. خودِ `iconUrl` — **منبعِ حقیقت**؛
 *  2. drawableِ لوکال ([getLocalIconResId])، فقط وقتی URL شکست بخورد یا اصلاً وجود نداشته باشد؛
 *  3. آواتارِ حرفیِ ساخته‌شده از نماد ([SymbolAvatar]).
 *
 * ⚠️ لایهٔ ۲ عمداً **placeholder نیست**. تا دیروز برای نمادهای شناخته‌شده drawableِ لوکال به‌عنوان
 * `placeholder` پاس می‌شد، یعنی کاربر اول آیکونِ داخلِ اپ را می‌دید و بعد با تصویرِ سرور عوض
 * می‌شد — و چون Coil کش دارد، این «گاهی این، گاهی آن» بود نه یک رفتارِ ثابت. حالا لحظهٔ
 * بارگذاری یک قابِ خنثی است و هیچ‌وقت دو آیکونِ متفاوت برای یک ارز پشتِ سر هم دیده نمی‌شود.
 *
 * لایهٔ سوم دائمی است، نه موقتی: هیچ منبعی کلِ توکن‌ها را پوشش نمی‌دهد و بخشی از آن‌ها هرگز آیکون
 * نخواهند داشت (سنجشِ زنده: ۸٪ روی BSC، ۱۹٪ آربیتروم، ۲۸٪ اتریوم). قبلاً همهٔ این‌ها یک
 * drawableِ عمومیِ کیف‌پول می‌گرفتند، یعنی ده‌ها ردیفِ متفاوت که از هم قابلِ تشخیص نبودند.
 *
 * هر سه لایه با [HexAssetShape] بریده می‌شوند تا شکلِ بیرونی مستقل از اینکه کدام لایه جواب داده
 * یکی بماند — همان شش‌ضلعیِ بجِ شبکه.
 */
@Composable
fun AssetIcon(
    iconUrl: String?,
    symbol: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val localResId = remember(symbol) { getLocalIconResId(symbol) }

    // `iconUrl` می‌تواند **دائماً** خالی باشد؛ در آن حالت اصلاً درخواستی زده نمی‌شود.
    if (iconUrl.isNullOrBlank()) {
        AssetIconFallback(
            localResId = localResId,
            symbol = symbol,
            contentDescription = contentDescription,
            modifier = modifier
        )
        return
    }

    SubcomposeAsyncImage(
        model = iconUrl,
        contentDescription = contentDescription,
        modifier = modifier.clip(HexAssetShape),
        contentScale = ContentScale.Fit,
        imageLoader = LocalContext.current.imageLoader,
        // `fillMaxSize` لازم است: اسلاتِ SubcomposeAsyncImage محتوا را wrap می‌کند، پس بدونِ آن
        // آواتار به اندازهٔ متنش جمع می‌شد و لحظهٔ بارگذاری یک شش‌ضلعیِ ریز وسطِ جای خالی می‌ماند.
        //
        // لحظهٔ بارگذاری عمداً آواتار است و نه drawableِ لوکال — به دلیلی که بالا آمد.
        loading = {
            SymbolAvatar(symbol = symbol, contentDescription = null, modifier = Modifier.fillMaxSize())
        },
        error = {
            AssetIconFallback(
                localResId = localResId,
                symbol = symbol,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}

/** لایه‌های ۲ و ۳: drawableِ لوکال اگر بود، وگرنه آواتارِ حرفی. */
@Composable
private fun AssetIconFallback(
    localResId: Int,
    symbol: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    if (localResId != 0) {
        Image(
            painter = painterResource(id = localResId),
            contentDescription = contentDescription,
            modifier = modifier.clip(HexAssetShape),
            contentScale = ContentScale.Fit
        )
    } else {
        SymbolAvatar(symbol = symbol, contentDescription = contentDescription, modifier = modifier)
    }
}

/**
 * آواتارِ حرفی از نماد — جایگزینِ آیکونِ نبوده.
 *
 * فرمش همان شش‌ضلعیِ [HexAssetShape] است و نه دایره: این کامپوزبل کنارِ آیکون‌های واقعیِ ارز
 * می‌نشیند و تا وقتی دایره بود، «آیکون نداریم» یک تفاوتِ فرمیِ چشمگیر می‌ساخت — چیزی که کاربر
 * به‌عنوانِ تفاوتِ **معنایی** می‌خواند. حالا فقط محتوای قاب فرق می‌کند، نه خودِ قاب.
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
            .clip(HexAssetShape)
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
