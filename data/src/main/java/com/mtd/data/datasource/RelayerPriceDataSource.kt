package com.mtd.data.datasource

import com.mtd.data.service.RelayerPriceApiService
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.assets.AssetPriceDto
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import java.math.BigDecimal
import javax.inject.Inject

/**
 * PRIMARY market-price source: the relayer's aggregated `/api/v1/prices` (ANDROID_API_CONTRACT §1.6).
 *
 * Per-symbol failure isolation is honored here: a symbol that resolves to `{error}` (or a null `usd`)
 * is **omitted** from the result rather than failing the whole call. Downstream, an omitted symbol
 * means the asset still renders from its balance and simply shows no price ("—") — never a crash and
 * never a hidden asset.
 */
class RelayerPriceDataSource @Inject constructor(
    private val api: RelayerPriceApiService
) {
    suspend fun getLatestPrices(symbols: Pair<List<String>,List<String>> ): ResultResponse<List<AssetPriceDto>> {
        if (symbols.first.isEmpty()) return ResultResponse.Success(emptyList())
        return safeApiCall {
            val response = api.getPrices(symbols.first.distinct().joinToString(","),symbols.second.distinct().joinToString(","))
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw ApiException(ApiError.from(null, response.code()), httpStatus = response.code())
            }
            body.prices.orEmpty().mapNotNull { (symbol, entry) ->
                val usd = entry.usd ?: return@mapNotNull null // failure-isolated: drop error/missing
                AssetPriceDto(
                    assetId = symbol,
                    priceUsd = usd,
                    priceChanges24h = entry.change24h ?: BigDecimal.ZERO
                )
            }
        }
    }
}
