package com.mtd.domain.repository

import com.mtd.domain.model.AssetPrice
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.ResultResponse

interface IMarketDataRepository {
    /**
     * قیمت لحظه‌ای لیستی از ارزها را دریافت می‌کند.
     * @param assetIds لیستی از شناسه‌های ارز (مطابق با API CoinGecko).
     * @return یک Result که حاوی لیستی از قیمت‌های دارایی است.
     */
    suspend fun getLatestPrices(assetIds: List<String>): ResultResponse<List<AssetPrice>>

    /**
     * دریافت نرخ تبدیل تتر به تومان.
     */
    suspend fun getUsdToIrrRate(): ResultResponse<CurrencyRate>
    
    /**
     * دریافت تاریخچه قیمت برای چارت.
     * @param coinId شناسه کوین گکو (مثلاً "bitcoin")
     * @param days تعداد روزهای تاریخچه (مثلاً "1", "7", "30")
     */
    suspend fun getHistoricalPrices(coinId: String, days: String): ResultResponse<List<Pair<Long, Double>>>
}