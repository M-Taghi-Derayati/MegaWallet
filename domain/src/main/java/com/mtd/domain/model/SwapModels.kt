package com.mtd.domain.model

import java.math.BigInteger

/**
 * Phase 4 — Multi-route swap domain models (`/api/v1/swap`).
 *
 * Contract constraints encoded here:
 *  - `toAmount.{gross,net,min}`, `fees.*`, and `estimatedGas.native/costInToToken` are raw base units
 *    as [BigInteger]. `net` is the user's receive amount after the 0.5% platform fee.
 *  - A quote is valid for a **strict [SwapQuote.ttlMs]** (default 15s); re-quote on expiry.
 *  - `prepare` returns an **ordered** [SwapPrepareResult.transactions] list (optional approve, then swap)
 *    to sign + submit in order. Non-custodial: the client signs locally.
 */

/** `GET /api/v1/swap/providers` — active strategies + platform fee + quote TTL. */
data class SwapProviders(
    val providers: List<String>,
    val platformFeeBps: Int?,
    val quoteTtlMs: Long?
)

data class SwapToAmount(
    val gross: BigInteger,
    val net: BigInteger,
    val min: BigInteger
)

data class SwapFees(
    val platformBps: Int?,
    val platformCommission: BigInteger?,
    val grossOutput: BigInteger?,
    val netOutput: BigInteger?
)

data class SwapEstimatedGas(
    val native: BigInteger?,
    val costInToToken: BigInteger?,
    val costUsd: Double?
)

/** An unsigned transaction template (approve or swap) to sign + broadcast locally. */
data class SwapTx(
    val to: String,
    val data: String,
    val value: String?     // hex-quantity string (e.g. "0x0"); not a raw base-unit amount
)

data class SwapRoute(
    val rank: Int?,
    val isBestReturn: Boolean,
    val provider: String?,
    val toAmount: SwapToAmount,
    val fees: SwapFees?,
    val estimatedGas: SwapEstimatedGas?,
    val allowanceTarget: String?,
    val tx: SwapTx?
)

/** `GET /api/v1/swap/quote` — ranked routes with a hard [ttlMs] expiry. */
data class SwapQuote(
    val requestId: String?,
    val routes: List<SwapRoute>,
    val bestRoute: SwapRoute?,
    val platformFeeBps: Int?,
    val expiresAt: String?,
    val ttlMs: Long?
)

/** Inputs for a swap quote. `amountRaw` is the raw base-unit input amount. */
data class SwapQuoteRequest(
    val fromNetwork: String,
    val toNetwork: String,
    val fromToken: String,
    val toToken: String,
    val amountRaw: BigInteger,
    val slippage: Double? = null,
    val userAddress: String? = null
)

/** `POST /api/v1/swap/prepare` — ordered, non-custodial transaction bundle to sign in order. */
data class SwapPrepareResult(
    val requestId: String?,
    val transactions: List<SwapTx>
)
