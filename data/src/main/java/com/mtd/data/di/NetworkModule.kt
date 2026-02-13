package com.mtd.data.di

import android.content.Context
import com.google.gson.Gson
import com.mtd.data.service.CoinDeskApiService
import com.mtd.data.service.SwapApiService
import com.mtd.data.service.USDTApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ForWebSocket


@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    val serverIp="195.78.49.45"
//    val serverIp="10.0.2.2"
//    val serverIp="localhost"
    //val serverIp="127.0.0.1"


    @Provides
    @Singleton
    fun provideNetworkConnectionInterceptor(@ApplicationContext context: Context): NetworkConnectionInterceptor {
        return NetworkConnectionInterceptor(context)
    }

        @Provides
        @Singleton
        fun provideGson(): Gson = Gson()

        @Provides
        fun httpLoggingInterceptorProvider(): HttpLoggingInterceptor {
            return HttpLoggingInterceptor((HttpLoggingInterceptor.Logger { message ->
                Timber.log(Timber.treeCount, message)
                Timber.tag("Network").e(message)
            })).apply {
                level=HttpLoggingInterceptor.Level.BODY /*= if (BuildConfig.DEBUG)*/

                /*else
                    HttpLoggingInterceptor.Level.NONE
*/            }
        }

        // --- ارائه‌دهنده‌های شبکه ---
        @Provides
        @Singleton
        fun provideOkHttpClient(
            httpLoggingInterceptor: HttpLoggingInterceptor,
            networkConnectionInterceptor: NetworkConnectionInterceptor
        ): OkHttpClient {
            return OkHttpClient.Builder()
                .addInterceptor(networkConnectionInterceptor)
                .addInterceptor(httpLoggingInterceptor)
                .connectTimeout(35, TimeUnit.SECONDS)
                .writeTimeout(35, TimeUnit.SECONDS)
                .readTimeout(35, TimeUnit.SECONDS)
                .build()
        }



        @Provides
        @Singleton
        fun provideRetrofitBuilder(okHttpClient: OkHttpClient, gson: Gson): Retrofit.Builder {
            return Retrofit.Builder()
                .baseUrl("https://placeholder.com/") // Base URL موقت
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(okHttpClient)

        }

        @Provides
        @Singleton
        fun provideCoinDeskApiService(
            retrofitBuilder: Retrofit.Builder,
            gson: Gson
        ): CoinDeskApiService {
            return retrofitBuilder
                .baseUrl("https://data-api.coindesk.com/") // Base URL مخصوص CoinGecko
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(CoinDeskApiService::class.java)
        }

    @Provides
    @Singleton
    @Named("WebSocketClient") // از Named Qualifier برای تمایز استفاده می‌کنیم
    fun provideWebSocketOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // برای سوکت‌ها تایم‌اوت نمی‌خواهیم
            .pingInterval(20, TimeUnit.SECONDS) // ارسال پینگ خودکار
            .build()
    }


    @Provides
    @Singleton
    fun provideSwapApiService(
        retrofitBuilder: Retrofit.Builder,
        gson: Gson
    ): SwapApiService {
        return retrofitBuilder
            .baseUrl("http://${serverIp}:3000/") // Base URL مخصوص CoinGecko
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SwapApiService::class.java)
    }


    @Provides
    @Singleton
    @ForWebSocket // استفاده از Qualifier
    fun provideWebSocketOrderOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS) // برای سوکت‌ها تایم‌اوت نمی‌خواهیم
            .pingInterval(30, TimeUnit.SECONDS) // OkHttp به صورت خودکار هر ۳۰ ثانیه پینگ می‌فرستد
            .build()
    }


    @Provides
    @Singleton
    fun provideUSDTApiService(
        retrofitBuilder: Retrofit.Builder,
        gson: Gson
    ): USDTApiService {
        return retrofitBuilder
            .baseUrl("https://api.wallex.ir/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(USDTApiService::class.java)
    }

}
