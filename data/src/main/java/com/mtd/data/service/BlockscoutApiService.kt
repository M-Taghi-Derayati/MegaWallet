package com.mtd.data.service


import com.mtd.data.dto.BlockscoutResponse
import com.mtd.data.dto.BlockscoutTransactionDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BlockscoutApiService {
    @GET("api/v2/addresses/{address}/transactions")
    suspend fun getTransactions(
        @Path("address") address: String,
        @Query("filter") filter: String = "to | from" // دریافت تراکنش‌های ورودی و خروجی
    ): Response<BlockscoutResponse<BlockscoutTransactionDto>>
}