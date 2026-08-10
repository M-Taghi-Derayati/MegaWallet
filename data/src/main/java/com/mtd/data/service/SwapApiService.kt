package com.mtd.data.service

import com.mtd.data.dto.SwapChainsDto
import com.mtd.data.dto.SwapPrepareRequestDto
import com.mtd.data.dto.SwapPrepareResponseDto
import com.mtd.data.dto.SwapProvidersDto
import com.mtd.data.dto.SwapQuoteResponseDto
import com.mtd.data.dto.SwapTokenLookupDto
import com.mtd.data.dto.SwapTokensDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Phase 4 — Multi-route swap (`/api/v1/swap`). The `x-idempotency-key` required on `/prepare` is
 * injected by the host-scoped IdempotencyInterceptor, so it is not declared as a parameter here.
 */
interface SwapApiService {

    @GET("api/v1/swap/providers")
    suspend fun getProviders(): Response<SwapProvidersDto>

    /**
     * Networks where swap/bridge actually works. A network missing from this list has no
     * liquidity (all testnets), so the swap entry point must be hidden rather than left to fail
     * with a 422 that reads like "no route right now".
     */
    @GET("api/v1/swap/chains")
    suspend fun getChains(): Response<SwapChainsDto>

    /**
     * The searchable swap universe (~11k tokens), served from a server-side mirror — no upstream
     * call, so per-keystroke queries are cheap. [query] matches symbol, name or contract address.
     */
    @GET("api/v1/swap/tokens")
    suspend fun getTokens(
        @Query("networkId") networkId: String,
        @Query("q") query: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<SwapTokensDto>

    /** Exact, case-insensitive lookup for the paste-an-address flow. 404 ⇒ not routable. */
    @GET("api/v1/swap/tokens/{networkId}/{contractAddress}")
    suspend fun findToken(
        @Path("networkId") networkId: String,
        @Path("contractAddress") contractAddress: String
    ): Response<SwapTokenLookupDto>

    /**
     * [userAddress] is required — omitting it returns `400 SWAP_VALIDATION_ERROR`, and the route's
     * `tx.data` is built for exactly that wallet.
     *
     * [toAddress] is the wallet the **output** is paid to. Optional on a same-chain swap or an
     * EVM → EVM bridge (it defaults to [userAddress]); **required** across TRON ↔ EVM, where the
     * two sides use different address formats and there is nothing sane to default to. The 15s
     * quote cache is keyed per wallet *and* per destination.
     */
    @GET("api/v1/swap/quote")
    suspend fun getQuote(
        @Query("fromNetwork") fromNetwork: String,
        @Query("toNetwork") toNetwork: String,
        @Query("fromToken") fromToken: String,
        @Query("toToken") toToken: String,
        @Query("amountRaw") amountRaw: String,
        @Query("userAddress") userAddress: String,
        @Query("toAddress") toAddress: String? = null,
        @Query("slippage") slippage: Double? = null
    ): Response<SwapQuoteResponseDto>

    @POST("api/v1/swap/prepare")
    suspend fun prepare(
        @Body body: SwapPrepareRequestDto
    ): Response<SwapPrepareResponseDto>
}
