package com.mtd.data.repository

import com.mtd.data.service.CoinDeskApiService
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
    private val coinGeckoApi: CoinDeskApiService,
    private val usdtPriceApi: USDTApiService
) : IMarketDataRepository {
    override suspend fun getLatestPrices(assetIds: List<String>): ResultResponse<List<AssetPrice>> {
        return safeApiCall {
            val idsString = assetIds.joinToString(",") { if (it=="USDT") "$it-USD" else "$it-USDT" }
            val response = coinGeckoApi.getPrices(vsCurrencies = idsString)
            if (!response.isSuccessful || response.body() == null) {
                throw Exception("Failed to fetch prices from CoinDesk.")
            }
            val priceMap = response.body()!!
            priceMap.data.map { (assetId, assets) ->
                AssetPrice(
                    assetId = assetId.replace("-USDT","").replace("-USD",""),
                    priceUsd = assets.priceUsd,
                    priceChanges24h = assets.priceChanges24h
                )
            }
        }
    }

    override suspend fun getUsdToIrrRate(): ResultResponse<CurrencyRate> {
        return safeApiCall {

            val response = usdtPriceApi.getMarketStats()

            if (!response.isSuccessful || response.body() == null || response.body()?.result?.uSDTTMN==null ) {
                throw Exception("Failed to fetch rates from USDT")
            }



            val latestPriceToman = response.body()?.result?.uSDTTMN?.toBigDecimal() ?: BigDecimal.ZERO

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

    override suspend fun getHistoricalPrices(coinId: String, days: String): ResultResponse<List<Pair<Long, Double>>> {
        return safeApiCall {
            val timeframe = when (days) {
                "1" -> "24_hours"
                "7" -> "7_days"
                "30" -> "30_days"
                "90" -> "90_days"
                else -> null
            }
            val response =    coinGeckoApi.getWebMarketChart(vsCurrencies = "")

            if (!response.isSuccessful || response.body() == null) {
                throw Exception("Failed to fetch chart data.")
            }
            
            val body = response.body()!!
            val rawPrices = body.prices ?: body.stats ?: emptyList()
            
            rawPrices.mapNotNull { 
                if (it.size >= 2) Pair(it[0].toLong(), it[1]) else null
            }
        }
    }
}