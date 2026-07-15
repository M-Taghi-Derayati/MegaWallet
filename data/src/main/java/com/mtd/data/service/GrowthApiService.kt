package com.mtd.data.service

import com.mtd.data.dto.BoxOddsResponseDto
import com.mtd.data.dto.BoxOpenResponseDto
import com.mtd.data.dto.BoxesResponseDto
import com.mtd.data.dto.GasCreditBalanceDto
import com.mtd.data.dto.ReferralStatsDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Phase 4 — Growth & Engagement (`/api/v1/growth`). Box-open and gas-credit balance are
 * device-gated: the bearer JWT (carrying the attested `deviceId` claim) is attached automatically by
 * the host-scoped AuthInterceptor — no `deviceId` is passed here.
 */
interface GrowthApiService {

    @GET("api/v1/growth/referral/stats")
    suspend fun getReferralStats(
        @Query("address") address: String
    ): Response<ReferralStatsDto>

    @GET("api/v1/growth/boxes")
    suspend fun getBoxes(
        @Query("owner") owner: String,
        @Query("status") status: String? = null
    ): Response<BoxesResponseDto>

    @GET("api/v1/growth/boxes/odds")
    suspend fun getBoxOdds(): Response<BoxOddsResponseDto>

    /** Device-bound — identity from the `deviceId` JWT claim attached by the interceptor. */
    @GET("api/v1/growth/gas-credit/balance")
    suspend fun getGasCreditBalance(): Response<GasCreditBalanceDto>

    /** Device-bound atomic open + draw. */
    @POST("api/v1/growth/boxes/{id}/open")
    suspend fun openBox(
        @Path("id") boxId: String
    ): Response<BoxOpenResponseDto>
}
