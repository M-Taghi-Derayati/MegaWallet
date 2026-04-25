package com.mtd.data.service

import com.mtd.data.dto.AssetPriceResponse
import com.mtd.data.dto.HistoricalOhlcResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface CoinDetailApiService {
    @GET("spot/v1/latest/tick")
    suspend fun getPrices(
        @Query("market") ids: String="binance",
        @Query("instruments") vsCurrencies: String, // "bitcoin,ethereum"
        @Query("apply_mapping") mapping: Boolean = true,
        @Query("groups") groups: String = "MAPPING,VALUE,MOVING_24_HOUR"
    ): Response<AssetPriceResponse>

    @GET("spot/v1/historical/hours")
    suspend fun getHistoricalHours(
        @Query("market") market: String = "binance",
        @Query("instrument") instrument: String,
        @Query("limit") limit: Int = 24,
        @Query("apply_mapping") applyMapping: Boolean = true,
        @Query("groups") groups: String = "MAPPING,OHLC"
    ): Response<HistoricalOhlcResponse>

    @GET("spot/v1/historical/days")
    suspend fun getHistoricalDays(
        @Query("market") market: String = "binance",
        @Query("instrument") instrument: String,
        @Query("limit") limit: Int = 365,
        @Query("apply_mapping") applyMapping: Boolean = true,
        @Query("groups") groups: String = "MAPPING,OHLC"
    ): Response<HistoricalOhlcResponse>
}

