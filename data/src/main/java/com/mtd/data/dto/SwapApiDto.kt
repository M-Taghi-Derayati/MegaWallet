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
    @SerializedName("ttlMs") val ttlMs: Long? = null
)

data class SwapRouteDto(
    @SerializedName("rank") val rank: Int? = null,
    @SerializedName("isBestReturn") val isBestReturn: Boolean? = null,
    @SerializedName("provider") val provider: String? = null,
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

// --- prepare ---
data class SwapPrepareRequestDto(
    @SerializedName("fromNetwork") val fromNetwork: String,
    @SerializedName("toNetwork") val toNetwork: String,
    @SerializedName("fromToken") val fromToken: String,
    @SerializedName("toToken") val toToken: String,
    @SerializedName("amountRaw") val amountRaw: BigInteger,
    @SerializedName("slippage") val slippage: Double? = null,
    @SerializedName("userAddress") val userAddress: String,
    @SerializedName("provider") val provider: String? = null
)

data class SwapPrepareResponseDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("requestId") val requestId: String? = null,
    @SerializedName("transactions") val transactions: List<SwapTxDto>? = null
)
