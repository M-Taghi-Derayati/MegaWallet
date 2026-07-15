package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.SwapPrepareResult
import com.mtd.domain.model.SwapProviders
import com.mtd.domain.model.SwapQuote
import com.mtd.domain.model.SwapQuoteRequest

/**
 * Phase 4 — Multi-route swap (`/api/v1/swap`). Quotes carry a hard 15s TTL (re-quote on expiry);
 * [prepare] returns an ordered, non-custodial transaction bundle to sign + submit in order. The
 * `x-idempotency-key` on prepare is injected by the host-scoped IdempotencyInterceptor. Failures map
 * to the typed [com.mtd.domain.model.error.ApiException] (422 = SWAP_SIMULATION_FAILED / SWAP_NO_ROUTES).
 */
interface ISwapRepository {

    /** Active provider strategies + platform fee bps + quote TTL. */
    suspend fun getProviders(): ResultResponse<SwapProviders>

    /** Ranked routes (15s cache). */
    suspend fun getQuote(request: SwapQuoteRequest): ResultResponse<SwapQuote>

    /**
     * Allowance bundle + post-selection simulation. Picks [routeProvider] (or the best route when
     * null). 422 ⇒ [com.mtd.domain.model.error.ApiError.SimulationReverted].
     */
    suspend fun prepare(
        request: SwapQuoteRequest,
        routeProvider: String? = null
    ): ResultResponse<SwapPrepareResult>
}
