package com.mtd.megawallet.viewmodel

import com.mtd.core.assets.AssetConfig
import com.mtd.core.model.NetworkName
import com.mtd.core.model.NetworkType
import com.mtd.core.model.WalletKey
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.BalanceFormatter
import com.mtd.core.utils.GlobalEvent
import com.mtd.core.utils.GlobalEventBus
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.repository.IWalletRepository
import com.mtd.domain.model.AssetPrice
import com.mtd.domain.model.BitcoinTransaction
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.Wallet
import com.mtd.domain.repository.IMarketDataRepository
import com.mtd.domain.wallet.ActiveWalletManager
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.event.ActivityItem
import com.mtd.megawallet.event.ActivityType
import com.mtd.megawallet.event.AssetItem
import com.mtd.megawallet.event.AssetWithBalance
import com.mtd.megawallet.event.HomeUiState
import com.mtd.megawallet.event.HomeUiState.DisplayCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val walletRepository: IWalletRepository,
    private val marketDataRepository: IMarketDataRepository,
    private val dataSourceFactory: ChainDataSourceFactory,
    private val assetRegistry: AssetRegistry,
    private val blockchainRegistry: BlockchainRegistry,
    private val globalEventBus: GlobalEventBus,
    private val activeWalletManager: ActiveWalletManager,
    errorManager: com.mtd.core.manager.ErrorManager
) : BaseViewModel(errorManager) {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        observeActiveWallet()
        listenToGlobalEvents()
    }

    private fun observeActiveWallet() {
        launchSafe {
            activeWalletManager.activeWallet.collect { wallet ->
                if (wallet != null) {
                    loadHomePageData(wallet)
                }
            }
        }
    }

    private fun loadHomePageData(activeWallet: Wallet) {
        launchSafe {
            // ۱. نمایش فوری اسکلت UI
            val allSupportedAssets = assetRegistry.getAllAssets()
            val initialAssetItems = allSupportedAssets.map { assetConfig ->
                AssetItem(
                    id = assetConfig.id,
                    name = assetConfig.name,
                    symbol = assetConfig.symbol,
                    networkName = "on ${blockchainRegistry.getNetworkById(assetConfig.networkId)?.name?.name?.lowercase()}",
                    networkId = assetConfig.networkId,
                    iconUrl = assetConfig.iconUrl,
                    balance = "...",
                    balanceUsdt = "...",
                    balanceRaw = BigDecimal.ZERO,
                    priceUsdRaw = BigDecimal.ZERO,
                    decimals = assetConfig.decimals,
                    contractAddress = assetConfig.contractAddress,
                    isNativeToken = assetConfig.contractAddress == null
                )
            }
            _uiState.value = HomeUiState.Success(
                totalBalanceUsdt = "...",
                isUpdating = true,
                assets = initialAssetItems,
                recentActivity = emptyList()
            )

            // ۳. دریافت موجودی‌ها به صورت موازی اما ایمن (بدون awaitAll)
            val networks = blockchainRegistry.getAllNetworks()
            networks.forEach { network ->
                launchSafe {
                    val assetsForNetwork = assetRegistry.getAssetsForNetwork(network.id)
                    if (assetsForNetwork.isNotEmpty()) {
                        val result =
                            fetchBalancesForNetwork(activeWallet, network.id, assetsForNetwork)
                        if (result.isNotEmpty()) {
                            updateAssetsState(result)
                        }
                    }
                }
            }

            // ۳.۵. دریافت قیمت‌ها (جداگانه و موازی)
            launchSafe {
                val allAssetIds = allSupportedAssets.mapNotNull { it.coinGeckoId }.distinct()
                if (allAssetIds.isNotEmpty()) {
                    val pricesResult = marketDataRepository.getLatestPrices(allAssetIds)
                    if (pricesResult is ResultResponse.Success) {
                        val priceMap = pricesResult.data.associateBy { it.assetId }
                        updatePricesState(priceMap)
                    }
                }
            }

            // ۴. دریافت تراکنش‌ها (جداگانه)
            launchSafe {
                val allTransactions = fetchAllTransactions(activeWallet)
                val activityItems = allTransactions
                    .sortedByDescending { it.timestamp }
                    .take(20)
                    .mapNotNull { transaction ->
                        mapTransactionToActivityItem(transaction, activeWallet.keys)
                    }

                _uiState.update { currentState ->
                    if (currentState is HomeUiState.Success) {
                        currentState.copy(recentActivity = activityItems, isUpdating = false)
                    } else currentState
                }
            }
        }
    }

    private suspend fun fetchBalancesForNetwork(
        wallet: Wallet,
        networkId: String,
        assets: List<AssetConfig>
    ): List<AssetWithBalance> {
        val networkInfo = blockchainRegistry.getNetworkById(networkId) ?: return emptyList()
        val key =
            wallet.keys.find { it.networkName.name.lowercase() == networkInfo.name.name.lowercase() }
                ?: return emptyList()
        val dataSource = dataSourceFactory.create(networkInfo.chainId ?: -1)

        return if (networkInfo.networkType == NetworkType.EVM) {
            val result = dataSource.getBalanceEVM(key.address)
            if (result is ResultResponse.Success) {
                result.data.mapNotNull { fetchedAsset ->
                    val config = assets.find {
                        it.symbol == fetchedAsset.symbol && (it.contractAddress == null || it.contractAddress.equals(
                            fetchedAsset.contractAddress,
                            true
                        ))
                    }
                    config?.let { AssetWithBalance(it, fetchedAsset.balance) }
                }
            } else {
                 if (result is ResultResponse.Error) throw result.exception // Rethrow for global handler
                 emptyList()
            }
        } else {
            val result = dataSource.getBalance(key.address)
            if (result is ResultResponse.Success) {
                assets.firstOrNull()?.let { listOf(AssetWithBalance(it, result.data)) }
                    ?: emptyList()
            } else {
                 if (result is ResultResponse.Error) throw result.exception // Rethrow for global handler
                 emptyList()
            }
        }
    }

    private fun updateAssetsState(newAssets: List<AssetWithBalance>) {
        launchSafe {
            val (usdtRate, irrRate) = fetchExchangeRates()

            _uiState.update { currentState ->
                if (currentState is HomeUiState.Success) {
                    val updatedList = currentState.assets.map { item ->
                        val matchingAsset = newAssets.find { it.config.id == item.id }
                        if (matchingAsset != null) {
                            val balanceDecimal = BigDecimal(matchingAsset.balance).divide(
                                BigDecimal.TEN.pow(matchingAsset.config.decimals)
                            )
                            // حفظ قیمت‌های موجود و محاسبه مجدد balanceUsd
                            val balanceUsd = if (item.priceUsdRaw > BigDecimal.ZERO) {
                                balanceDecimal * item.priceUsdRaw
                            } else {
                                BigDecimal.ZERO
                            }

                            val balanceUsdt = balanceUsd / usdtRate
                            val balanceIrr = balanceUsd * irrRate

                            item.copy(
                                balance = BalanceFormatter.formatBalance(
                                    matchingAsset.balance,
                                    matchingAsset.config.decimals
                                ),
                                balanceUsdt = BalanceFormatter.formatUsdValue(balanceUsdt),
                                balanceIrr = "{balanceIrr.setScale(0, RoundingMode.HALF_UP)}",
                                formattedDisplayBalance = BalanceFormatter.formatUsdValue(balanceUsdt), // پیش‌فرض تتر
                                balanceRaw = balanceDecimal
                                // priceUsdRaw و priceChange24h را حفظ می‌کنیم
                            )
                        } else {
                            item
                        }
                    }

                    // محاسبه totalBalanceUsd
                    val totalUsd = updatedList.sumOf {
                        if (it.priceUsdRaw > BigDecimal.ZERO) it.balanceRaw * it.priceUsdRaw else BigDecimal.ZERO
                    }
                    val totalUsdt = totalUsd / usdtRate
                    val totalIrr = totalUsd * irrRate

                    currentState.copy(
                        assets = updatedList,
                        totalBalanceUsdt = BalanceFormatter.formatUsdValue(totalUsdt),
                        totalBalanceIrr = "{totalIrr.setScale(0, RoundingMode.HALF_UP)}"
                    )
                } else {
                    currentState
                }
            }
        }
    }

    private suspend fun fetchAllTransactions(wallet: Wallet): List<TransactionRecord> {
        // اینجا هم می‌توانیم از launchSafe داخلی استفاده کنیم یا همینطور بگذاریم چون در launchSafe والد است
        // اما برای اینکه awaitAll کل پروسه را قفل نکند، بهتر است لیست را برگردانیم
        // فعلاً برای سادگی همان منطق قبلی را با try-catch داخلی نگه می‌داریم
        val allTxs = mutableListOf<TransactionRecord>()
        wallet.keys.forEach { key ->
            try {
                val chainId = key.chainId ?: -1
                val dataSource = dataSourceFactory.create(chainId)
                val result = dataSource.getTransactionHistory(key.address)
                if (result is ResultResponse.Success) {
                    result.data.forEach { it.networkName = key.networkName }
                    allTxs.addAll(result.data)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error fetching transactions for ${key.networkName}")
            }
        }
        return allTxs
    }

    private fun listenToGlobalEvents() {
        launchSafe {
            globalEventBus.events.collect { event ->
                if (event is GlobalEvent.WalletNeedsRefresh) {
                    Timber.i("WalletNeedsRefresh event received, forcing data refresh.")
                    activeWalletManager.activeWallet.value?.let { wallet ->
                        loadHomePageData(wallet)
                    }
                }
            }
        }
    }

    private fun mapTransactionToActivityItem(
        transaction: TransactionRecord,
        userKeys: List<WalletKey>
    ): ActivityItem? {
        return when (transaction) {
            is EvmTransaction -> {
                val userAddressInTx = userKeys.find {
                    it.address.equals(transaction.fromAddress, true) || it.address.equals(transaction.toAddress, true)
                }?.address

                val keyForNetwork = userKeys.find {
                    it.address.equals(userAddressInTx, true) && it.networkName == transaction.networkName
                }
                val network = blockchainRegistry.getNetworkByName(keyForNetwork?.networkName ?: return null) ?: return null

                var identifiedAsset: AssetConfig?
                val possibleToken = assetRegistry.getAllAssets().find {
                    it.contractAddress != null && it.contractAddress.equals(
                        transaction.contractAddress,
                        true
                    )
                }

                identifiedAsset = possibleToken ?: assetRegistry.getAssetsForNetwork(network.id)
                    .find { it.contractAddress == null }

                if (identifiedAsset == null) return null

                val title: String
                val subtitle: String
                val type: ActivityType

                if (transaction.isOutgoing) {
                    type = ActivityType.SEND
                    title = "ارسال ${identifiedAsset.symbol}"
                    subtitle = "به: ${transaction.toAddress.take(6)}...${transaction.toAddress.takeLast(4)}"
                } else {
                    type = ActivityType.RECEIVE
                    title = "دریافت ${identifiedAsset.symbol}"
                    subtitle = "از: ${transaction.fromAddress.take(6)}...${transaction.fromAddress.takeLast(4)}"
                }

                ActivityItem(
                    id = transaction.hash,
                    type = type,
                    title = title,
                    subtitle = subtitle,
                    amount = "${if (transaction.isOutgoing) "-" else "+"} ${BalanceFormatter.formatBalance(transaction.amount, identifiedAsset.decimals)} ${identifiedAsset.symbol}",
                    amountUsd = "",
                    iconUrl = identifiedAsset.iconUrl
                )
            }
            is BitcoinTransaction -> {
                val network = blockchainRegistry.getNetworkByName(NetworkName.BITCOINTESTNET)
                val asset = assetRegistry.getAssetsForNetwork(network?.id ?: "").first()

                val title: String
                val subtitle: String
                val type: ActivityType

                if (transaction.isOutgoing) {
                    type = ActivityType.SEND
                    title = "ارسال ${asset.symbol}"
                    subtitle =
                        "به: ${transaction.toAddress?.take(6)}...${transaction.toAddress?.takeLast(4)}"
                } else {
                    type = ActivityType.RECEIVE
                    title = "دریافت ${asset.symbol}"
                    subtitle = "از: ${transaction.fromAddress?.take(6)}...${
                        transaction.fromAddress?.takeLast(4)
                    }"
                }

                ActivityItem(
                    id = transaction.hash,
                    type = type,
                    title = title,
                    subtitle = subtitle,
                    amount = "${if (transaction.isOutgoing) "-" else "+"} ${
                        BalanceFormatter.formatBalance(
                            transaction.amount,
                            asset.decimals
                        )
                    } ${asset.symbol}",
                    amountUsd = "",
                    iconUrl = asset.iconUrl
                )
            }

            else -> null
        }
    }
    // و یک متد جدید برای آپدیت قیمت‌ها:

    // تغییر واحد پول نمایش داده شده
    fun toggleDisplayCurrency() {
        _uiState.update { currentState ->
            if (currentState is HomeUiState.Success) {
                val newCurrency = when (currentState.displayCurrency) {
                    DisplayCurrency.USDT -> DisplayCurrency.IRR
                    DisplayCurrency.IRR -> DisplayCurrency.USDT
                    else -> DisplayCurrency.USDT // Default fallback
                }

                // آپدیت کردن لیست دارایی‌ها بر اساس ارز جدید
                val updatedAssets = currentState.assets.map { asset ->
                    val newDisplayBalance = when (newCurrency) {
                        DisplayCurrency.USDT -> asset.balanceUsdt
                        DisplayCurrency.IRR -> asset.balanceIrr
                    }
                    // فقط فیلد نمایش را آپدیت می‌کنیم
                    asset.copy(formattedDisplayBalance = newDisplayBalance)
                }

                currentState.copy(
                    displayCurrency = newCurrency,
                    assets = updatedAssets
                )
            } else {
                currentState
            }
        }
    }

    // در HomeViewModel
    private suspend fun fetchExchangeRates(): Pair<BigDecimal, BigDecimal> {
        // دریافت قیمت USDT/USD (معمولاً 1)
        val usdtPrice = BigDecimal.ONE

        // دریافت قیمت USD/IRR از چند صرافی
        val irrPrice = fetchUsdToIrrRate()

        return Pair(usdtPrice, irrPrice)
    }

    private suspend fun fetchUsdToIrrRate(): BigDecimal {
        return try {
           // val result = marketDataRepository.getUsdToIrrRate()
            if (false) {
               // result.data.rate
                BigDecimal("127000000")
            } else {
                BigDecimal("60000") // Fallback
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching USD to IRR rate")
            BigDecimal("60000") // Fallback
        }
    }

    private fun updatePricesState(priceMap: Map<String, AssetPrice>) {
        launchSafe {
            val (usdtRate, irrRate) = fetchExchangeRates()

            _uiState.update { currentState ->
                if (currentState is HomeUiState.Success) {
                    val updatedList = currentState.assets.map { item ->
                        val assetConfig = assetRegistry.getAssetById(item.id)
                        val priceData = assetConfig?.coinGeckoId?.let { priceMap[it] }
                        if (priceData != null) {
                            val balanceUsd = item.balanceRaw * priceData.priceUsd
                            val balanceUsdt = balanceUsd / usdtRate
                            val balanceIrr = balanceUsd * irrRate

                            item.copy(
                                balanceUsdt = BalanceFormatter.formatUsdValue(balanceUsdt).replace("$", ""),
                                balanceIrr = "${balanceIrr.setScale(0, RoundingMode.HALF_UP)} ",
                                formattedDisplayBalance = BalanceFormatter.formatUsdValue(balanceUsdt).replace("$", ""), // پیش‌فرض تتر
                                priceUsdRaw = priceData.priceUsd,
                                priceChange24h = priceData.priceChanges24h.toDouble()
                            )
                        } else {
                            item
                        }
                    }

                    // محاسبه totalBalance به هر سه ارز
                    val totalUsd = updatedList.sumOf {
                        if (it.priceUsdRaw > BigDecimal.ZERO) it.balanceRaw * it.priceUsdRaw else BigDecimal.ZERO
                    }
                    val totalUsdt = totalUsd / usdtRate
                    val totalIrr = totalUsd * irrRate

                    currentState.copy(
                        assets = updatedList,
                        totalBalanceUsdt = BalanceFormatter.formatUsdValue(totalUsdt),
                        totalBalanceIrr = "${totalIrr.setScale(0, RoundingMode.HALF_UP)}"
                    )
                } else {
                    currentState
                }
            }
        }
    }
}