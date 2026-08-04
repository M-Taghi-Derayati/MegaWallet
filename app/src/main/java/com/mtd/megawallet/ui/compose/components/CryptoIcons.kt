package com.mtd.megawallet.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.imageLoader
import com.mtd.megawallet.R

/**
 * تنها خانهٔ آیکونِ ارز و شبکه در کل اپ.
 *
 * پیش از این [NetworkIcon]/[AssetIcon] داخل `WalletScreen.kt` بودند و ده فایل از پکیج‌های دیگر
 * (send، swap، history، components) از یک *صفحه* import می‌کردند. نتیجه‌اش این بود که هر تغییرِ
 * رفتارِ آیکون — اولویتِ URL بر drawable، placeholder، ImageLoader — باید در چند جا تکرار می‌شد و
 * عملاً هم نمی‌شد؛ همان چیزی که باعث شد آیکونِ باندلِ سرور در بعضی صفحه‌ها هرگز دیده نشود.
 *
 * قاعده در هر دو: **URL مسیرِ اصلی است، drawableِ لوکال فقط placeholder/fallback.** هر آیکونی که
 * سرور در باندلِ امضاشده اضافه کند بدون آپدیتِ اپ نمایش داده می‌شود.
 *
 * `LocalContext.current.imageLoader` عمداً استفاده شده تا لودرِ پیکربندی‌شدهٔ
 * `MegaWalletApplication` بیاید (کلاینتِ OkHttp اپ + SvgDecoder + disk cache). ساختنِ
 * `ImageLoader(context)` محلی این‌ها را از دست می‌دهد و آیکون‌های SVG بی‌صدا شکست می‌خورند.
 */

/** drawableِ لوکالِ نماد، یا `0` اگر نداشته باشیم. هرگز مستقیم به `painterResource` نده. */
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
 * آیکونِ ارز از `AssetItem.iconUrl` (assets.json یا باندلِ امضاشده) — همتای [NetworkIcon].
 *
 * [getLocalIconResId] فقط placeholder/fallbackِ آفلاینِ نمادهای شناخته‌شده است؛ اگر آن هم چیزی
 * نداشت drawableِ عمومی می‌نشیند، پس `painterResource(0)` هرگز صدا زده نمی‌شود.
 */
@Composable
fun AssetIcon(
    iconUrl: String?,
    symbol: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val localResId = remember(symbol) { getLocalIconResId(symbol) }
    val placeholder = painterResource(
        id = if (localResId != 0) localResId else getPlaceholderIconResId()
    )
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
