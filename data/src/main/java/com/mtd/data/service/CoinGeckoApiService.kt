package com.mtd.data.service

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import java.math.BigDecimal

interface CoinGeckoApiService {
    @GET("api/v3/simple/price")
    suspend fun getPrices(
        @Query("ids") ids: String, // "bitcoin,ethereum"
        @Query("vs_currencies") vsCurrencies: String = "usd",
        @Query("include_24hr_change") includeChange: Boolean = true
    ): Response<Map<String, Map<String, BigDecimal>>>
}