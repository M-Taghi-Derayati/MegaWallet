package com.mtd.data.dto

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/**
 * Relayer unified market-price feed — `GET /api/v1/prices?symbols=...`
 * (ANDROID_API_CONTRACT §1.6 / PriceFeedService §2). The server aggregates
 * CoinDesk → CryptoCompare → CoinGecko with **per-symbol failure isolation** and adds IRR.
 *
 * Every field is nullable with NO crashing default: this endpoint has had zero production
 * validation from the Android client, and per the contract a symbol resolves to either
 * `{usd, irr, source, fetchedAt}` OR `{error}`. `usd`/`irr` are JSON **numbers** on the wire
 * (e.g. `3500.12`) — BigDecimal decodes them directly (the BigInteger string-codec does not apply).
 */
data class RelayerPricesResponseDto(
    @SerializedName("quoteSymbol") val quoteSymbol: String? = null,
    @SerializedName("usdToIrr") val usdToIrr: BigDecimal? = null,
    @SerializedName("fetchedAt") val fetchedAt: Long? = null,
    @SerializedName("prices") val prices: Map<String, RelayerPriceEntryDto>? = null
)

data class RelayerPriceEntryDto(
    @SerializedName("usd") val usd: BigDecimal? = null,
    @SerializedName("irr") val irr: BigDecimal? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("fetchedAt") val fetchedAt: Long? = null,
    // 24h change %: the §1.6 sample does NOT show this field, so its real name is UNVERIFIED against
    // the live body. The alternates cover the plausible server spellings; if absent it stays null and
    // the UI simply renders no change badge (never crashes). Confirm against a real /api/v1/prices body.
    @SerializedName(
        value = "change24h",
        alternate = ["changePercent24h", "priceChange24h", "usd24hChange", "changePct24h", "change"]
    )
    val change24h: BigDecimal? = null,
    // Per-symbol failure isolation — a non-null error means this symbol carries no price this round.
    @SerializedName("error") val error: String? = null
) {
    /** True only when this symbol resolved to a usable price (no error, non-null usd). */
    val hasPrice: Boolean get() = error == null && usd != null
}
