package com.mtd.data.repository

import com.mtd.data.service.CoinGeckoApiService
import com.mtd.domain.model.AssetPrice
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.repository.IMarketDataRepository
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class MarketDataRepositoryImpl @Inject constructor(
    private val coinGeckoApi: CoinGeckoApiService
) : IMarketDataRepository {
    override suspend fun getLatestPrices(assetIds: List<String>): ResultResponse<List<AssetPrice>> {
        return try {
            val idsString = assetIds.joinToString(",")
            val response = coinGeckoApi.getPrices(ids = idsString)
            if (!response.isSuccessful || response.body() == null) {
                throw Exception("Failed to fetch prices from CoinGecko.")
            }
            val priceMap = response.body()!!
            val assetPrices = priceMap.map { (assetId, priceData) ->
                AssetPrice(
                    assetId = assetId,
                    priceUsd = priceData["usd"] ?: BigDecimal.ZERO,
                    priceChanges24h = priceData["usd_24h_change"] ?: BigDecimal.ZERO
                )
            }
            ResultResponse.Success(assetPrices)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }
}