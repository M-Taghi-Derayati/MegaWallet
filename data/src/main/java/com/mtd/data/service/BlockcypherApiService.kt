package com.mtd.data.service

import com.google.gson.annotations.SerializedName
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.math.BigInteger

// --- اینترفیس Retrofit ---
interface BlockcypherApiService {

    // 📌 گرفتن UTXO ها (لیست خروجی‌های خرج‌نشده) برای یک آدرس
    @GET("address/{address}/utxo")
    suspend fun getUtxos(
        @Path("address") address: String
    ): Response<List<MempoolUtxoDto>>

    // 📌 ارسال تراکنش به شبکه
    @POST("tx")
    suspend fun broadcastTransaction(
        @Body txHex: RequestBody
    ): Response<String>

    // 📌 گرفتن جزئیات کامل یک آدرس (برای مشاهده تراکنش‌ها و موجودی)
    @GET("address/{address}")
    suspend fun getAddressDetails(
        @Path("address") address: String
    ): Response<MempoolAddressDto>

    // 📌 گرفتن تاریخچه تراکنش‌های تأیید شده برای یک آدرس
    @GET("address/{address}/txs")
    suspend fun getConfirmedTransactions(
        @Path("address") address: String
    ): Response<List<MempoolTxDto>>

    // 📌 گرفتن نرخ‌های کارمزد پیشنهادی شبکه
    @GET("v1/fees/recommended")
    suspend fun getRecommendedFees(): Response<MempoolFeeRecommendationDto>


}

data class MempoolUtxoDto(
    val txid: String,
    val vout: Int,
    val value: Long,
    val status: MempoolStatusDto
)

data class MempoolStatusDto(
    val confirmed: Boolean,
    val block_height: Long?,
    val block_hash: String?,
    val block_time: Long?
)

data class MempoolAddressDto(
    val address: String,
    val chain_stats: MempoolAddressStatsDto,
    val mempool_stats: MempoolAddressStatsDto
)
data class MempoolAddressStatsDto(
    val funded_txo_count: Int,
    val funded_txo_sum: Long,
    val spent_txo_count: Int,
    val spent_txo_sum: Long,
    val tx_count: Int
)
data class MempoolTxDto(
    val txid: String,
    val fee: Long?,
    val status: MempoolStatusDto,
    val vin: List<MempoolVinDto>,
    val vout: List<MempoolVoutDto>
)
data class MempoolVinDto(
    val prevout: MempoolVoutDto?
)
data class MempoolVoutDto(
    val scriptpubkey_address: String?,
    val value: Long
)
data class MempoolFeeRecommendationDto(
    val fastestFee: Int,
    val halfHourFee: Int,
    val hourFee: Int,
    val economyFee: Int,
    val minimumFee: Int,
)

/*

// --- مدل‌های داده (DTOs) ---
// یک DTO جدید برای پاسخ getChainInfo اضافه می‌کنیم
data class ChainInfoDto(
    @SerializedName("name") val name: String,
    @SerializedName("high_fee_per_kb") val highFeePerKb: Long,
    @SerializedName("medium_fee_per_kb") val mediumFeePerKb: Long,
    @SerializedName("low_fee_per_kb") val lowFeePerKb: Long
)

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
)*/
