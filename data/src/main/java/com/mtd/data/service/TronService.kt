package com.mtd.data.service

import com.google.gson.JsonObject
import com.mtd.data.dto.AccountRequest
import com.mtd.data.dto.ChainParameterResponse
import com.mtd.data.dto.CreateTransactionResponse
import com.mtd.data.dto.CreateTxRequest
import com.mtd.data.dto.EnergyResponse
import com.mtd.data.dto.TriggerConstantRequest
import com.mtd.data.dto.TriggerSmartContractRequest
import com.mtd.data.dto.TronAccountResourceResponse
import com.mtd.data.dto.TronAccountResponse
import com.mtd.data.dto.TronscanNormalResponse
import com.mtd.data.dto.TronscanTokenResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface TronNativeService {
    @GET("wallet/getchainparameters")
    suspend fun getChainParameters(): ChainParameterResponse

    @POST("wallet/triggerconstantcontract")
    suspend fun triggerConstantContract(@Body body: TriggerConstantRequest): EnergyResponse

    @POST("wallet/createtransaction")
    suspend fun createTransaction(@Body body: CreateTxRequest): CreateTransactionResponse

    @POST("wallet/createtransaction")
    suspend fun createTransactionRaw(@Body body: CreateTxRequest): JsonObject

    @POST("wallet/getaccount")
    suspend fun getAccount(@Body body: AccountRequest): TronAccountResponse

    @POST("wallet/getaccountresource")
    suspend fun getAccountResource(@Body body: AccountRequest): TronAccountResourceResponse

    @POST("wallet/triggersmartcontract")
    suspend fun triggerSmartContractRaw(@Body body: TriggerSmartContractRequest): JsonObject

    @POST("wallet/broadcasttransaction")
    suspend fun broadcastTransaction(@Body body: JsonObject): JsonObject
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
