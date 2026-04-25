package com.mtd.data.service


import com.mtd.data.dto.BlockscoutResponse
import com.mtd.data.dto.EVMTokenTransferDto
import com.mtd.data.dto.EVMTransactionDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface EVMApiService {
    @GET("api/v2/addresses/{address}/transactions")
    suspend fun getTransactions(
        @Path("address") address: String,
        @Query("filter") filter: String? = null
    ): Response<BlockscoutResponse<EVMTransactionDto>>

    @GET("api/v2/addresses/{address}/token-transfers")
    suspend fun getTokenTransfers(
        @Path("address") address: String
    ): Response<BlockscoutResponse<EVMTokenTransferDto>>
}