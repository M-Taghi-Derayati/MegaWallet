package com.mtd.data.di

import android.content.Context
import android.content.SharedPreferences
import com.mtd.core.di.CryptoModule
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.GoogleAuthManager
import com.mtd.data.datasource.GoogleDriveDataSource
import com.mtd.data.datasource.ICloudDataSource
import com.mtd.data.datasource.RemoteDataSource
import com.mtd.data.repository.BackupRepositoryImpl
import com.mtd.data.repository.IBackupRepository
import com.mtd.data.repository.IWalletRepository
import com.mtd.data.repository.MarketDataRepositoryImpl
import com.mtd.data.repository.SwapRepositoryImpl
import com.mtd.data.repository.UserPreferencesRepositoryImpl
import com.mtd.data.repository.WalletRepositoryImpl
import com.mtd.domain.model.IUserPreferencesRepository
import com.mtd.domain.repository.IAuthManager
import com.mtd.domain.repository.IMarketDataRepository
import com.mtd.domain.repository.ISwapRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton


@InstallIn(SingletonComponent::class)
@Module(includes = [NetworkModule::class,CryptoModule::class])
abstract class DataModule {

    @Binds
    abstract fun bindWalletRepository(
        walletRepositoryImpl: WalletRepositoryImpl
    ): IWalletRepository

    @Binds
    abstract fun bindUserPreferencesRepository(
        userPreferencesRepositoryImpl: UserPreferencesRepositoryImpl
    ): IUserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindSwapRepository(impl: SwapRepositoryImpl): ISwapRepository


    @Binds
    abstract fun bindBackupRepository(
        backupRepositoryImpl: BackupRepositoryImpl
    ): IBackupRepository

    @Binds
    abstract fun bindAuthManager(
        googleAuthManager: GoogleAuthManager
    ): IAuthManager

    @Binds
    abstract fun bindCloudDataSource(
        googleDriveDataSource: GoogleDriveDataSource
    ): ICloudDataSource

    @Binds
    @Singleton
    abstract fun bindMarketDataRepository(impl: MarketDataRepositoryImpl): IMarketDataRepository


    companion object {

        @Provides
        @Singleton
        fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
            return context.getSharedPreferences("mega_wallet_user_prefs", Context.MODE_PRIVATE)
        }


        @Provides
        @Singleton
        fun provideRemoteDataSource(
            blockchainRegistry: BlockchainRegistry,
            userPreferencesRepository: IUserPreferencesRepository, // این وابستگی جدید است
            okHttpClient: OkHttpClient,
            retrofitBuilder: Retrofit.Builder
        ): RemoteDataSource {
            return RemoteDataSource(blockchainRegistry, userPreferencesRepository, okHttpClient,retrofitBuilder)
        }
    }

}