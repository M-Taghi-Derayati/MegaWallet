package com.mtd.data.repository

import com.mtd.data.datasource.RelayerPriceDataSource
import com.mtd.data.dto.OhlcCandle
import com.mtd.data.service.CoinDetailApiService
import com.mtd.data.service.USDTApiService
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.interfaceRepository.IMarketDataRepository
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.assets.AssetPriceDto
import timber.log.Timber
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class MarketDataRepositoryImpl @Inject constructor(
    private val coinDetailApi: CoinDetailApiService,
    private val usdtPriceApi: USDTApiService,
    private val relayerPriceDataSource: RelayerPriceDataSource
) : IMarketDataRepository {
    override suspend fun getLatestPrices(assetIds: Pair<List<String>,List<String>>): ResultResponse<List<AssetPriceDto>> {
        when (val primary = relayerPriceDataSource.getLatestPrices(assetIds)) {
            is ResultResponse.Success -> if (primary.data.isNotEmpty()) return primary
            is ResultResponse.Error ->
                Timber.w(primary.exception, "Relayer /api/v1/prices failed; falling back to CoinDesk")
        }
        return fetchLatestPricesFromCoinDesk(assetIds)
    }

    /** Legacy direct-CoinDesk path, retained only as the market-price fallback. */
    private suspend fun fetchLatestPricesFromCoinDesk(
        assetIds: Pair<List<String>,List<String>>
    ): ResultResponse<List<AssetPriceDto>> {
        return safeApiCall {
            // TASK-16: always query by the `ids` param. The old multi-asset branch called
            // `getPrices(idsString)` positionally, which bound the ids to the *first* parameter
            // (`search`) and left `ids` null — so multi-asset price lookups silently returned the
            // wrong/empty result. `search`/`symbolString` were never actually needed here.
            val idsString = assetIds.second.joinToString(",")
            val response = coinDetailApi.getPrices(ids = idsString)

            if (!response.isSuccessful || response.body() == null) {
                throw Exception("Failed to fetch prices from CoinDesk.")
            }
            val priceMap = response.body()!!
            priceMap.data.map { it ->
                AssetPriceDto(
                    assetId = it.assetId,
                    priceUsd = it.priceUsd,
                    priceChanges24h = it.priceChanges24h
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
                // TASK-56 — the label now matches the value. Wallex's field is `uSDTTMN`, i.e. تومان,
                // which is why the local is called `latestPriceToman`; labelling it "IRR" claimed a
                // unit ten times smaller than what is actually in there. `FiatConversion.tomanPerUsd`
                // resolves the unit from this string, so a wrong label is a 10× money error.
                quoteCurrency = "TMN",
                baseCurrency = "USDT", // Assumed USDT ~ USD
                rate = latestPriceToman,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    override suspend fun getHistoricalPrices(coinName: String, days: String): ResultResponse<List<Pair<Long, String>>> {
        return safeApiCall {
            val nowMillis = Instant.now().toEpochMilli()
           val candle= if (days=="1"){
                val oneDayAgoMillis = Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli()
                 fetchDailyCandles(coinName,oneDayAgoMillis,nowMillis,"h1")
            }else{
                val oneDayAgoMillis = Instant.now().minus(days.toLong(), ChronoUnit.DAYS).toEpochMilli()
                 fetchDailyCandles(coinName,oneDayAgoMillis,nowMillis)
            }
            candle
                .mapNotNull { candle ->
                    val ts = candle.time ?: return@mapNotNull null
                    val closePrice = candle.priceUsd ?: return@mapNotNull null
                    Pair(ts, closePrice)
                }
                .sortedBy { it.first }
        }
    }

    private suspend fun fetchDailyCandles(coinName: String, startDate: Long,endDate:Long,interval:String="d1"): List<OhlcCandle> {
        var lastError: String? = null

            val response = coinDetailApi.getHistorical(coin = coinName,startDate,endDate, interval = interval)
            if (response.isSuccessful) {
                val candles = response.body()?.data.orEmpty()
                if (candles.isNotEmpty()) return candles
            } else {
                lastError = "HTTP ${response.code()}"
            }

        throw Exception("Failed to fetch hourly chart data for $coinName. ${lastError ?: ""}".trim())
    }

}
