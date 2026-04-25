package com.mtd.data.service

import com.mtd.data.dto.AddressDto
import com.mtd.data.dto.FeeRecommendationDto
import com.mtd.data.dto.TxDto
import com.mtd.data.dto.UtxoDto
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path


interface BTCApiService {

    @GET("address/{address}/utxo")
    suspend fun getUtxos(
        @Path("address") address: String
    ): Response<List<UtxoDto>>

    @POST("tx")
    suspend fun broadcastTransaction(
        @Body txHex: RequestBody
    ): Response<String>

    @GET("address/{address}")
    suspend fun getAddressDetails(
        @Path("address") address: String
    ): Response<AddressDto>

    @GET("address/{address}/txs")
    suspend fun getConfirmedTransactions(
        @Path("address") address: String
    ): Response<List<TxDto>>

    @GET("v1/fees/recommended")
    suspend fun getRecommendedFees(): Response<FeeRecommendationDto>

}



