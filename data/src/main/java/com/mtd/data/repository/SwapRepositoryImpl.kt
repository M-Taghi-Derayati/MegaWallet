package com.mtd.data.repository

import android.util.Log
import com.mtd.core.model.ExecuteSwapRequest
import com.mtd.domain.model.Quote
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.SwapPair
import com.mtd.domain.repository.ISwapRepository
import kotlinx.coroutines.delay
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwapRepositoryImpl @Inject constructor() : ISwapRepository {
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
            // ... می‌تونید جفت‌ارزهای بیشتری اضافه کنید
        )
        return ResultResponse.Success(pairs)

       }
    override suspend fun getQuote(
        fromAssetId: String,
        toAssetId: String,
        amount: String
    ): ResultResponse<Quote> {
        delay(300)
        val amountIn = amount.toBigDecimalOrNull() ?: return ResultResponse.Error(Exception("Invalid amount"))

        // --- قیمت‌گذاری هاردکد ---
        val rate = if (fromAssetId.startsWith("USDT")) BigDecimal("0.0005") else BigDecimal("1950.5") // 1 USDT = 0.0005 ETH | 1 ETH = 1950.5 USDT
        val feeRate = BigDecimal("0.001") // کارمزد ۱٪

        val amountOut = amountIn * rate * (BigDecimal.ONE - feeRate)
        val feeAmount = amountIn * feeRate

        return ResultResponse.Success(
            Quote(
                quoteId = "fake-quote-${System.currentTimeMillis()}",
                fromAssetId = fromAssetId,
                fromAmount = amount,
                fromAssetSymbol = fromAssetId.split("-").first(),
                toAssetId = toAssetId,
                receiveAmount = amountOut.toPlainString(),
                receiveAssetSymbol = toAssetId.split("-").first(),
                feeAmount = feeAmount.toPlainString(),
                feeAssetSymbol = fromAssetId.split("-").first()
            )
        )



    }

    override suspend fun executeSwap(request: ExecuteSwapRequest): ResultResponse<String> {



        delay(1500)
        Log.d("FakeSwapRepository", "Execute request received: $request")
        return ResultResponse.Success("fake-trade-id-${System.currentTimeMillis()}")
    }
}