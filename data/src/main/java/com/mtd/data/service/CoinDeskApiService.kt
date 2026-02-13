package com.mtd.data.service

import com.google.gson.annotations.SerializedName
import com.mtd.domain.model.AssetPriceResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface CoinDeskApiService {
    @GET("spot/v1/latest/tick")
    suspend fun getPrices(
        @Query("market") ids: String="binance",
        @Query("instruments") vsCurrencies: String, // "bitcoin,ethereum"
        @Query("apply_mapping") mapping: Boolean = true,
        @Query("groups") groups: String = "MAPPING,VALUE,MOVING_24_HOUR"
    ): Response<AssetPriceResponse>

    @GET("spot/v1/historical/hours")
    suspend fun getWebMarketChart(
        @Query("market") ids: String="binance",
        @Query("instruments") vsCurrencies: String,
        @Query("groups") groups: String = "MAPPING,VALUE,MOVING_24_HOUR"
    ): Response<CoinGeckoMarketChartResponse>
}

data class CoinGeckoMarketChartResponse(
    @SerializedName("prices") val prices: List<List<Double>>? = null,
    @SerializedName("stats") val stats: List<List<Double>>? = null
)