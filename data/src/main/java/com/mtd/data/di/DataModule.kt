package com.mtd.data.di

import android.content.Context
import android.content.SharedPreferences
import com.mtd.core.di.CryptoModule
import com.mtd.data.GoogleAuthManager
import com.mtd.data.datasource.GoogleDriveDataSource
import com.mtd.data.datasource.ICloudDataSource
import com.mtd.data.datasource.DefaultBlockchainConnectionModeProvider
import com.mtd.data.repository.BackupRepositoryImpl
import com.mtd.data.repository.CachedWalletBalanceReaderImpl
import com.mtd.data.repository.CloudWalletBalanceCalculatorImpl
import com.mtd.data.repository.gasless.EvmGaslessRepositoryImpl
import com.mtd.domain.interfaceRepository.IBackupRepository
import com.mtd.domain.interfaceRepository.IGaslessEvmRepository
import com.mtd.domain.interfaceRepository.IGaslessTronRepository
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.data.repository.MarketDataRepositoryImpl
import com.mtd.data.repository.gasless.TronGaslessRepositoryImpl
import com.mtd.data.repository.UserPreferencesRepositoryImpl
import com.mtd.data.repository.SendAssetDataSourceImpl
import com.mtd.data.repository.transfer.UnifiedTransferCoordinator
import com.mtd.data.repository.WalletBalanceSynchronizerImpl
import com.mtd.data.repository.WalletRepositoryImpl
import com.mtd.domain.model.IUserPreferencesRepository
import com.mtd.domain.interfaceRepository.IAuthManager
import com.mtd.domain.interfaceRepository.IBlockchainConnectionModeProvider
import com.mtd.domain.interfaceRepository.ICachedWalletBalanceReader
import com.mtd.domain.interfaceRepository.ICloudBackupDataSource
import com.mtd.domain.interfaceRepository.ICloudWalletBalanceCalculator
import com.mtd.domain.interfaceRepository.IMarketDataRepository
import com.mtd.domain.interfaceRepository.ISendAssetDataSource
import com.mtd.domain.interfaceRepository.IUnifiedTransferCoordinator
import com.mtd.domain.interfaceRepository.IWalletBalanceSynchronizer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@InstallIn(SingletonComponent::class)
@Module(includes = [NetworkModule::class,CryptoModule::class])
abstract class DataModule {

    @Binds
    abstract fun bindWalletRepository(
        walletRepositoryImpl: WalletRepositoryImpl
    ): IWalletRepository

    @Binds
    @Singleton
    abstract fun bindGaslessEvmRepository(
        impl: EvmGaslessRepositoryImpl
    ): IGaslessEvmRepository

    @Binds
    @Singleton
    abstract fun bindGaslessTronRepository(
        impl: TronGaslessRepositoryImpl
    ): IGaslessTronRepository

    @Binds
    @Singleton
    abstract fun bindBlockchainConnectionModeProvider(
        impl: DefaultBlockchainConnectionModeProvider
    ): IBlockchainConnectionModeProvider

    @Binds
    abstract fun bindUserPreferencesRepository(
        userPreferencesRepositoryImpl: UserPreferencesRepositoryImpl
    ): IUserPreferencesRepository



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
    abstract fun bindCloudBackupDataSource(
        googleDriveDataSource: GoogleDriveDataSource
    ): ICloudBackupDataSource

    @Binds
    @Singleton
    abstract fun bindMarketDataRepository(impl: MarketDataRepositoryImpl): IMarketDataRepository

    @Binds
    abstract fun bindCloudWalletBalanceCalculator(
        impl: CloudWalletBalanceCalculatorImpl
    ): ICloudWalletBalanceCalculator

    @Binds
    abstract fun bindCachedWalletBalanceReader(
        impl: CachedWalletBalanceReaderImpl
    ): ICachedWalletBalanceReader

    @Binds
    abstract fun bindWalletBalanceSynchronizer(
        impl: WalletBalanceSynchronizerImpl
    ): IWalletBalanceSynchronizer

    @Binds
    abstract fun bindSendAssetDataSource(
        impl: SendAssetDataSourceImpl
    ): ISendAssetDataSource

    @Binds
    @Singleton
    abstract fun bindUnifiedTransferCoordinator(
        impl: UnifiedTransferCoordinator
    ): IUnifiedTransferCoordinator


    companion object {

        @Provides
        @Singleton
        fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
            return context.getSharedPreferences("mega_wallet_user_prefs", Context.MODE_PRIVATE)
        }
    }

}
