package com.mtd.data.repository

import com.mtd.data.service.CoinGeckoApiService
import com.mtd.data.service.USDTApiService
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.model.AssetPrice
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.repository.IMarketDataRepository
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton


@Singleton

class MarketDataRepositoryImpl @Inject constructor(
    private val coinGeckoApi: CoinGeckoApiService,
    private val usdtPriceApi: USDTApiService
) : IMarketDataRepository {
    override suspend fun getLatestPrices(assetIds: List<String>): ResultResponse<List<AssetPrice>> {
        return safeApiCall {
            val idsString = assetIds.joinToString(",")
            val response = coinGeckoApi.getPrices(ids = idsString)
            if (!response.isSuccessful || response.body() == null) {
                throw Exception("Failed to fetch prices from CoinGecko.")
            }
            val priceMap = response.body()!!
            priceMap.map { (assetId, priceData) ->
                AssetPrice(
                    assetId = assetId,
                    priceUsd = priceData["usd"] ?: BigDecimal.ZERO,
                    priceChanges24h = priceData["usd_24h_change"] ?: BigDecimal.ZERO
                )
            }
        }
    }

    override suspend fun getUsdToIrrRate(): ResultResponse<CurrencyRate> {
        return safeApiCall {
            // فراخوانی API نوبیتکس برای دریافت قیمت تتر به ریال/تومان
            // معمولاً نمادها در نوبیتکس USDTIRT (تومان) یا USDTRLS (ریال) هستند.
            // ما درخواست آمار بازار را می‌دهیم.
            val response = usdtPriceApi.getMarketStats("usdt", "rls")

            if (!response.isSuccessful || response.body() == null || response.body()?.status != "ok") {
                throw Exception("Failed to fetch rates from USDT")
            }

            val stats = response.body()!!.stats["usdt-rls"]
                ?: throw Exception("USDT-RLS pair not found in response")

            val latestPriceRials = stats.latest?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val latestPriceToman = latestPriceRials.divide(BigDecimal.TEN)

            if (latestPriceToman <= BigDecimal.ZERO) {
                throw Exception("Invalid price from USDT")
            }

            CurrencyRate(
                quoteCurrency = "IRR", // We show Toman but internally it's IRR/Toman value
                baseCurrency = "USDT", // Assumed USDT ~ USD
                rate = latestPriceToman,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}