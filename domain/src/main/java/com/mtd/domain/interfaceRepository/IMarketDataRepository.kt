package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.assets.AssetPriceDto

interface IMarketDataRepository {
    /**
     * قیمت لحظه‌ای لیستی از ارزها را دریافت می‌کند.
     * @param assetIds لیستی از شناسه‌های ارز (مطابق با API CoinGecko).
     * @return یک Result که حاوی لیستی از قیمت‌های دارایی است.
     */
    suspend fun getLatestPrices(assetIds: List<String>): ResultResponse<List<AssetPriceDto>>

    /**
     * دریافت نرخ تبدیل تتر به تومان.
     */
    suspend fun getUsdToIrrRate(): ResultResponse<CurrencyRate>
    
    /**
     * دریافت تاریخچه قیمت برای چارت.
     * @param coinId نماد پایه دارایی (مثلاً "BTC")
     * @param days تعداد روزهای تاریخچه (مثلاً "1", "7", "30")
     */
    suspend fun getHistoricalPrices(coinId: String, days: String): ResultResponse<List<Pair<Long, Double>>>
}
