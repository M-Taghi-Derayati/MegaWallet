package com.mtd.data.di

import android.content.Context
import com.google.gson.Gson
import com.mtd.core.utils.TransactionRecordAdapter
import com.mtd.data.BuildConfig
import com.mtd.data.dto.HistoryItemDto
import com.mtd.data.dto.HistoryItemDtoDeserializer
import com.mtd.data.network.interceptor.AuthInterceptor
import com.mtd.data.network.interceptor.IdempotencyInterceptor
import com.mtd.data.network.wire.BigIntegerStringAdapter
import com.mtd.data.service.AuthApiService
import com.mtd.data.service.CoinDetailApiService
import com.mtd.data.service.ConfigApiService
import com.mtd.data.service.GaslessApiService
import com.mtd.data.service.GrowthApiService
import com.mtd.data.service.MobileProxyApiService
import com.mtd.data.service.NotificationApiService
import com.mtd.data.service.RelayerPriceApiService
import com.mtd.data.service.SwapApiService
import com.mtd.data.service.USDTApiService
import com.mtd.domain.interfaceRepository.ITokenStore
import com.mtd.domain.model.TransactionRecord
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
import java.math.BigInteger
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

    // Centralized in BuildConfig (Phase 1) — no hardcoded IPs in Kotlin. Retained as `serverIp`
    // for NotificationSocketManager until the /ws + JWT-at-upgrade rewire in Phase 4.
    val serverIp: String = BuildConfig.RELAYER_HOST


    @Provides
    @Singleton
    fun provideNetworkConnectionInterceptor(@ApplicationContext context: Context): NetworkConnectionInterceptor {
        return NetworkConnectionInterceptor(context)
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = com.google.gson.GsonBuilder()
        .registerTypeAdapter(TransactionRecord::class.java, TransactionRecordAdapter())
        // Phase 1 — enforce BigInt-as-String for every BigInteger field across all DTOs.
        .registerTypeAdapter(BigInteger::class.java, BigIntegerStringAdapter())
        // Phase 2 — polymorphic history items dispatched by the `type` discriminator.
        .registerTypeAdapter(HistoryItemDto::class.java, HistoryItemDtoDeserializer())
        .create()

        @Provides
        fun httpLoggingInterceptorProvider(): HttpLoggingInterceptor {
            return HttpLoggingInterceptor((HttpLoggingInterceptor.Logger { message ->
                Timber.log(Timber.treeCount, message)
                Timber.tag("Network").e(message)
            })).apply {
                // Phase 1 security: full BODY logging (signatures / prepareToken / quoteToken / JWT)
                // only in debug builds — release drops to BASIC.
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.BASIC
                }
                redactHeader("Authorization")
                redactHeader("Cookie")
                redactHeader("X-Idempotency-Key")
            }
        }

        // --- ارائه‌دهنده‌های شبکه ---
        @Provides
        @Singleton
        fun provideOkHttpClient(
            httpLoggingInterceptor: HttpLoggingInterceptor,
            networkConnectionInterceptor: NetworkConnectionInterceptor,
            // Default lets manual (test) callers omit it; Hilt always injects the real SecureTokenStore.
            tokenStore: ITokenStore = com.mtd.data.repository.auth.NoOpTokenStore
        ): OkHttpClient {
            return OkHttpClient.Builder()
                .addInterceptor(networkConnectionInterceptor)
                // Auth + idempotency are host-scoped to the relayer (never leak to CoinDesk/Wallex).
                .addInterceptor(AuthInterceptor(tokenStore, BuildConfig.RELAYER_HOST))
                .addInterceptor(IdempotencyInterceptor(BuildConfig.RELAYER_HOST))
                .addInterceptor(httpLoggingInterceptor) // last → logs the final, mutated headers
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
        ): CoinDetailApiService {
            return retrofitBuilder
                .baseUrl("https://rest.coincap.io/")
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(CoinDetailApiService::class.java)
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
    fun provideGaslessApiService(
        retrofitBuilder: Retrofit.Builder,
        gson: Gson
    ): GaslessApiService {
        return retrofitBuilder
            .baseUrl(BuildConfig.RELAYER_BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(GaslessApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideRelayerPriceApiService(
        retrofitBuilder: Retrofit.Builder,
        gson: Gson
    ): RelayerPriceApiService {
        return retrofitBuilder
            .baseUrl(BuildConfig.RELAYER_BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(RelayerPriceApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideMobileProxyApiService(
        retrofitBuilder: Retrofit.Builder,
        gson: Gson
    ): MobileProxyApiService {
        return retrofitBuilder
            .baseUrl(BuildConfig.RELAYER_BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(MobileProxyApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideConfigApiService(
        retrofitBuilder: Retrofit.Builder,
        gson: Gson
    ): ConfigApiService {
        return retrofitBuilder
            .baseUrl(BuildConfig.RELAYER_BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ConfigApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideCapabilityApiService(
        retrofitBuilder: Retrofit.Builder,
        gson: Gson
    ): com.mtd.data.service.CapabilityApiService {
        return retrofitBuilder
            .baseUrl(BuildConfig.RELAYER_BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(com.mtd.data.service.CapabilityApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthApiService(
        retrofitBuilder: Retrofit.Builder,
        gson: Gson
    ): AuthApiService {
        return retrofitBuilder
            .baseUrl(BuildConfig.RELAYER_BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideGrowthApiService(
        retrofitBuilder: Retrofit.Builder,
        gson: Gson
    ): GrowthApiService {
        return retrofitBuilder
            .baseUrl(BuildConfig.RELAYER_BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(GrowthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideSwapApiService(
        retrofitBuilder: Retrofit.Builder,
        gson: Gson
    ): SwapApiService {
        return retrofitBuilder
            .baseUrl(BuildConfig.RELAYER_BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SwapApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideNotificationApiService(
        retrofitBuilder: Retrofit.Builder,
        gson: Gson
    ): NotificationApiService {
        return retrofitBuilder
            .baseUrl(BuildConfig.RELAYER_BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(NotificationApiService::class.java)
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
