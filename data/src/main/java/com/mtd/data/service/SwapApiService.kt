package com.mtd.data.service

import com.mtd.data.dto.SwapPrepareRequestDto
import com.mtd.data.dto.SwapPrepareResponseDto
import com.mtd.data.dto.SwapProvidersDto
import com.mtd.data.dto.SwapQuoteResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Phase 4 — Multi-route swap (`/api/v1/swap`). The `x-idempotency-key` required on `/prepare` is
 * injected by the host-scoped IdempotencyInterceptor, so it is not declared as a parameter here.
 */
interface SwapApiService {

    @GET("api/v1/swap/providers")
    suspend fun getProviders(): Response<SwapProvidersDto>

    @GET("api/v1/swap/quote")
    suspend fun getQuote(
        @Query("fromNetwork") fromNetwork: String,
        @Query("toNetwork") toNetwork: String,
        @Query("fromToken") fromToken: String,
        @Query("toToken") toToken: String,
        @Query("amountRaw") amountRaw: String,
        @Query("slippage") slippage: Double? = null,
        @Query("userAddress") userAddress: String? = null
    ): Response<SwapQuoteResponseDto>

    @POST("api/v1/swap/prepare")
    suspend fun prepare(
        @Body body: SwapPrepareRequestDto
    ): Response<SwapPrepareResponseDto>
}
