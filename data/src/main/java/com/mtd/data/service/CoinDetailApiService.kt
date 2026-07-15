package com.mtd.data.service

import com.mtd.data.dto.AssetPriceResponse
import com.mtd.data.dto.HistoricalOhlcResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface CoinDetailApiService {
    @Headers(
        "Origin: https://pro.coincap.io",
        "Referer: https://pro.coincap.io/",
        "Accept: application/json",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )
    @GET("v3/assets")
    suspend fun getPrices(
        @Query("search") search: String?=null,
        @Query("ids") ids: String?=null,
    ): Response<AssetPriceResponse>


    @Headers(
        "Origin: https://pro.coincap.io",
        "Referer: https://pro.coincap.io/",
        "Accept: application/json",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )
    @GET("v3/assets/{slug}/history")
    suspend fun getHistorical(
       @Path ("slug")coin : String,
       @Query("start") start: Long,
       @Query("end") end: Long,
       @Query("interval") interval: String="d1",
    ): Response<HistoricalOhlcResponse>


}

