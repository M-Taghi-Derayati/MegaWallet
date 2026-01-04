package com.mtd.data.service

import com.mtd.core.model.ExecuteNativeRequest
import com.mtd.domain.model.ExecuteRequest
import com.mtd.domain.model.ExecuteRequest.ExecuteResponse
import com.mtd.core.model.NativeQuoteResponse
import com.mtd.domain.model.QuoteRequest
import com.mtd.core.model.QuoteResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface SwapApiService {

    // برای سواپ ERC20
    @POST("api/v1/swap/quote")
    suspend fun getErc20Quote(@Body request: QuoteRequest): Response<QuoteResponse>

    @POST("api/v1/swap/execute")
    suspend fun executeErc20Swap(@Body request: ExecuteRequest): Response<ExecuteResponse>

    // --- متدهای جدید برای سواپ Native ---

    @POST("api/v1/swap/native/quote")
    suspend fun getNativeQuote(@Body request: QuoteRequest): Response<NativeQuoteResponse>

    @POST("api/v1/swap/native/execute")
    suspend fun executeNativeSwap(@Body request: ExecuteNativeRequest): Response<ExecuteResponse>
}