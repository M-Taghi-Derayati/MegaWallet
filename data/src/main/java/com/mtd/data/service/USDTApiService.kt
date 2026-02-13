package com.mtd.data.service

import com.mtd.domain.model.USDTPriceModel
import retrofit2.Response
import retrofit2.http.GET

interface USDTApiService {
    @GET("v1/all-fairPrice")
    suspend fun getMarketStats(): Response<USDTPriceModel>
}
