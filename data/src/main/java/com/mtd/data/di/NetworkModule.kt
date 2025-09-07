package com.mtd.data.di

import android.content.Context
import com.google.gson.Gson
import com.mtd.core.socket.UnsafeTrustManager
import com.mtd.data.service.CoinGeckoApiService
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
import javax.inject.Singleton
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {


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
                level = //if (BuildConfig.BUILD_TYPE=="debug")
                    HttpLoggingInterceptor.Level.BODY
                //else
                // HttpLoggingInterceptor.Level.NONE

            }
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
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
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
        fun provideCoinGeckoApiService(
            retrofitBuilder: Retrofit.Builder,
            gson: Gson
        ): CoinGeckoApiService {
            return retrofitBuilder
                .baseUrl("https://api.coingecko.com/") // Base URL مخصوص CoinGecko
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(CoinGeckoApiService::class.java)
        }

    @Provides
    @Singleton
    @Named("WebSocketClient") // از Named Qualifier برای تمایز استفاده می‌کنیم
    fun provideWebSocketOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .sslSocketFactory( UnsafeTrustManager.sslSocketFactory,
                UnsafeTrustManager.trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .readTimeout(0, TimeUnit.MILLISECONDS) // برای سوکت‌ها تایم‌اوت نمی‌خواهیم
            .pingInterval(20, TimeUnit.SECONDS) // ارسال پینگ خودکار
            .build()
    }



}
