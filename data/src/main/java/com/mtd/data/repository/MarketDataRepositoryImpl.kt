package com.mtd.data.repository

import com.mtd.data.dto.OhlcCandle
import com.mtd.data.service.CoinDetailApiService
import com.mtd.data.service.USDTApiService
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.interfaceRepository.IMarketDataRepository
import com.mtd.domain.model.assets.AssetPriceDto
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.ResultResponse
import java.math.BigDecimal
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class MarketDataRepositoryImpl @Inject constructor(
    private val coinGeckoApi: CoinDetailApiService,
    private val usdtPriceApi: USDTApiService
) : IMarketDataRepository {
    override suspend fun getLatestPrices(assetIds: List<String>): ResultResponse<List<AssetPriceDto>> {
        return safeApiCall {
            val idsString = assetIds.joinToString(",") { if (it=="USDT") "$it-USD" else "$it-USDT" }
            val response = coinGeckoApi.getPrices(vsCurrencies = idsString)
            if (!response.isSuccessful || response.body() == null) {
                throw Exception("Failed to fetch prices from CoinDesk.")
            }
            val priceMap = response.body()!!
            priceMap.data.map { (assetId, assets) ->
                AssetPriceDto(
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
            val symbol = coinId.trim().uppercase(Locale.US)

            val rawCandles: List<OhlcCandle> = when (days) {
                "1" -> fetchHourlyCandles(symbol, limit = 24)
                "7" -> fetchDailyCandles(symbol, limit = 7)
                "30" -> fetchDailyCandles(symbol, limit = 30)
                "90" -> fetchDailyCandles(symbol, limit = 90)
                "365" -> fetchDailyCandles(symbol, limit = 365)
                else -> fetchDailyCandles(symbol, limit = 30)
            }

            rawCandles
                .mapNotNull { candle ->
                    val ts = candle.timestamp ?: return@mapNotNull null
                    val closePrice = candle.close ?: candle.open ?: return@mapNotNull null
                    Pair(ts, closePrice)
                }
                .sortedBy { it.first }
        }
    }

    private suspend fun fetchHourlyCandles(symbol: String, limit: Int): List<OhlcCandle> {
        var lastError: String? = null
        for (instrument in buildInstrumentCandidates(symbol)) {
            val response = coinGeckoApi.getHistoricalHours(
                instrument = instrument,
                limit = limit
            )
            if (response.isSuccessful) {
                val candles = response.body()?.data.orEmpty()
                if (candles.isNotEmpty()) return candles
            } else {
                lastError = "HTTP ${response.code()}"
            }
        }
        throw Exception("Failed to fetch hourly chart data for $symbol. ${lastError ?: ""}".trim())
    }

    private suspend fun fetchDailyCandles(symbol: String, limit: Int): List<OhlcCandle> {
        var lastError: String? = null
        for (instrument in buildInstrumentCandidates(symbol)) {
            val response = coinGeckoApi.getHistoricalDays(
                instrument = instrument,
                limit = limit
            )
            if (response.isSuccessful) {
                val candles = response.body()?.data.orEmpty()
                if (candles.isNotEmpty()) return candles
            } else {
                lastError = "HTTP ${response.code()}"
            }
        }
        throw Exception("Failed to fetch daily chart data for $symbol. ${lastError ?: ""}".trim())
    }

    private fun buildInstrumentCandidates(baseSymbol: String): List<String> {
        val base = baseSymbol.trim().uppercase(Locale.US)
        return if (base == "USDT") {
            listOf("USDT-USD")
        } else {
            listOf("$base-USDT", "$base-USD")
        }
    }
}
