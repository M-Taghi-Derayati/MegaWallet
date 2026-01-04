package com.mtd.data.service


import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import java.math.BigInteger

// اینترفیس Retrofit
interface BSCscanApiService {
    @GET("v2/api")
    suspend fun getTransactions(
        @Query("chainid") chainid: String = "97",
        @Query("module") module: String = "account",
        @Query("action") action: String = "txlist",
        @Query("address") address: String,
        @Query("sort") sort: String = "desc",
        @Query("apikey") apiKey: String="AP9B14J1M6MGACP5GAHMS6377VAD8U2NT5",
        @Query("page") page: Int = 1,
        @Query("offset") offset: Int = 30
    ): Response<BSCscanResponse>
}

// مدل‌های DTO
data class BSCscanResponse(val status: String, val result: List<BSCscanTransactionDto>)
data class BSCscanTransactionDto(
    val hash: String, val from: String, val to: String, val value: BigInteger,
    val gasUsed: String, val gasPrice: String, val timeStamp: String, val isError: String
)