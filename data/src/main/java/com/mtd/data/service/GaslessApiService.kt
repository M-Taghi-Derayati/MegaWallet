package com.mtd.data.service

import com.mtd.data.dto.EvmSponsorApproveRequestDto
import com.mtd.data.dto.EvmSponsorApproveResponseDto
import com.mtd.data.dto.GaslessEligibilityRequestDto
import com.mtd.data.dto.GaslessEligibilityResponseDto
import com.mtd.data.dto.GaslessPrepareResponseDto
import com.mtd.data.dto.GaslessQuoteRequestDto
import com.mtd.data.dto.GaslessQuoteResponseDto
import com.mtd.data.dto.GaslessRelayRequestDto
import com.mtd.data.dto.GaslessRelayResponseDto
import com.mtd.data.dto.GaslessSupportedTokenDto
import com.mtd.data.dto.GaslessTxStatusDto
import com.mtd.data.dto.TronApproveQuoteRequestDto
import com.mtd.data.dto.TronApproveQuoteResponseDto
import com.mtd.data.dto.TronSponsorApproveRequestDto
import com.mtd.data.dto.TronSponsorApproveResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GaslessApiService {

    @GET("api/{chain}/tokens")
    suspend fun getSupportedTokens(
        @Path("chain") chain: String
    ): Response<List<GaslessSupportedTokenDto>>

    @POST("api/{chain}/eligibility")
    suspend fun checkEligibility(
        @Path("chain") chain: String,
        @Body request: GaslessEligibilityRequestDto
    ): Response<GaslessEligibilityResponseDto>

    @GET("api/{chain}/prepare/{userAddress}")
    suspend fun prepareGasless(
        @Path("chain") chain: String,
        @Path("userAddress") userAddress: String,
        @Query("startNonce") startNonce: String? = null
    ): Response<GaslessPrepareResponseDto>

    @POST("api/{chain}/quote")
    suspend fun quoteGasless(
        @Path("chain") chain: String,
        @Body request: GaslessQuoteRequestDto
    ): Response<GaslessQuoteResponseDto>

    @POST("api/{chain}/quote/approve")
    suspend fun quoteTronApprove(
        @Path("chain") chain: String,
        @Body request: TronApproveQuoteRequestDto
    ): Response<TronApproveQuoteResponseDto>

    @POST("api/{chain}/relay")
    suspend fun relayGasless(
        @Path("chain") chain: String,
        @Header("x-idempotency-key") idempotencyKey: String,
        @Body request: GaslessRelayRequestDto
    ): Response<GaslessRelayResponseDto>

    @POST("api/tron/sponsor-approve")
    suspend fun sponsorTronApprove(
        @Body request: TronSponsorApproveRequestDto
    ): Response<TronSponsorApproveResponseDto>

    @POST("api/evm/sponsor-approve")
    suspend fun sponsorEvmApprove(
        @Body request: EvmSponsorApproveRequestDto
    ): Response<EvmSponsorApproveResponseDto>

    @GET("api/{chain}/tx/{id}")
    suspend fun getGaslessTxStatus(
        @Path("chain") chain: String,
        @Path("id") txId: String
    ): Response<GaslessTxStatusDto>
}
