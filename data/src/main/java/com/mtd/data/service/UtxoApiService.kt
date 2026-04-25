package com.mtd.data.service

import com.mtd.data.dto.AddressDataDto
import com.mtd.data.dto.ChainInfoDto
import com.mtd.data.dto.PushTxRequest
import com.mtd.data.dto.PushTxResponse
import com.mtd.data.dto.TransactionDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface UtxoApiService {

    @GET("v1/{coin}/{chain}/addrs/{address}")
    suspend fun getAddressData(
        @Path("coin") coin: String,
        @Path("chain") chain: String,
        @Path("address") address: String,
        @Query("unspentOnly") unspentOnly: Boolean? = null,
        @Query("includeScript") includeScript: Boolean? = null,
        @Query("limit") limit: Int? = 50
    ): Response<AddressDataDto>

    @GET("v1/{coin}/{chain}")
    suspend fun getChainInfo(
        @Path("coin") coin: String,
        @Path("chain") chain: String
    ): Response<ChainInfoDto>

    @GET("v1/{coin}/{chain}/txs/{txHash}")
    suspend fun getTransaction(
        @Path("coin") coin: String,
        @Path("chain") chain: String,
        @Path("txHash") txHash: String
    ): Response<TransactionDto>

    @POST("v1/{coin}/{chain}/txs/push")
    suspend fun pushTransaction(
        @Path("coin") coin: String,
        @Path("chain") chain: String,
        @Body body: PushTxRequest
    ): Response<PushTxResponse>
}
