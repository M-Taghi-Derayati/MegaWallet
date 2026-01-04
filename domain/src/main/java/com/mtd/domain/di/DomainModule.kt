package com.mtd.domain.di

import com.mtd.core.registry.AssetRegistry
import com.mtd.domain.model.SwapConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideSwapConfig(assetRegistry: AssetRegistry): SwapConfig {
        return SwapConfig(assetRegistry)
    }
}