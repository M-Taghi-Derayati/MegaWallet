package com.mtd.core.di

import android.content.Context
import com.mtd.core.encryption.SecureStorage
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.registry.BlockchainRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun provideSecureStorage(@ApplicationContext context: Context): SecureStorage {
        return SecureStorage(context)
    }

    @Provides
    @Singleton
    fun provideBlockchainRegistry(@ApplicationContext context: Context): BlockchainRegistry {
        return BlockchainRegistry().apply {
            loadNetworksFromAssets(context)
        }
    }

    @Provides
    @Singleton
    fun provideKeyManager(registry: BlockchainRegistry): KeyManager {
        return KeyManager(registry)
    }
}