package com.mtd.domain.model.assets

import java.math.BigDecimal

/** از کدام منبع پاسخ آمد — `catalog` (کیوریت‌شده)، `mirror` (فهرستِ آینه‌ای)، `onchain` (خوانده از قرارداد). */
enum class TokenSource { CATALOG, MIRROR, ONCHAIN, UNKNOWN }

/**
 * توکنی که از سرویسِ کشفِ توکن آمده — فهرستِ پیش‌فرض، `/tokens/held`، `/tokens/search` یا
 * `/tokens/resolve`.
 *
 * این مدلِ **گذرا**ی نتیجهٔ جست‌وجوست، نه چیزی که ذخیره می‌شود؛ وقتی کاربر یکی را اضافه کرد به
 * [UserToken] تبدیل و ذخیره می‌شود.
 */
data class DiscoveredToken(
    /**
     * شناسهٔ توکن در کاتالوگِ ما، یا `null` اگر در کاتالوگ نباشد.
     *
     * `null` یعنی این توکن فقط با `contractAddress` قابل استفاده است — برای موجودی و انتقال
     * مجاز، ولی هرگز برای مسیرِ gasless (فهرستِ gasless جداگانه و curated است).
     */
    val catalogId: String?,
    val networkId: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    /**
     * **دقیقاً همان‌طور که سرور فرستاده.** هرگز lowercase نشود: base58ِ ترون حساس به حروف است و
     * شکلِ lowercase آن قابلِ بازسازی نیست، پس هر خواندنِ on-chain با آن شکست می‌خورد. یکتاسازیِ
     * محلی از [dedupeKey] استفاده می‌کند که کپیِ خودش را lowercase می‌کند.
     */
    val contractAddress: String,
    val iconUrl: String?,
    /** توکنِ curated که سرور به‌صورت پیش‌فرض پیشنهاد می‌دهد. */
    val featured: Boolean,
    /**
     * **نشانِ منشأ، نه نشانِ ایمنی.** `true` یعنی فهرستی که به آن اتکا می‌کنیم این قرارداد را روی
     * این شبکه منتشر کرده — نه ممیزی، نه تأییدِ مالی، نه توصیهٔ سرمایه‌گذاری.
     *
     * `null` با `false` یکی نیست: `null` یعنی سرور چیزی نگفته (نه نشان، نه هشدار). `false` یعنی
     * هیچ فهرستی منتشرش نکرده — که برای یک توکنِ واقعیِ تازه یا کم‌مخاطب هم دقیقاً همین‌طور است،
     * پس فقط هشدارِ خنثی می‌گیرد و هرگز پنهان یا مسدود نمی‌شود.
     */
    val verified: Boolean?,
    /** رتبهٔ ارزشِ بازار. `0`/`null` یعنی **بی‌رتبه**، نه رتبهٔ صفر — این‌ها باید آخر بیایند. */
    val marketCapRank: Int?,
    /**
     * `null` **عمدی** است، نه صفر: سرور قیمت‌های نامعتبرِ upstream را حذف می‌کند. صفر گرفتنش یعنی
     * دارایی کاربر بی‌صدا از مجموعِ سبد حذف می‌شود.
     */
    val priceUsd: BigDecimal?,
    /** `false` ⇒ قابلِ تبدیل و انتقال، ولی هرگز مسیرِ gasless/sponsor. */
    val curated: Boolean?,
    val source: TokenSource
) {
    /** کلیدِ یکتاسازی بین منابعِ لیست (پیش‌فرض / held / کاتالوگ / نتایجِ جست‌وجو). */
    val dedupeKey: String get() = "$networkId:${contractAddress.lowercase()}"

    /** رتبهٔ مؤثر برای مرتب‌سازی — بی‌رتبه‌ها ته فهرست. ترتیبِ خودِ سرور مقدم است. */
    val effectiveRank: Int get() = marketCapRank?.takeIf { it > 0 } ?: Int.MAX_VALUE
}
