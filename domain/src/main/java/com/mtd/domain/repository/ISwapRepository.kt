package com.mtd.domain.repository // یا هر پکیجی که بقیه ریپازیتوری‌ها هستن

import com.mtd.core.model.ExecuteSwapRequest
import com.mtd.domain.model.Quote
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.SwapPair


interface ISwapRepository {
    suspend fun getAvailablePairs(): ResultResponse<List<SwapPair>>
    suspend fun getQuote(fromAssetId: String, toAssetId: String, amount: String): ResultResponse<Quote>
    suspend fun executeSwap(request: ExecuteSwapRequest): ResultResponse<String> // String -> tradeId
}

