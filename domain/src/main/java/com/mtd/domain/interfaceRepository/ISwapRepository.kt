package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.SwapChain
import com.mtd.domain.model.SwapPrepareResult
import com.mtd.domain.model.SwapProviders
import com.mtd.domain.model.SwapQuote
import com.mtd.domain.model.SwapQuoteRequest
import com.mtd.domain.model.SwapToken

/**
 * Phase 4 — Multi-route swap (`/api/v1/swap`). Quotes carry a hard 15s TTL (re-quote on expiry);
 * [prepare] returns an ordered, non-custodial transaction bundle to sign + submit in order. The
 * `x-idempotency-key` on prepare is injected by the host-scoped IdempotencyInterceptor. Failures map
 * to the typed [com.mtd.domain.model.error.ApiException] (422 = SWAP_SIMULATION_FAILED / SWAP_NO_ROUTES).
 */
interface ISwapRepository {

    /** Active provider strategies + platform fee bps + quote TTL. */
    suspend fun getProviders(): ResultResponse<SwapProviders>

    /**
     * Networks where swap/bridge works. Cached in-memory (it changes rarely); [forceRefresh]
     * bypasses the cache for the app-start/resume refresh.
     */
    suspend fun getChains(forceRefresh: Boolean = false): ResultResponse<List<SwapChain>>

    /**
     * The swap universe for one network — **not** the signed config bundle. [query] null ⇒ the
     * default popularity-ordered list; otherwise a symbol/name/address search.
     *
     * The returned order is already ranked (exact symbol match first, so the real USDT outranks
     * look-alikes); callers must not re-sort it. [limit] is clamped to the server's 1..200 range.
     */
    suspend fun getTokens(
        networkId: String,
        query: String? = null,
        limit: Int? = null
    ): ResultResponse<List<SwapToken>>

    /**
     * Exact contract-address lookup for the paste-an-address import.
     * 404 ⇒ [com.mtd.domain.model.error.ApiError.AssetNotFound]: refuse the import rather than
     * adding a token the user can never trade.
     */
    suspend fun findToken(networkId: String, contractAddress: String): ResultResponse<SwapToken>

    /**
     * Ranked routes (15s cache, keyed per wallet **and** per
     * [com.mtd.domain.model.SwapQuoteRequest.toAddress] — a cache hit is only ever your own
     * previous quote to your own previous destination).
     */
    suspend fun getQuote(request: SwapQuoteRequest): ResultResponse<SwapQuote>

    /**
     * Allowance bundle + post-selection simulation. Picks [routeProvider] (or the best route when
     * null). 422 ⇒ [com.mtd.domain.model.error.ApiError.SimulationReverted].
     *
     * [routeToAddress] is the selected route's own `toAddress`. When both it and the request's
     * destination are known they **must** agree: the calldata was built for one recipient, so a
     * disagreement is refused here rather than sent — preparing under the wrong name is how funds
     * reach the wrong wallet.
     */
    suspend fun prepare(
        request: SwapQuoteRequest,
        routeProvider: String? = null,
        routeToAddress: String? = null
    ): ResultResponse<SwapPrepareResult>
}
