package com.mtd.domain.model

import java.math.BigInteger

/**
 * Phase 4 — Multi-route swap domain models (`/api/v1/swap`).
 *
 * Contract constraints encoded here:
 *  - `toAmount.{gross,net,min}`, `fees.*`, and `estimatedGas.native/costInToToken` are raw base units
 *    as [BigInteger]. **[SwapToAmount.net] is what the wallet actually receives — always display it**
 *    and never re-derive it as `gross − platformBps`.
 *  - A quote is valid for a **strict [SwapQuote.ttlMs]** (default 15s); re-quote on expiry.
 *  - `prepare` returns an **ordered** [SwapPrepareResult.transactions] list (optional approve, then swap)
 *    to sign + submit in order. Non-custodial: the client signs locally.
 */

/** `GET /api/v1/swap/providers` — active strategies + platform fee + quote TTL. */
data class SwapProviders(
    val providers: List<String>,
    val platformFeeBps: Int?,
    val platformFeeCollected: Boolean?,
    val quoteTtlMs: Long?
)

data class SwapToAmount(
    val gross: BigInteger,
    val net: BigInteger,
    val min: BigInteger
)

/**
 * کارمزدِ پلتفرم برای یک مسیر.
 *
 * [platformBps] فقط «نرخِ پیکربندی‌شده» است، نه سندِ این‌که چیزی برداشته شده — اپراتور می‌تواند آن را
 * در زمانِ اجرا عوض کند و بین دو استعلامِ پشت‌سرهم فرق کند، پس هرگز کش یا hard-code نمی‌شود و از
 * *همین پاسخ* خوانده می‌شود. تنها [collected] می‌گوید کارمزد واقعاً روی زنجیره برداشته شده؛ تا وقتی
 * `false` است `platformCommission` صفر و `toAmount.net == toAmount.gross` است.
 *
 * [uncollectedCommission] صرفاً اطلاعاتی است و **هرگز** نباید از مبلغِ دریافتی کم شود.
 */
data class SwapFees(
    val platformBps: Int?,
    val collected: Boolean,
    val platformCommission: BigInteger?,
    val uncollectedCommission: BigInteger?,
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
    /** Whether the commission on [bestRoute] was really withheld. Per-response; never cache it. */
    val platformFeeCollected: Boolean?,
    val expiresAt: String?,
    val ttlMs: Long?
)

/**
 * Inputs for a swap quote. `amountRaw` is the raw base-unit input amount.
 *
 * [userAddress] is **required** by the server: routes are quoted *for a specific wallet* and the
 * returned `tx.data` is built for that address. A placeholder would produce a transaction that
 * pays out to someone else, so there is no default — the caller must resolve a real address or
 * not ask for a quote at all.
 */
data class SwapQuoteRequest(
    val fromNetwork: String,
    val toNetwork: String,
    val fromToken: String,
    val toToken: String,
    val amountRaw: BigInteger,
    val userAddress: String,
    val slippage: Double? = null
)

/** `POST /api/v1/swap/prepare` — ordered, non-custodial transaction bundle to sign in order. */
data class SwapPrepareResult(
    val requestId: String?,
    val transactions: List<SwapTx>
)
