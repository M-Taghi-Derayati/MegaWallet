package com.mtd.data.di

import com.google.gson.Gson
import com.mtd.data.service.BlockcypherApiService
import com.mtd.data.service.CoinGeckoApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class  NetworkModule {

    companion object {

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
        fun provideOkHttpClient( httpLoggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
            return OkHttpClient.Builder()
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
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
        }

        @Provides
        @Singleton
        fun provideCoinGeckoApiService(retrofitBuilder: Retrofit.Builder): CoinGeckoApiService {
            return retrofitBuilder
                .baseUrl("https://api.coingecko.com/") // Base URL مخصوص CoinGecko
                .build()
                .create(CoinGeckoApiService::class.java)
        }

        @Provides
        @Singleton
        fun provideBlockcypherApiService(retrofitBuilder: Retrofit.Builder): BlockcypherApiService {
            return retrofitBuilder
                .baseUrl("https://blockstream.info/api/") // Base URL برای Mainnet
                .build()
                .create(BlockcypherApiService::class.java)
        }
    }


}