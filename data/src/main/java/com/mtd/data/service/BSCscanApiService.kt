package com.mtd.data.service


import com.mtd.data.dto.BSCscanResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

// اینترفیس Retrofit
interface BSCscanApiService {
    @GET("api/tx/getAssetTransferByAddress")
    suspend fun getAssetTransferByAddress(
        @Query("address") address: String,
        @Query("type") type: String, // "transaction", "20", "721", "1155"
        @Query("pageSize") pageSize: Int = 20,
        @Query("page") page: Int = 1,
        @Query("order") order: String = "desc"
    ): Response<BSCscanResponse>
}


