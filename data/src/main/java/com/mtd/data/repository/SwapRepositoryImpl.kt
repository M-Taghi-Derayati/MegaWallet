package com.mtd.data.repository

import com.mtd.data.service.SwapApiService
import com.mtd.core.model.ExecuteNativeRequest
import com.mtd.domain.model.ExecuteRequest
import com.mtd.core.model.NativeQuoteResponse
import com.mtd.domain.model.QuoteRequest
import com.mtd.core.model.QuoteResponse
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.SwapPair
import com.mtd.domain.repository.ISwapRepository
import com.mtd.data.utils.safeApiCall
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwapRepositoryImpl @Inject constructor(private val swapApiService: SwapApiService) :
    ISwapRepository {
    // فعلاً اینها رو خالی میذاریم چون تمرکز ما روی امضا کردنه
    override suspend fun getAvailablePairs(): ResultResponse<List<SwapPair>> {

        delay(500) // شبیه‌سازی تاخیر شبکه
        // --- داده‌های هاردکد ---
        val pairs = listOf(
            SwapPair(
                fromAssetId = "USDT-SEPOLIA",
                fromAssetName = "Tether USD",
                fromAssetSymbol = "USDT",
                fromAssetIconUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/825.png",
                fromNetworkName = "Sepolia",
                fromNetworkIconUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/1027.png",

                toAssetId = "ETH-SEPOLIA",
                toAssetName = "Ethereum",
                toAssetSymbol = "ETH",
                toAssetIconUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/1027.png",
                toNetworkName = "Sepolia",
                toNetworkIconUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/1027.png"
            ),
            SwapPair(
                fromAssetId = "USDC-SEPOLIA",
                fromAssetName = "USD Coin",
                fromAssetSymbol = "USDC",
                fromAssetIconUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/3408.png",
                fromNetworkName = "Sepolia",
                fromNetworkIconUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/1027.png",

                toAssetId = "ETH-SEPOLIA",
                toAssetName = "Ethereum",
                toAssetSymbol = "ETH",
                toAssetIconUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/1027.png",
                toNetworkName = "Sepolia",
                toNetworkIconUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/1027.png"
            ),

            SwapPair(
                fromAssetId = "ETH-SEPOLIA",
                fromAssetName = "Ethereum",
                fromAssetSymbol = "ETH",
                fromAssetIconUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/1027.png",
                fromNetworkName = "Sepolia",
                fromNetworkIconUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/1027.png",

                toAssetId = "USDT-SEPOLIA",
                toAssetName = "Tether USD",
                toAssetSymbol = "USDT",
                toAssetIconUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/825.png",
                toNetworkName = "Sepolia",
                toNetworkIconUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/1027.png"
            )
        )
        return ResultResponse.Success(pairs)

       }

    override suspend fun getErc20Quote(
        fromAssetSymbol: String,
        fromNetworkId: String,
        toAssetSymbol: String,
        toNetworkId: String?,
        amount: Double,
        recipientAddress: String?
    ): ResultResponse<QuoteResponse> {
        return safeApiCall {
            val request = QuoteRequest(
                fromAssetSymbol = fromAssetSymbol,
                fromNetworkId = fromNetworkId.lowercase(),
                toAssetSymbol = toAssetSymbol,
                amount = amount,
                recipientAddress = recipientAddress,
                toNetworkId = toNetworkId?.lowercase()
            )
            val response = swapApiService.getErc20Quote(request)

            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                throw Exception("API Error ${response.code()}: $errorBody")
            }
        }
    }


    override suspend fun executeErc20Swap(request: ExecuteRequest): ResultResponse<String> {
        return safeApiCall {
            val response = swapApiService.executeErc20Swap(request)

            if (response.isSuccessful && response.body() != null) {
                response.body()!!.tradeId
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                throw Exception("API Error ${response.code()}: $errorBody")
            }
        }
    }

    override suspend fun getNativeQuote(
        fromAssetSymbol: String,
        fromNetworkId: String,
        toAssetSymbol: String,
        toNetworkId: String?,
        amount: Double,
        userAddress: String,
        recipientAddress: String?
    ): ResultResponse<NativeQuoteResponse> {
        return safeApiCall {
            val request = QuoteRequest(
                fromAssetSymbol = fromAssetSymbol,
                fromNetworkId = fromNetworkId,
                toAssetSymbol = toAssetSymbol,
                amount = amount,
                recipientAddress = recipientAddress,
                toNetworkId = toNetworkId,
                userAddress = userAddress
            )
            val response = swapApiService.getNativeQuote(request)

            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                throw Exception("API Error ${response.code()}: $errorBody")
            }
        }
    }

    override suspend fun executeNativeSwap(request: ExecuteNativeRequest): ResultResponse<String> {
        return safeApiCall {
            val response = swapApiService.executeNativeSwap(request)

            if (response.isSuccessful && response.body() != null) {
                response.body()!!.tradeId
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                throw Exception("API Error ${response.code()}: $errorBody")
            }
        }

    }
}