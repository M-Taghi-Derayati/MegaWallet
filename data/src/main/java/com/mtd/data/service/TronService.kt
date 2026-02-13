package com.mtd.data.service

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.math.BigInteger

interface TronNativeService {
    @GET("wallet/getchainparameters")
    suspend fun getChainParameters(): ChainParameterResponse

    @POST("wallet/triggerconstantcontract")
    suspend fun triggerConstantContract(@Body body: TriggerConstantRequest): EnergyResponse

    @POST("wallet/createtransaction")
    suspend fun createTransaction(@Body body: CreateTxRequest): CreateTransactionResponse

    @POST("wallet/getaccount")
    suspend fun getAccount(@Body body: AccountRequest): TronAccountResponse
}


interface TronExplorerService {
    // سوابق تراکنش‌های بومی (TRX)
    @GET("api/transaction")
    suspend fun getTrxHistory(
        @Query("address") address: String,
        @Query("limit") limit: Int = 20,
        @Query("start") start: Int = 0 // برای صفحه‌بندی (آیتم شروع)
    ): TronscanNormalResponse

    // سوابق تراکنش‌های توکن (مثل USDT)
    @GET("api/token_trc20/transfers")
    suspend fun getTokenHistory(
        @Query("relatedAddress") address: String,
        @Query("limit") limit: Int = 20,
        @Query("start") start: Int = 0,
        @Query("contract_address") contractAddress: String? = null // اگر فقط یک توکن خاص را بخواهید
    ): TronscanTokenResponse
}

data class TronscanNormalResponse(
    val data: List<TrxData>
)
data class TrxData(
    val hash: String,
    val timestamp: Long,
    val ownerAddress: String,
    val toAddress: String,
    val amount: BigInteger,
    val cost: TrxCost,
    val confirmed: Boolean,
    val result: String? // "SUCCESS"
)
data class TrxCost(val net_fee: BigInteger, val energy_fee: BigInteger)

// برای تراکنش‌های توکن (USDT/TRC20)
data class TronscanTokenResponse(
    val trc20_transfer: List<TokenTransferData>
)
data class TokenTransferData(
    val transaction_id: String,
    val block_ts: Long,
    val from: String,
    val to: String,
    val value: BigInteger,
    val token_id: String, // همان آدرس قرارداد
    val symbol: String
)




// پاسخ پارامترهای شبکه (برای جلوگیری از هاردکد)
data class ChainParameterResponse(val chainParameter: List<ChainParameter>)
data class ChainParameter(val key: String, val value: Long?)

// پاسخ متد Trigger Constant (برای تخمین انرژی)
data class EnergyResponse(val energy_used: Long?, val result: TriggerResult?)
data class TriggerResult(val result: Boolean, val message: String?)

// پاسخ ایجاد تراکنش خام (برای محاسبه پهنای باند)
data class CreateTransactionResponse(val raw_data_hex: String, val txID: String)

// پاسخ موجودی اکانت بومی
data class TronAccountResponse(val address: String?, val balance: Long?)

// مدل ارسال درخواست برای ایجاد تراکنش
data class CreateTxRequest(
    val owner_address: String,
    val to_address: String,
    val amount: Long,
    val visible: Boolean = true
)

data class TriggerConstantRequest(
    val owner_address: String,
    val contract_address: String,
    val function_selector: String,
    val parameter: String,
    val visible: Boolean = true
)

data class AccountRequest(
    val address: String,
    val visible: Boolean = true
)