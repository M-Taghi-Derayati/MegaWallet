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
    @SerializedName("quoteTtlMs") val quoteTtlMs: Long? = null
)

// --- quote ---
data class SwapQuoteResponseDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("requestId") val requestId: String? = null,
    @SerializedName("routes") val routes: List<SwapRouteDto>? = null,
    @SerializedName("bestRoute") val bestRoute: SwapRouteDto? = null,
    @SerializedName("platformFeeBps") val platformFeeBps: Int? = null,
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

data class SwapFeesDto(
    @SerializedName("platformBps") val platformBps: Int? = null,
    @SerializedName("platformCommission") val platformCommission: BigInteger? = null,
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
    @SerializedName("userAddress") val userAddress: String? = null,
    @SerializedName("provider") val provider: String? = null
)

data class SwapPrepareResponseDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("requestId") val requestId: String? = null,
    @SerializedName("transactions") val transactions: List<SwapTxDto>? = null
)
