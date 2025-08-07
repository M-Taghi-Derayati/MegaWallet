package com.mtd.data


import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtd.data.di.DataModule
import com.mtd.data.di.NetworkModule.Companion.httpLoggingInterceptorProvider
import com.mtd.data.di.NetworkModule.Companion.provideGson
import com.mtd.data.di.NetworkModule.Companion.provideOkHttpClient
import com.mtd.data.repository.MarketDataRepositoryImpl
import com.mtd.data.service.CoinGeckoApiService
import com.mtd.domain.model.ResultResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class MarketDataRepositoryIntegrationTest {

    private lateinit var marketDataRepository: MarketDataRepositoryImpl

    @Before
    fun setUp() {
        // --- ساخت تمام وابستگی‌ها به صورت واقعی برای تست ---
        // ما از Hilt استفاده نمی‌کنیم و همه چیز را به صورت دستی می‌سازیم

        val okHttpClient = provideOkHttpClient(httpLoggingInterceptorProvider())
        val gson = provideGson()

        // ساخت Retrofit و سرویس API به صورت واقعی
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        val coinGeckoApi = retrofit.create(CoinGeckoApiService::class.java)

        // ساخت ریپازیتوری با سرویس API واقعی
        marketDataRepository = MarketDataRepositoryImpl(coinGeckoApi)
    }

    @Test
    fun getLatestPrices_withRealApiCall_shouldReturnValidData() = runTest {
        // --- آماده‌سازی ---
        // لیستی از ارزهای معروف که می‌دانیم در CoinGecko وجود دارند
        val assetIds = listOf("bitcoin", "ethereum", "binancecoin")

        // --- اجرا ---
        val result = marketDataRepository.getLatestPrices(assetIds)

        // --- بررسی ---
        // ۱. بررسی می‌کنیم که نتیجه موفقیت‌آمیز بوده است
        assertTrue(
            "API call should be successful. Result was: $result",
            result is ResultResponse.Success
        )
        val prices = (result as ResultResponse.Success).data

        print("Price ${prices.get(0)}")

        // ۲. بررسی می‌کنیم که برای تمام ارزهای درخواستی، پاسخ دریافت کرده‌ایم
        assertEquals("Should return prices for all 3 requested assets", 3, prices.size)

        // ۳. بررسی می‌کنیم که داده‌ها معتبر هستند (مثلاً قیمت‌ها بزرگتر از صفر هستند)
        prices.forEach { assetPrice ->
            println("Fetched price for ${assetPrice.assetId}: ${assetPrice.priceUsd} USD, 24h Change: ${assetPrice.priceChanges24h}%")

            // بررسی می‌کنیم که شناسه ارز در لیست درخواستی ما بوده است
            assertTrue("Asset ID should be one of the requested IDs", assetIds.contains(assetPrice.assetId))

            // بررسی می‌کنیم که قیمت یک عدد مثبت و معتبر است
            assertTrue("Price for ${assetPrice.assetId} should be greater than zero", assetPrice.priceUsd > BigDecimal.ZERO)

            // درصد تغییر می‌تواند هر عددی باشد، پس فقط وجود آن را چک می‌کنیم (این خط ضروری نیست)
            // assertNotNull(assetPrice.priceChange24h)
        }
    }
}