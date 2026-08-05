package com.mtd.domain.model.assets

/**
 * توکنی که از سرویسِ کشفِ توکن آمده — `/tokens/held` یا `/tokens/search`.
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
    val contractAddress: String,
    val iconUrl: String?,
    /** توکنِ curated که سرور به‌صورت پیش‌فرض پیشنهاد می‌دهد. */
    val featured: Boolean
) {
    /** کلیدِ یکتاسازی بین سه منبعِ لیست (held / کاتالوگ / نتایجِ جست‌وجو). */
    val dedupeKey: String get() = "$networkId:${contractAddress.lowercase()}"
}
