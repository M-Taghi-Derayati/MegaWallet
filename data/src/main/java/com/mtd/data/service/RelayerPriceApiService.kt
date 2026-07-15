package com.mtd.data.service

import com.mtd.data.dto.RelayerPricesResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Relayer market-price endpoint (ANDROID_API_CONTRACT §1.6). Public (no auth), `max-age=15`,
 * per-symbol `{error}` isolation, max 50 symbols. This is the PRIMARY market-price source; the
 * direct CoinDesk call ([CoinDetailApiService.getPrices]) is retained only as a fallback.
 *
 * NOTE: this replaces ONLY market-price display. Wallex ([USDTApiService]) USD→IRR for trading is
 * unrelated and untouched.
 */
interface RelayerPriceApiService {
    @GET("api/v1/prices")
    suspend fun getPrices(
        @Query("symbols") symbols: String ,// comma-separated, e.g. "BTC,ETH,USDT"
        @Query("ids") ids: String?=null // comma-separated, e.g. "BTC,ETH,USDT"
    ): Response<RelayerPricesResponseDto>
}
