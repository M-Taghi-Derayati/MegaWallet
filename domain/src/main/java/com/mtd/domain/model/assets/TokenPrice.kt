package com.mtd.domain.model.assets

import java.math.BigDecimal

/**
 * یک درخواستِ قیمت بر اساسِ **آدرسِ قرارداد**.
 *
 * [address] دقیقاً همان‌طور که داریمش فرستاده می‌شود — بدونِ lowercase. برای EVM بی‌اثر است، برای
 * ترون تفاوتِ بینِ پاسخ و شکست.
 *
 * [symbol] اختیاری است و فقط fallbackِ آخرِ سرور را بهتر می‌کند.
 */
data class TokenPriceQuery(
    val networkId: String,
    val address: String,
    val symbol: String? = null
)

/**
 * پاسخِ `POST /api/mobile/v1/tokens/prices`.
 *
 * **پاسخِ ناقص عادی است.** هرچه در [missing] است قیمت ندارد — که با «قیمتِ صفر» یکی نیست، و
 * [missing]ِ ناخالی یعنی درخواست شکست نخورده.
 */
data class TokenPriceResult(
    /** کلید: `"{networkId}:{address}"` — سرور برای EVM lowercase و برای ترون عیناً برمی‌گرداند. */
    private val prices: Map<String, BigDecimal>,
    val missing: List<String>,
    val sources: List<String> = emptyList()
) {
    /**
     * قیمتِ یک قرارداد، یا `null` وقتی قیمتی نیست.
     *
     * تطبیق دو مرحله‌ای است و همین‌جا تنها جایی است که عدمِ تقارنِ EVM/TRON زندگی می‌کند: اول
     * کلیدِ عینی (ترون که base58ِ حساس‌به‌حروف است از این راه می‌آید)، بعد کلیدِ lowercase (EVM که
     * سرور آن را lowercase برمی‌گرداند ولی ما ممکن است شکلِ checksum را داشته باشیم). ترتیبِ
     * برعکس، یک آدرسِ ترون را که تصادفاً همه‌حروف‌کوچک است به کلیدِ اشتباه می‌بست.
     */
    fun priceFor(networkId: String, address: String): BigDecimal? =
        prices["$networkId:$address"] ?: prices["$networkId:${address.lowercase()}"]

    val isEmpty: Boolean get() = prices.isEmpty()

    companion object {
        val EMPTY = TokenPriceResult(prices = emptyMap(), missing = emptyList())

        /** سقفِ هر درخواست، سمتِ سرور. فراخوان باید chunk کند. */
        const val MAX_TOKENS_PER_REQUEST = 100
    }
}
