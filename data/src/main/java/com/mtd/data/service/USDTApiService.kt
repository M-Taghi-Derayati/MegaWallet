package com.mtd.data.service

import com.mtd.domain.model.USDTMarketStatsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface USDTApiService {
    @GET("market/stats")
    suspend fun getMarketStats(
        @Query("srcCurrency") srcCurrency: String,
        @Query("dstCurrency") dstCurrency: String
    ): Response<USDTMarketStatsResponse>
}
