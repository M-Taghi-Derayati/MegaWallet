package com.mtd.megawallet.viewmodel.news

import com.mtd.core.manager.CacheManager
import com.mtd.core.model.NetworkType
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.BalanceFormatter
import com.mtd.core.utils.GlobalEvent
import com.mtd.core.utils.GlobalEventBus
import com.mtd.core.utils.formatWithSeparator
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.repository.IWalletRepository
import com.mtd.domain.model.AssetPrice
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.Wallet
import com.mtd.domain.repository.IMarketDataRepository
import com.mtd.domain.wallet.ActiveWalletManager
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.event.ActivityItem
import com.mtd.megawallet.event.AssetItem
import com.mtd.megawallet.event.CachedAssetBalance
import com.mtd.megawallet.event.HomeUiState
import com.mtd.megawallet.event.HomeUiState.DisplayCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
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
    private val cacheManager: CacheManager,
    errorManager: com.mtd.core.manager.ErrorManager
) : BaseViewModel(errorManager) {

    companion object {
        private const val CACHE_KEY_PREFIX = "asset_balance_"
        private fun getAssetCacheKey(walletId: String, assetId: String) = "$CACHE_KEY_PREFIX${walletId}_$assetId"
    }

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
    }

    val activeWallet = activeWalletManager.activeWallet

    // لیست کامل و خام تمام دارایی‌ها برای مدیریت آپدیت‌ها
    private var fullRawAssets = mutableListOf<AssetItem>()
    private var currentWalletId: String? = null

    init {
        loadWalletIfNeeded()
        observeActiveWallet()
        listenToGlobalEvents()
    }

    private fun loadWalletIfNeeded() {
        launchSafe {
            if (activeWalletManager.activeWallet.value != null) return@launchSafe
            when (val result = walletRepository.loadExistingWallet()) {
                is ResultResponse.Success -> {
                    result.data?.let { Timber.i("Wallet loaded: ${it.name}") }
                }
                is ResultResponse.Error -> {
                    _uiState.value = HomeUiState.Error("خطا در لود کیف پول")
                }
            }
        }
    }

    private fun observeActiveWallet() {
        launchSafe {
            activeWalletManager.activeWallet.collect { wallet ->
                if (wallet != null) {
                    currentWalletId = wallet.id
                    loadHomePageData(wallet)
                } else {
                    if (!walletRepository.hasWallet()) {
                        _uiState.value = HomeUiState.Error("کیف پولی یافت نشد.")
                    }
                }
            }
        }
    }

    private fun loadHomePageData(activeWallet: Wallet) {
        launchSafe {
            val allSupportedAssets = assetRegistry.getAllAssets()
            
            // ۱. لود از کش
            val cachedAssetsMap = mutableMapOf<String, CachedAssetBalance>()
            allSupportedAssets.forEach { config ->
                cacheManager.get(getAssetCacheKey(activeWallet.id, config.id), CachedAssetBalance::class.java)?.let {
                    cachedAssetsMap[config.id] = it
                }
            }
            
            // ۲. ساخت لیست بر اساس کش یا مقادیر اولیه
            fullRawAssets = allSupportedAssets.map { config ->
                val cached = cachedAssetsMap[config.id]
                val network = blockchainRegistry.getNetworkById(config.networkId)
                if (cached != null) {
                    AssetItem(
                        id = config.id,
                        name = config.name,
                        faName = config.faName,
                        symbol = config.symbol,
                        networkName = "on ${network?.name?.name?.lowercase()}",
                        networkFaName = network?.faName,
                        networkId = config.networkId,
                        iconUrl = config.iconUrl,
                        balance = cached.balance,
                        balanceUsdt = cached.balanceUsdt,
                        balanceIrr = cached.balanceIrr,
                        formattedDisplayBalance = cached.balanceUsdt,
                        balanceRaw = cached.balanceRaw,
                        priceUsdRaw = cached.priceUsdRaw,
                        priceChange24h = cached.priceChange24h,
                        decimals = config.decimals,
                        contractAddress = config.contractAddress,
                        isNativeToken = config.contractAddress == null
                    )
                } else {
                    AssetItem(
                        id = config.id,
                        name = config.name,
                        faName = config.faName,
                        symbol = config.symbol,
                        networkName = "on ${network?.name?.name?.lowercase()}",
                        networkFaName = network?.faName,
                        networkId = config.networkId,
                        iconUrl = config.iconUrl,
                        balance = "...",
                        balanceUsdt = "...",
                        balanceRaw = BigDecimal.ZERO,
                        priceUsdRaw = BigDecimal.ZERO,
                        decimals = config.decimals,
                        contractAddress = config.contractAddress,
                        isNativeToken = config.contractAddress == null
                    )
                }
            }.toMutableList()

            val (usdtRate, irrRate) = fetchExchangeRates()
            val initialAggregated = createAggregatedListWithRates(fullRawAssets, usdtRate, irrRate)
            
            // محاسبه موجودی کل بر اساس کش
            val totalUsd = fullRawAssets.sumOf { it.balanceRaw * it.priceUsdRaw }
            val totalUsdt = if (usdtRate > BigDecimal.ZERO) totalUsd / usdtRate else BigDecimal.ZERO
            val totalIrr = totalUsd * irrRate

            // ۳. نمایش موفقیت (اول از کش، بعد آپدیت آنلاین)
            _uiState.value = HomeUiState.Success(
                totalBalanceUsdt = if (totalUsdt > BigDecimal.ZERO) BalanceFormatter.formatUsdValue(totalUsdt, false) else "...",
                totalBalanceIrr = if (totalIrr > BigDecimal.ZERO) totalIrr.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true) else "...",
                tetherPriceIrr = irrRate.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true),
                isUpdating = true,
                assets = initialAggregated,
                recentActivity = emptyList(),
                displayCurrency = (uiState.value as? HomeUiState.Success)?.displayCurrency ?: DisplayCurrency.USDT
            )

            // ۴. شروع آپدیت آنلاین در پس‌زمینه
            updateBalancesAsync(activeWallet, allSupportedAssets)
        }
    }

    private fun updateBalancesAsync(wallet: Wallet, allAssets: List<com.mtd.core.assets.AssetConfig>) {
        launchSafe {
            val (usdtRate, irrRate) = fetchExchangeRates()
            
            // دریافت قیمت‌های جدید
            val allCoinGeckoIds = allAssets.mapNotNull { it.coinGeckoId }.distinct()
            val pricesMap = marketDataRepository.getLatestPrices(allCoinGeckoIds).let {
                if (it is ResultResponse.Success) it.data.associateBy { p -> p.assetId } else emptyMap()
            }

            // ارسال درخواست‌ها به صورت همزمان برای هر شبکه
            val jobs = blockchainRegistry.getAllNetworks().map { network ->
                launchSafe {
                    val assetsInNetwork = assetRegistry.getAssetsForNetwork(network.id)
                    if (assetsInNetwork.isEmpty()) return@launchSafe
                    
                    val address = activeWalletManager.getAddressForNetwork(network.chainId ?: return@launchSafe) ?: return@launchSafe
                    val ds = dataSourceFactory.create(network.chainId!!)
                    
                    if (network.networkType == NetworkType.EVM) {
                        try {
                            (ds.getBalanceEVM(address) as? ResultResponse.Success)?.data?.forEach { balanceData ->
                                val config = assetsInNetwork.find {
                                    it.symbol == balanceData.symbol && 
                                    (it.contractAddress?.equals(balanceData.contractAddress, true) ?: (balanceData.contractAddress == null))
                                }
                                if (config != null) {
                                    val bal = BigDecimal(balanceData.balance).divide(BigDecimal.TEN.pow(config.decimals))
                                    updateAssetItemAndTotal(wallet.id, config, bal, pricesMap, usdtRate, irrRate)
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error fetching EVM balance for ${network.id}")
                        }
                    } else {
                        // Bitcoin
                        try {
                            (ds.getBalance(address) as? ResultResponse.Success)?.data?.let { satoshi ->
                                val config = assetsInNetwork.firstOrNull()
                                if (config != null) {
                                    val bal = BalanceFormatter.formatBalance(satoshi, config.decimals).toBigDecimal()
                                    updateAssetItemAndTotal(wallet.id, config, bal, pricesMap, usdtRate, irrRate)
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error fetching BTC balance")
                        }
                    }
                }
            }
            
            // منتظر تمام شدن همه درخواست‌ها برای برداشتن لودینگ کلی
            jobs.joinAll()
            
            val activity = fetchRecentActivity(wallet)
            _uiState.update { state ->
                if (state is HomeUiState.Success) state.copy(isUpdating = false, recentActivity = activity) else state
            }
        }
    }

    private fun updateAssetItemAndTotal(
        walletId: String,
        assetConfig: com.mtd.core.assets.AssetConfig,
        balance: BigDecimal,
        pricesMap: Map<String, AssetPrice>,
        usdtRate: BigDecimal,
        irrRate: BigDecimal
    ) {
        // ۱. به‌روزرسانی لیست خام در حافظه
        synchronized(fullRawAssets) {
            val index = fullRawAssets.indexOfFirst { it.id == assetConfig.id }
            val priceInfo = assetConfig.coinGeckoId?.let { pricesMap[it] }
            val usdValue = if (priceInfo != null) balance * priceInfo.priceUsd else BigDecimal.ZERO
            val usdtValue = if (usdtRate > BigDecimal.ZERO) usdValue / usdtRate else BigDecimal.ZERO
            val irrValue = usdValue * irrRate

            if (index != -1) {
                val oldAsset = fullRawAssets[index]
                val updatedAsset = oldAsset.copy(
                    balance = "${BalanceFormatter.formatBalance(balance.multiply(BigDecimal.TEN.pow(assetConfig.decimals)).toBigInteger(), assetConfig.decimals)} ${assetConfig.symbol}",
                    balanceUsdt = "${BalanceFormatter.formatUsdValue(usdtValue, false)} ",
                    balanceIrr = "${irrValue.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true)} ",
                    formattedDisplayBalance = if ((uiState.value as? HomeUiState.Success)?.displayCurrency == DisplayCurrency.IRR) 
                        "${irrValue.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true)} " else 
                        "${BalanceFormatter.formatUsdValue(usdtValue, false)} ",
                    balanceRaw = balance,
                    priceUsdRaw = priceInfo?.priceUsd ?: BigDecimal.ZERO,
                    priceChange24h = priceInfo?.priceChanges24h?.toDouble() ?: 0.0
                )
                fullRawAssets[index] = updatedAsset
                
                // به‌روزرسانی در کش
                launchSafe {
                    cacheManager.put(getAssetCacheKey(walletId, assetConfig.id), CachedAssetBalance(
                        assetId = assetConfig.id, walletId = walletId, balanceRaw = balance,
                        priceUsdRaw = priceInfo?.priceUsd ?: BigDecimal.ZERO,
                        balance = updatedAsset.balance, balanceUsdt = updatedAsset.balanceUsdt,
                        balanceIrr = updatedAsset.balanceIrr, priceChange24h = updatedAsset.priceChange24h
                    ))
                }
            } else {
                // اگر ارزی در لیست Placeholder نبود (سناریوی ارزهای جدید یا داینامیک)
                val network = blockchainRegistry.getNetworkById(assetConfig.networkId)
                val newAsset = AssetItem(
                    id = assetConfig.id,
                    name = assetConfig.name,
                    faName = assetConfig.faName,
                    symbol = assetConfig.symbol,
                    networkName = "on ${network?.name?.name?.lowercase()}",
                    networkFaName = network?.faName,
                    networkId = assetConfig.networkId,
                    iconUrl = assetConfig.iconUrl,
                    balance = "${BalanceFormatter.formatBalance(balance.multiply(BigDecimal.TEN.pow(assetConfig.decimals)).toBigInteger(), assetConfig.decimals)} ${assetConfig.symbol}",
                    balanceUsdt = "${BalanceFormatter.formatUsdValue(usdtValue, false)} ",
                    balanceIrr = "${irrValue.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true)} ",
                    formattedDisplayBalance = BalanceFormatter.formatUsdValue(usdtValue, false),
                    balanceRaw = balance,
                    priceUsdRaw = priceInfo?.priceUsd ?: BigDecimal.ZERO,
                    priceChange24h = priceInfo?.priceChanges24h?.toDouble() ?: 0.0,
                    decimals = assetConfig.decimals,
                    contractAddress = assetConfig.contractAddress,
                    isNativeToken = assetConfig.contractAddress == null
                )
                fullRawAssets.add(newAsset)
            }
        }

        // ۲. به‌روزرسانی UI به محض دریافت هر جواب (یک به یک)
        _uiState.update { currentState ->
            if (currentState is HomeUiState.Success) {
                // محاسبه مجدد تجمیع و مجموع کل
                val reAggregated = createAggregatedListWithRates(fullRawAssets.toList(), usdtRate, irrRate)
                
                // حفظ وضعیت expanded گروه‌ها
                val expansionState = currentState.assets.filter { it.isGroupHeader }.associate { it.symbol to it.isExpanded }
                val finalAssets = reAggregated.map { 
                    if (it.isGroupHeader && expansionState[it.symbol] == true) it.copy(isExpanded = true) else it 
                }

                val totalUsd = fullRawAssets.sumOf { it.balanceRaw * it.priceUsdRaw }
                val totalUsdt = if (usdtRate > BigDecimal.ZERO) totalUsd / usdtRate else BigDecimal.ZERO
                val totalIrr = totalUsd * irrRate

                currentState.copy(
                    assets = finalAssets,
                    totalBalanceUsdt = BalanceFormatter.formatUsdValue(totalUsdt, false),
                    totalBalanceIrr = totalIrr.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true)
                )
            } else currentState
        }
    }

    private fun createAggregatedListWithRates(rawList: List<AssetItem>, usdtRate: BigDecimal, irrRate: BigDecimal): List<AssetItem> {
        val mustShow = setOf("BTC", "ETH", "USDT")
        return rawList.groupBy { it.symbol.uppercase() }.mapNotNull { (symbol, assets) ->
            val totalBal = assets.sumOf { it.balanceRaw }
            if (totalBal > BigDecimal.ZERO || mustShow.contains(symbol)) {
                if (assets.size > 1) {
                    val first = assets.first()
                    val totalUsd = totalBal * first.priceUsdRaw
                    val totalUsdt = if (usdtRate > BigDecimal.ZERO) totalUsd / usdtRate else BigDecimal.ZERO
                    val totalIrr = totalUsd * irrRate
                    
                    val activeAssets = assets.filter { it.balanceRaw > BigDecimal.ZERO }
                    val finalNetworkId: String
                    val finalNetworkName: String
                    
                    when {
                        totalBal == BigDecimal.ZERO -> {
                            val ethAsset = assets.find { it.networkId.contains("ethereum", true) || it.networkId.contains("sepolia", true) }
                            val defaultAsset = ethAsset ?: assets.first()
                            finalNetworkId = defaultAsset.networkId
                            finalNetworkName = defaultAsset.networkName
                        }
                        activeAssets.size == 1 -> {
                            val activeAsset = activeAssets.first()
                            finalNetworkId = activeAsset.networkId
                            finalNetworkName = activeAsset.networkName
                        }
                        else -> {
                            finalNetworkId = "GROUP"
                            finalNetworkName = ""
                        }
                    }

                    val dist = assets.filter { it.balanceRaw > BigDecimal.ZERO }.map { 
                        val pct = (it.balanceRaw.toFloat() / totalBal.toFloat()) * 100f
                        com.mtd.megawallet.event.NetworkShare(
                            it.networkId, it.networkName.removePrefix("on ").trim(),
                            blockchainRegistry.getNetworkById(it.networkId)?.color ?: "#888888", pct
                        )
                    }

                    AssetItem(
                        id = "GROUP_$symbol", networkId = finalNetworkId, name = first.name, faName = first.faName,
                        symbol = symbol, networkName = finalNetworkName, iconUrl = first.iconUrl,
                        balance = "${BalanceFormatter.formatBalance(totalBal.multiply(BigDecimal.TEN.pow(first.decimals)).toBigInteger(), first.decimals)} $symbol",
                        balanceUsdt = "${BalanceFormatter.formatUsdValue(totalUsdt, false)} ",
                        balanceIrr = "${totalIrr.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true)} ",
                        formattedDisplayBalance = if ((uiState.value as? HomeUiState.Success)?.displayCurrency == DisplayCurrency.IRR) 
                            "${totalIrr.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true)} " else
                            "${BalanceFormatter.formatUsdValue(totalUsdt, false)} ",
                        balanceRaw = totalBal, priceUsdRaw = first.priceUsdRaw, priceChange24h = first.priceChange24h,
                        decimals = first.decimals, isGroupHeader = true, groupAssets = assets, networkDistribution = dist
                    )
                } else assets.first()
            } else null
        }
    }

    fun toggleGroupExpansion(groupId: String) {
        _uiState.update { state ->
            if (state is HomeUiState.Success) {
                state.copy(assets = state.assets.map { if (it.id == groupId) it.copy(isExpanded = !it.isExpanded) else it })
            } else state
        }
    }

    fun toggleDisplayCurrency() {
        _uiState.update { state ->
            if (state is HomeUiState.Success) {
                val next = if (state.displayCurrency == DisplayCurrency.USDT) DisplayCurrency.IRR else DisplayCurrency.USDT
                state.copy(displayCurrency = next, assets = state.assets.map { 
                    it.copy(formattedDisplayBalance = if (next == DisplayCurrency.USDT) it.balanceUsdt else it.balanceIrr)
                })
            } else state
        }
    }

    private suspend fun fetchExchangeRates() = Pair(BigDecimal.ONE, fetchUsdToIrrRate())
    private suspend fun fetchUsdToIrrRate() = (marketDataRepository.getUsdToIrrRate() as? ResultResponse.Success)?.data?.rate ?: BigDecimal("137000")
    private suspend fun fetchRecentActivity(w: Wallet) = emptyList<ActivityItem>()

    private fun listenToGlobalEvents() {
        launchSafe {
            globalEventBus.events.collect { if (it is GlobalEvent.WalletNeedsRefresh) refreshData() }
        }
    }

    fun refreshData() {
        activeWalletManager.activeWallet.value?.let { loadHomePageData(it) }
    }
}
