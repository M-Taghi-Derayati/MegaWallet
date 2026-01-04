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
}