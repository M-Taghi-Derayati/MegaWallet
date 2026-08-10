package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

/** بدنهٔ `POST /api/mobile/v1/networks/:networkId/tokens/held`. */
data class HeldTokensRequestDto(
    @SerializedName("address") val address: String
)

/**
 * `data` مشترکِ همهٔ اندپوینت‌های کشفِ توکن — فهرستِ پیش‌فرض، per-network (held/search) و
 * cross-network (search/resolve).
 */
data class TokenListDto(
    @SerializedName("count") val count: Int? = null,
    /** چندتای این آرایه واقعاً «دارایی کاربر» بودند — فقط اطلاعاتی. */
    @SerializedName("held") val held: Int? = null,
    @SerializedName("tokens") val tokens: List<DiscoveredTokenDto>? = null,
    /**
     * نامِ جایگزینی که ممکن است `/tokens/resolve/{address}` برای همان آرایه استفاده کند.
     *
     * Gson کلیدِ ناشناخته را بی‌صدا دور می‌اندازد، پس اگر سرور `matches` بفرستد و ما فقط `tokens`
     * را بشناسیم نتیجه «آرایهٔ خالی» می‌شود — که از «هیچ زنجیره‌ای این آدرس را ندارد» قابلِ
     * تشخیص نیست و باعثِ ردِ اشتباهِ یک آدرسِ معتبر می‌شود. [items] هر دو را می‌پذیرد.
     */
    @SerializedName("matches") val matches: List<DiscoveredTokenDto>? = null
) {
    val items: List<DiscoveredTokenDto> get() = tokens ?: matches.orEmpty()
}

/**
 * `data` ی `GET /networks/:networkId/tokens/resolve/:contractAddress` — یک توکن، نه آرایه.
 *
 * هر دو شکل پذیرفته می‌شود چون این مسیر با مسیرِ cross-networkِ هم‌نام یک خانواده است و شکلِ
 * دقیقِ پاسخ نباید تفاوتِ «پیدا نشد» با «پارس نشد» را از بین ببرد.
 */
data class TokenResolveDto(
    @SerializedName("token") val token: DiscoveredTokenDto? = null,
    @SerializedName("tokens") val tokens: List<DiscoveredTokenDto>? = null
) {
    val item: DiscoveredTokenDto? get() = token ?: tokens?.firstOrNull()
}

data class DiscoveredTokenDto(
    /** `null` یعنی این توکن در کاتالوگِ ما نیست و فقط با `contractAddress` قابلِ استفاده است. */
    @SerializedName("id") val id: String? = null,
    /**
     * اندپوینت‌های cross-network این را می‌فرستند؛ مسیرهای per-network ممکن است حذفش کنند چون
     * از قبل در URL آمده. هرگز فرض نکنید فراخوان شبکه را می‌داند — یک نتیجهٔ
     * `/tokens/search` سراسری می‌تواند از هر زنجیره‌ای باشد.
     */
    @SerializedName("networkId") val networkId: String? = null,
    /** نامِ نمایشیِ شبکه در پاسخ‌های cross-network. */
    @SerializedName("networkName") val networkName: String? = null,
    @SerializedName("symbol") val symbol: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("decimals") val decimals: Int? = null,
    @SerializedName("contractAddress") val contractAddress: String? = null,
    /** ممکن است **دائماً** خالی باشد — هیچ فهرستی دنبالهٔ بلندِ توکن‌ها را پوشش نمی‌دهد. */
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("faName") val faName: String? = null,
    @SerializedName("featured") val featured: Boolean? = null,
    /** نشانِ **منشأ**؛ نه ممیزی، نه تأییدِ مالی. `null` یعنی سرور چیزی نگفته. */
    @SerializedName("verified") val verified: Boolean? = null,
    /** `0` یعنی بی‌رتبه، نه رتبهٔ صفر. */
    @SerializedName("marketCapRank") val marketCapRank: Int? = null,
    /** رشتهٔ decimal یا `null`. `null` عمدی است — پول هرگز به‌صورت float پارس نمی‌شود. */
    @SerializedName("priceUsd") val priceUsd: String? = null,
    /** `false` ⇒ قابلِ تبدیل و انتقال، ولی هرگز gasless/sponsor. */
    @SerializedName("curated") val curated: Boolean? = null,
    /** `catalog` | `mirror` | `onchain`. */
    @SerializedName("source") val source: String? = null
)

/** بدنهٔ `POST /api/mobile/v1/tokens/prices` — حداکثر ۱۰۰ عنصر. */
data class TokenPricesRequestDto(
    @SerializedName("tokens") val tokens: List<TokenPriceQueryDto>
)

data class TokenPriceQueryDto(
    @SerializedName("networkId") val networkId: String,
    /** ⚠️ عیناً همان‌طور که داریمش — base58ِ ترون حساس به حروف است. */
    @SerializedName("address") val address: String,
    @SerializedName("symbol") val symbol: String? = null
)

/**
 * `data` ی `POST /tokens/prices`. کلیدهای [prices] به شکلِ `"{networkId}:{address}"` هستند —
 * EVM با حروفِ کوچک، ترون عیناً همان‌طور که فرستادیم.
 *
 * [missing]ِ ناخالی **عادی** است و شکستِ درخواست نیست.
 */
data class TokenPricesDto(
    @SerializedName("quoteSymbol") val quoteSymbol: String? = null,
    @SerializedName("prices") val prices: Map<String, String>? = null,
    @SerializedName("missing") val missing: List<String>? = null,
    @SerializedName("sources") val sources: List<String>? = null
)
