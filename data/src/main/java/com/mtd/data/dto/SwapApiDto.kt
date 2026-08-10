package com.mtd.data.dto

import com.google.gson.annotations.SerializedName
import java.math.BigInteger

/**
 * Phase 4 — Multi-route swap DTOs (`/api/v1/swap`). Raw-money fields (`gross`/`net`/`min`,
 * `platformCommission`, `grossOutput`/`netOutput`, gas `native`/`costInToToken`) are [BigInteger]
 * via the registered adapter. `tx.value` stays a String (hex quantity, not a base-unit amount).
 * Responses carry `ok:true` + DTO or `{ ok:false, error:{code,message} }`.
 */

// --- providers ---
data class SwapProvidersDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("providers") val providers: List<String>? = null,
    @SerializedName("platformFeeBps") val platformFeeBps: Int? = null,
    @SerializedName("platformFeeCollected") val platformFeeCollected: Boolean? = null,
    @SerializedName("quoteTtlMs") val quoteTtlMs: Long? = null
)

// --- quote ---
data class SwapQuoteResponseDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("requestId") val requestId: String? = null,
    @SerializedName("routes") val routes: List<SwapRouteDto>? = null,
    @SerializedName("bestRoute") val bestRoute: SwapRouteDto? = null,
    @SerializedName("platformFeeBps") val platformFeeBps: Int? = null,
    /** Whether the commission on the best route was really withheld on-chain. */
    @SerializedName("platformFeeCollected") val platformFeeCollected: Boolean? = null,
    @SerializedName("expiresAt") val expiresAt: String? = null,
    @SerializedName("ttlMs") val ttlMs: Long? = null,
    /** Echo of the inputs the server actually quoted for — see [SwapQuoteEchoDto]. */
    @SerializedName("request") val request: SwapQuoteEchoDto? = null
)

/**
 * The server's echo of the quote inputs. `toAddress` is the only reliable statement of **who gets
 * paid**: it may have been defaulted to `userAddress` server-side, or it may be the destination we
 * sent for a cross-VM bridge. It is read back rather than assumed so the recipient shown to the
 * user is the one the route was actually built for.
 */
data class SwapQuoteEchoDto(
    @SerializedName("userAddress") val userAddress: String? = null,
    @SerializedName("toAddress") val toAddress: String? = null
)

data class SwapRouteDto(
    @SerializedName("rank") val rank: Int? = null,
    @SerializedName("isBestReturn") val isBestReturn: Boolean? = null,
    @SerializedName("provider") val provider: String? = null,
    /** A route with [fromNetwork] != [toNetwork] is a bridge, not a swap. */
    @SerializedName("fromNetwork") val fromNetwork: String? = null,
    @SerializedName("toNetwork") val toNetwork: String? = null,
    /** Names the underlying bridge/DEX (`lifiIntents`, `near`, …) for display. */
    @SerializedName("tool") val tool: String? = null,
    /** The address **this route's** `tx` pays out to. Render it whenever it is not the sender. */
    @SerializedName("toAddress") val toAddress: String? = null,
    @SerializedName("toAmount") val toAmount: SwapToAmountDto? = null,
    @SerializedName("fees") val fees: SwapFeesDto? = null,
    @SerializedName("estimatedGas") val estimatedGas: SwapEstimatedGasDto? = null,
    @SerializedName("allowanceTarget") val allowanceTarget: String? = null,
    @SerializedName("tx") val tx: SwapTxDto? = null
)

data class SwapToAmountDto(
    @SerializedName("gross") val gross: BigInteger? = null,
    @SerializedName("net") val net: BigInteger? = null,
    @SerializedName("min") val min: BigInteger? = null
)

/**
 * ⚠️ [platformBps] is the *configured* rate, not proof that anything was charged — it is
 * operator-changeable at runtime and can differ between two quotes. Only [collected] says the
 * commission was really withheld on-chain; while it is `false`, [platformCommission] is `"0"` and
 * `toAmount.net == toAmount.gross`. [uncollectedCommission] is informational and must never be
 * subtracted from anything shown to the user.
 */
data class SwapFeesDto(
    @SerializedName("platformBps") val platformBps: Int? = null,
    @SerializedName("collected") val collected: Boolean? = null,
    @SerializedName("platformCommission") val platformCommission: BigInteger? = null,
    @SerializedName("uncollectedCommission") val uncollectedCommission: BigInteger? = null,
    @SerializedName("grossOutput") val grossOutput: BigInteger? = null,
    @SerializedName("netOutput") val netOutput: BigInteger? = null
)

data class SwapEstimatedGasDto(
    @SerializedName("native") val native: BigInteger? = null,
    @SerializedName("costInToToken") val costInToToken: BigInteger? = null,
    @SerializedName("costUsd") val costUsd: Double? = null
)

data class SwapTxDto(
    @SerializedName("to") val to: String? = null,
    @SerializedName("data") val data: String? = null,
    @SerializedName("value") val value: String? = null
)

// --- chains ---
data class SwapChainsDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("count") val count: Int? = null,
    @SerializedName("chains") val chains: List<SwapChainDto>? = null
)

data class SwapChainDto(
    @SerializedName("networkId") val networkId: String? = null,
    @SerializedName("chainId") val chainId: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("tokenCount") val tokenCount: Int? = null
)

// --- tokens ---
data class SwapTokensDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("networkId") val networkId: String? = null,
    @SerializedName("returned") val returned: Int? = null,
    @SerializedName("total") val total: Int? = null,
    @SerializedName("tokens") val tokens: List<SwapTokenDto>? = null
)

/**
 * `id: null` + `imported: true` ⇒ routable but **not** curated: swap/bridge/transfer only, never
 * the sponsored gasless path. `priceUsd` is a decimal *string* and is deliberately null for
 * tokens whose upstream price failed the plausibility band.
 *
 * [verified] is **provenance, not safety**: it says a list we trust publishes this contract on this
 * network. It is nullable on purpose — absent means "the server did not say", which is not the same
 * as `false`, and only a real `false` earns a caution.
 */
data class SwapTokenDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("symbol") val symbol: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("decimals") val decimals: Int? = null,
    @SerializedName("networkId") val networkId: String? = null,
    @SerializedName("contractAddress") val contractAddress: String? = null,
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("priceUsd") val priceUsd: String? = null,
    @SerializedName("imported") val imported: Boolean? = null,
    @SerializedName("verified") val verified: Boolean? = null,
    /** 0 ⇒ unranked. Display only; the array order already encodes the ranking. */
    @SerializedName("marketCapRank") val marketCapRank: Int? = null
)

/**
 * `GET /swap/tokens/{networkId}/{contractAddress}`. The contract does not pin down whether the hit
 * comes back as `token` or as a one-element `tokens`, so both are accepted rather than risking a
 * silent null on a shape we guessed wrong.
 */
data class SwapTokenLookupDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("token") val token: SwapTokenDto? = null,
    @SerializedName("tokens") val tokens: List<SwapTokenDto>? = null
)

// --- prepare ---
data class SwapPrepareRequestDto(
    @SerializedName("fromNetwork") val fromNetwork: String,
    @SerializedName("toNetwork") val toNetwork: String,
    @SerializedName("fromToken") val fromToken: String,
    @SerializedName("toToken") val toToken: String,
    @SerializedName("amountRaw") val amountRaw: BigInteger,
    @SerializedName("slippage") val slippage: Double? = null,
    @SerializedName("userAddress") val userAddress: String,
    /**
     * Optional, but when sent it **must** equal the selected route's own `toAddress` — the calldata
     * was built for one recipient and a mismatch is a 400 rather than something the server absorbs.
     */
    @SerializedName("toAddress") val toAddress: String? = null,
    @SerializedName("provider") val provider: String? = null
)

data class SwapPrepareResponseDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("requestId") val requestId: String? = null,
    @SerializedName("transactions") val transactions: List<SwapTxDto>? = null
)
