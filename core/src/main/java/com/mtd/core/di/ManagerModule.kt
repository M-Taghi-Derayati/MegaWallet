package com.mtd.core.di

import com.google.gson.Gson
import com.mtd.core.manager.CacheManager
import com.mtd.core.manager.CoroutineManager
import com.mtd.core.manager.ErrorManager
import com.mtd.core.manager.NavigationManager
import com.mtd.core.manager.NetworkManager
import com.mtd.core.manager.PerformanceManager
import com.mtd.core.manager.ResourceManager
import com.mtd.domain.model.error.ErrorMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ManagerModule {

    @Provides
    @Singleton
    fun provideErrorMapper(): ErrorMapper {
        return ErrorMapper
    }

    @Provides
    @Singleton
    fun provideNetworkManager(
        @ApplicationContext context: android.content.Context
    ): NetworkManager {
        return NetworkManager(context)
    }

    @Provides
    @Singleton
    fun provideErrorManager(
        errorMapper: ErrorMapper,
        networkManager: NetworkManager
    ): ErrorManager {
        return ErrorManager(errorMapper, networkManager)
    }

    @Provides
    @Singleton
    fun provideCacheManager(
        @ApplicationContext context: android.content.Context,
        gson: Gson
    ): CacheManager {
        return CacheManager(context, gson)
    }

    @Provides
    @Singleton
    fun provideResourceManager(): ResourceManager {
        return ResourceManager()
    }

    @Provides
    @Singleton
    fun provideCoroutineManager(
        @ApplicationScope applicationScope: kotlinx.coroutines.CoroutineScope
    ): CoroutineManager {
        return CoroutineManager(applicationScope)
    }

    @Provides
    @Singleton
    fun provideNavigationManager(): NavigationManager {
        return NavigationManager()
    }

    @Provides
    @Singleton
    fun providePerformanceManager(): PerformanceManager {
        return PerformanceManager()
    }
}

