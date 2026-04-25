package com.mtd.data.service

import com.mtd.data.dto.USDTPriceApiDto
import retrofit2.Response
import retrofit2.http.GET

interface USDTApiService {
    @GET("v1/all-fairPrice")
    suspend fun getMarketStats(): Response<USDTPriceApiDto>
}
