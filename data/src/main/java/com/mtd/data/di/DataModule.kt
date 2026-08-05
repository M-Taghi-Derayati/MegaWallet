package com.mtd.data.di

import android.content.Context
import android.content.SharedPreferences
import com.mtd.core.di.CryptoModule
import com.mtd.data.auth.GoogleAuthManager
import com.mtd.data.config.ConfigSignatureVerifier
import com.mtd.data.config.Secp256k1ConfigSignatureVerifier
import com.mtd.data.datasource.DefaultBlockchainConnectionModeProvider
import com.mtd.data.datasource.DefaultTestnetVisibilityProvider
import com.mtd.data.datasource.GoogleDriveDataSource
import com.mtd.data.datasource.ICloudDataSource
import com.mtd.data.repository.BackupRepositoryImpl
import com.mtd.data.repository.CachedWalletBalanceReaderImpl
import com.mtd.data.repository.CloudWalletBalanceCalculatorImpl
import com.mtd.data.repository.GsonCloudWalletBackupCodec
import com.mtd.data.repository.MarketDataRepositoryImpl
import com.mtd.data.repository.FiatCurrencyProvider
import com.mtd.data.repository.UsdToIrrRateProvider
import com.mtd.data.repository.MonitoringRepositoryImpl
import com.mtd.data.repository.SendAssetDataSourceImpl
import com.mtd.data.repository.TransactionStatusRepositoryImpl
import com.mtd.data.repository.UserPreferencesRepositoryImpl
import com.mtd.data.repository.WalletBalanceSynchronizerImpl
import com.mtd.data.repository.WalletRepositoryImpl
import com.mtd.data.device.PlayIntegrityTokenProvider
import com.mtd.data.device.ResilientDeviceIdProvider
import com.mtd.data.device.UnavailablePlayIntegrityTokenProvider
import com.mtd.data.repository.assets.MergedAssetCatalog
import com.mtd.data.repository.assets.TokenDiscoveryRepositoryImpl
import com.mtd.data.repository.assets.UserTokenRepositoryImpl
import com.mtd.data.repository.auth.AuthRepositoryImpl
import com.mtd.data.repository.auth.EvmAuthMessageSigner
import com.mtd.data.repository.auth.SecureTokenStore
import com.mtd.data.repository.realtime.RealtimeConnectionGateway
import com.mtd.data.repository.gasless.DirectGaslessChainReader
import com.mtd.data.repository.gasless.EvmGaslessRepositoryImpl
import com.mtd.data.repository.gasless.TronGaslessRepositoryImpl
import com.mtd.data.repository.growth.GrowthRepositoryImpl
import com.mtd.data.repository.notification.NotificationRepositoryImpl
import com.mtd.data.repository.swap.SwapRepositoryImpl
import com.mtd.data.repository.transfer.UnifiedTransferCoordinator
import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.interfaceRepository.IAuthManager
import com.mtd.domain.interfaceRepository.IAuthMessageSigner
import com.mtd.domain.interfaceRepository.IAuthRepository
import com.mtd.domain.interfaceRepository.IRealtimeConnectionGateway
import com.mtd.domain.interfaceRepository.IBackupRepository
import com.mtd.domain.interfaceRepository.IDeviceIdProvider
import com.mtd.domain.interfaceRepository.IBlockchainConnectionModeProvider
import com.mtd.domain.interfaceRepository.ITestnetVisibilityProvider
import com.mtd.domain.interfaceRepository.ICachedWalletBalanceReader
import com.mtd.domain.interfaceRepository.ICloudBackupDataSource
import com.mtd.domain.interfaceRepository.ICloudWalletBackupCodec
import com.mtd.domain.interfaceRepository.ICloudWalletBalanceCalculator
import com.mtd.domain.interfaceRepository.IGaslessChainReader
import com.mtd.domain.interfaceRepository.IGaslessEvmRepository
import com.mtd.domain.interfaceRepository.IGaslessTronRepository
import com.mtd.domain.interfaceRepository.IGrowthRepository
import com.mtd.domain.interfaceRepository.IManageableAssetCatalog
import com.mtd.domain.interfaceRepository.IMarketDataRepository
import com.mtd.domain.interfaceRepository.INotificationRepository
import com.mtd.domain.interfaceRepository.IMonitoringRepository
import com.mtd.domain.interfaceRepository.ISwapRepository
import com.mtd.domain.interfaceRepository.ISendAssetDataSource
import com.mtd.domain.interfaceRepository.ITokenDiscoveryRepository
import com.mtd.domain.interfaceRepository.ITokenStore
import com.mtd.domain.interfaceRepository.ITransactionStatusRepository
import com.mtd.domain.interfaceRepository.IUnifiedTransferCoordinator
import com.mtd.domain.interfaceRepository.IWalletBalanceSynchronizer
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.interfaceRepository.IFiatCurrencyProvider
import com.mtd.domain.interfaceRepository.IUsdToIrrRateProvider
import com.mtd.domain.interfaceRepository.IUserPreferencesRepository
import com.mtd.domain.interfaceRepository.IUserTokenRepository
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
    abstract fun bindTokenStore(
        impl: SecureTokenStore
    ): ITokenStore

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): IAuthRepository

    @Binds
    @Singleton
    abstract fun bindDeviceIdProvider(
        impl: ResilientDeviceIdProvider
    ): IDeviceIdProvider

    @Binds
    @Singleton
    abstract fun bindAuthMessageSigner(
        impl: EvmAuthMessageSigner
    ): IAuthMessageSigner

    @Binds
    @Singleton
    abstract fun bindRealtimeConnectionGateway(
        impl: RealtimeConnectionGateway
    ): IRealtimeConnectionGateway

    @Binds
    @Singleton
    abstract fun bindPlayIntegrityTokenProvider(
        impl: UnavailablePlayIntegrityTokenProvider
    ): PlayIntegrityTokenProvider

    @Binds
    @Singleton
    abstract fun bindTransactionStatusRepository(
        impl: TransactionStatusRepositoryImpl
    ): ITransactionStatusRepository

    @Binds
    @Singleton
    abstract fun bindMonitoringRepository(
        impl: MonitoringRepositoryImpl
    ): IMonitoringRepository

    @Binds
    @Singleton
    abstract fun bindGaslessChainReader(
        impl: DirectGaslessChainReader
    ): IGaslessChainReader

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

    // TASK-53 — نمایش شبکه‌های تست؛ همان الگوی بالا.
    @Binds
    @Singleton
    abstract fun bindTestnetVisibilityProvider(
        impl: DefaultTestnetVisibilityProvider
    ): ITestnetVisibilityProvider

    @Binds
    abstract fun bindUserPreferencesRepository(
        userPreferencesRepositoryImpl: UserPreferencesRepositoryImpl
    ): IUserPreferencesRepository

    // Phase 3 — secp256k1 verifier for the dynamic config bundle. ConfigManager / ConfigCacheStore /
    // LocalConfigAssetProvider are plain @Inject @Singleton classes, so only the interface→impl
    // binding is required here (a @Provides for ConfigManager would be a duplicate binding).
    @Binds
    @Singleton
    abstract fun bindConfigSignatureVerifier(
        impl: Secp256k1ConfigSignatureVerifier
    ): ConfigSignatureVerifier

    // Capability Platform (Android Migration, Step 1) — server-driven feature availability.
    // CapabilityManager / CapabilityCacheStore are plain @Inject @Singleton classes, so only the
    // interface→impl binding is required here. Foundation only: NOT consumed by any wallet,
    // gasless, sponsor, or transfer flow in this step.
    @Binds
    @Singleton
    abstract fun bindCapabilityProvider(
        impl: com.mtd.data.config.CapabilityManager
    ): com.mtd.domain.interfaceRepository.ICapabilityProvider

    // Capability Platform (Android Migration, Phase A) — feature-availability decision seam.
    // FeatureAvailabilityResolver depends only on ICapabilityProvider; per-token/eligibility
    // signals are passed in by the caller. Foundation only: NOT consumed by SendViewModel or
    // any gasless/sponsor/swap/transfer flow in this phase.
    @Binds
    @Singleton
    abstract fun bindFeatureAvailabilityResolver(
        impl: com.mtd.data.config.FeatureAvailabilityResolver
    ): com.mtd.domain.interfaceRepository.IFeatureAvailabilityResolver

    // Gasless Routing Migration (Phase 1) — data-driven networkId → relayPrefix routing.
    // GaslessRouteResolver reads relayPrefix from ICapabilityProvider + family from the
    // registry. Foundation only: NOT consumed by any gasless repository/coordinator yet
    // (threaded in Phase 3).
    @Binds
    @Singleton
    abstract fun bindGaslessRouteResolver(
        impl: com.mtd.data.repository.gasless.GaslessRouteResolver
    ): com.mtd.domain.interfaceRepository.IGaslessRouteResolver



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
    abstract fun bindCloudWalletBackupCodec(
        impl: GsonCloudWalletBackupCodec
    ): ICloudWalletBackupCodec

    @Binds
    @Singleton
    abstract fun bindMarketDataRepository(impl: MarketDataRepositoryImpl): IMarketDataRepository

    /** TASK-54 — must be @Singleton: it IS the shared rate state every screen observes. */
    @Binds
    @Singleton
    abstract fun bindUsdToIrrRateProvider(impl: UsdToIrrRateProvider): IUsdToIrrRateProvider

    /** TASK-56 — must be @Singleton for the same reason: one currency, observed by every screen. */
    @Binds
    @Singleton
    abstract fun bindFiatCurrencyProvider(impl: FiatCurrencyProvider): IFiatCurrencyProvider

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

    @Binds
    @Singleton
    abstract fun bindGrowthRepository(
        impl: GrowthRepositoryImpl
    ): IGrowthRepository

    @Binds
    @Singleton
    abstract fun bindSwapRepository(
        impl: SwapRepositoryImpl
    ): ISwapRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(
        impl: NotificationRepositoryImpl
    ): INotificationRepository

    /**
     * تنها بایندِ [IAssetCatalog] در کلِ برنامه.
     *
     * قبلاً `core/di/ManagerModule` مستقیم [com.mtd.core.registry.AssetRegistry] را بایند می‌کرد؛
     * حالا رجیستری فقط نیمهٔ باندلِ امضاشده است و [MergedAssetCatalog] آن را با فهرستِ توکنِ کاربر
     * ادغام می‌کند. اگر روزی دو بایند هم‌زمان وجود داشته باشد، نیمی از برنامه فهرستِ ناقص می‌بیند.
     */
    @Binds
    @Singleton
    abstract fun bindAssetCatalog(impl: MergedAssetCatalog): IAssetCatalog

    /** همان singletonِ بالا از زاویهٔ صفحهٔ مدیریت — یک کلاس، دو نما، یک منبعِ دانشِ ادغام. */
    @Binds
    @Singleton
    abstract fun bindManageableAssetCatalog(impl: MergedAssetCatalog): IManageableAssetCatalog

    @Binds
    @Singleton
    abstract fun bindUserTokenRepository(
        impl: UserTokenRepositoryImpl
    ): IUserTokenRepository

    @Binds
    @Singleton
    abstract fun bindTokenDiscoveryRepository(
        impl: TokenDiscoveryRepositoryImpl
    ): ITokenDiscoveryRepository


    companion object {

        @Provides
        @Singleton
        fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
            return context.getSharedPreferences("mega_wallet_user_prefs", Context.MODE_PRIVATE)
        }
    }

}
