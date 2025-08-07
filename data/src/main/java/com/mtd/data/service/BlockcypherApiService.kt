package com.mtd.data.service

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.math.BigInteger

// --- اینترفیس Retrofit ---
interface BlockcypherApiService {

    @GET("addrs/{address}?unspentOnly=true&includeScript=true")
    suspend fun getUtxos(
        @Path("address") address: String,
    ): Response<AddressFullDto>

    @GET("addrs/{address}/full")
    suspend fun getTransactionHistory(
        @Path("address") address: String,
    ): Response<AddressFullDto>


    // ارسال یک تراکنش امضا شده
    @POST("{chain}/txs/push")
    suspend fun broadcastTransaction(
        @Body tx: PushTxDto
    ): Response<PushTxResponseDto>

}

// --- مدل‌های داده (DTOs) ---

data class AddressFullDto(
    @SerializedName("address") val address: String,
    @SerializedName("final_balance") val finalBalance: Long,
    @SerializedName("txrefs") val txrefs: List<TxRefDto>?,
    @SerializedName("txs") val txs: List<TxDto>?
)
data class TxRefDto(
    @SerializedName("tx_hash") val txHash: String,
    @SerializedName("block_height") val blockHeight: Long,
    @SerializedName("tx_input_n") val txInputN: Int,
    @SerializedName("tx_output_n") val txOutputN: Int,
    @SerializedName("value") val value: Long, // in Satoshis
    @SerializedName("confirmations") val confirmations: Int,
    @SerializedName("spent") val spent: Boolean,
    @SerializedName("script") val script: String, // اسکریپت هگز برای امضا
    @SerializedName("received") val received: String // تاریخ
)
data class TxDto(
    @SerializedName("hash") val hash: String,
    @SerializedName("block_height") val blockHeight: Long?,
    @SerializedName("received") val received: String?, // ISO 8601
    @SerializedName("confirmations") val confirmations: Int,
    @SerializedName("confirmed") val confirmed: String,
    @SerializedName("fees") val fees: Long?,
    @SerializedName("inputs") val inputs: List<IO>?,
    @SerializedName("outputs") val outputs: List<IO>?
)

data class IO(
    @SerializedName("addresses") val addresses: List<String>?,
    @SerializedName("output_value") val outputValue: Long? // only for inputs
    ,
    @SerializedName("value") val value: Long? // only for outputs
)

// مدل برای ارسال تراکنش
data class PushTxDto(
    @SerializedName("tx") val txHex: String
)

data class PushTxResponseDto(
    @SerializedName("tx") val tx: TxDto
)