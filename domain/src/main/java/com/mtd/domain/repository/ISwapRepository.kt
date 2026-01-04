package com.mtd.domain.repository // یا هر پکیجی که بقیه ریپازیتوری‌ها هستن

import com.mtd.core.model.ExecuteNativeRequest
import com.mtd.domain.model.ExecuteRequest
import com.mtd.core.model.NativeQuoteResponse
import com.mtd.core.model.QuoteResponse
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.SwapPair


interface ISwapRepository {
    suspend fun getAvailablePairs(): ResultResponse<List<SwapPair>>
    suspend fun getErc20Quote(
        fromAssetSymbol: String,
        fromNetworkId: String,
        toAssetSymbol: String,
        toNetworkId: String?,
        amount: Double,
        recipientAddress: String?
        ): ResultResponse<QuoteResponse>


    suspend fun executeErc20Swap(request: ExecuteRequest): ResultResponse<String>


    suspend fun getNativeQuote(
        fromAssetSymbol: String,
        fromNetworkId: String,
        toAssetSymbol: String,
        toNetworkId: String?,
        amount: Double,
        userAddress: String, // <<-- userAddress برای دریافت nonce لازم است
        recipientAddress: String?
    ): ResultResponse<NativeQuoteResponse>

    suspend fun executeNativeSwap(request: ExecuteNativeRequest): ResultResponse<String>
}

