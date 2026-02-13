package com.mtd.megawallet.viewmodel.news

import com.mtd.core.assets.AssetConfig
import com.mtd.core.manager.CacheManager
import com.mtd.core.manager.CacheManager.Companion.ASSETS_TTL
import com.mtd.core.manager.ErrorManager
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
import com.mtd.megawallet.event.AssetItem
import com.mtd.megawallet.event.CachedAssetBalance
import com.mtd.megawallet.event.HomeUiState
import com.mtd.megawallet.event.HomeUiState.DisplayCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    companion object {
         const val CACHE_KEY_PREFIX = "asset_balance_"
        private fun getAssetCacheKey(walletId: String, assetId: String) = "$CACHE_KEY_PREFIX${walletId}_$assetId"
    }

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    // Cache برای نرخ تتر به تومان
    private var cachedIrrRate: BigDecimal? = null
    private var lastIrrRateUpdateTime: Long = 0
    
    // زمان‌بندی‌های مجزا برای رفرش دیتای خودکار
    private val RR_PRICE_REFRESH_INTERVAL = 5 * 60 * 1000L // 2 دقیقه برای قیمت‌ها
    private val RR_BALANCE_REFRESH_INTERVAL = 10 * 60 * 1000L // 5 دقیقه برای موجودی‌ها
    private val IRR_RATE_CACHE_DURATION_MS = 3 * 60 * 1000L // 3 دقیقه
    
    // کلیدهای ذخیره زمان آخرین آپدیت در کش
    private val LAST_PRICE_SYNC_TIME_KEY = "last_price_sync_time"
    private val LAST_BALANCE_SYNC_TIME_KEY = "last_balance_sync_time"


    val activeWallet = activeWalletManager.activeWallet

    // لیست کامل و خام تمام دارایی‌ها برای مدیریت آپدیت‌ها
    private var fullRawAssets = mutableListOf<AssetItem>()
    private var currentWalletId: String? = null
    private var dataFetchJob: kotlinx.coroutines.Job? = null


    // Lock object for synchronizing access to fullRawAssets
    private val assetsLock = Any()

    // Channel for throttling UI updates (Conflated to drop intermediate updates during delay)
    private val uiUpdateChannel = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    init {
        loadWalletIfNeeded()
        observeActiveWallet()
        listenToGlobalEvents()
        
        // Start the UI update consumer
        launchSafe {
            uiUpdateChannel.receiveAsFlow().collect {
                processUiUpdate()
                // Throttle updates: wait 100ms before processing the next batch
                // This prevents "Recomposition Storm" while still showing live progress
                kotlinx.coroutines.delay(100)
            }
        }
    }

    fun getAssetById(id: String): AssetItem? {
        val state = _uiState.value
        if (state is HomeUiState.Success) {
            state.assets.find { it.id == id }?.let { return it }
        }
        synchronized(assetsLock) {
            return fullRawAssets.find { it.id == id }
        }
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
                    errorManager.showSnackbar("خطا در لود کیف پول")

                }
            }
        }
    }

    private fun observeActiveWallet() {
        launchSafe {
            activeWalletManager.activeWallet.collect { wallet ->
                // کنسل کردن کارهای قبلی برای جلوگیری از نشت دیتا بین والت‌ها
                dataFetchJob?.cancel()
                
                if (wallet != null) {
                    currentWalletId = wallet.id
                    dataFetchJob = launchSafe {
                        // در لحظه سوئیچ فقط کش را نشان بده و آپدیت خودکار نکن
                        loadHomePageData(wallet, forceUpdate = false)
                    }
                } else {
                    if (!walletRepository.hasWallet()) {
                        _uiState.value = HomeUiState.Error("کیف پولی یافت نشد.")
                        errorManager.showSnackbar("کیف پولی یافت نشد.")
                    }
                }
            }
        }
    }

    private suspend fun loadHomePageData(activeWallet: Wallet, forceUpdate: Boolean = false) {
        val allSupportedAssets = assetRegistry.getAllAssets()
        
        // ۱. لود از کش
        val cachedAssetsMap = mutableMapOf<String, CachedAssetBalance>()
        allSupportedAssets.forEach { config ->
            cacheManager.get(getAssetCacheKey(activeWallet.id, config.id), CachedAssetBalance::class.java)?.let {
                cachedAssetsMap[config.id] = it
            }
        }
        
        val hasAnyCache = cachedAssetsMap.isNotEmpty()

        // ۲. ساخت لیست بر اساس کش یا مقادیر اولیه
        val localAssets = allSupportedAssets.map { config ->
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

        synchronized(assetsLock) {
            fullRawAssets = localAssets
        }

        //  عدم بلاک کردن UI برای دریافت نرخ ارز
        val savedRate = cacheManager.get("LAST_IRR_RATE", String::class.java)?.toBigDecimalOrNull()
        val irrRate = cachedIrrRate ?: savedRate ?: BigDecimal("0")
        val usdtRate = BigDecimal.ONE 

        // محاسبه اولیه برای نمایش سریع کش
        val initialAggregated = createAggregatedListWithRates(localAssets, usdtRate, irrRate)
        
        val totalUsd = localAssets.sumOf { it.balanceRaw * it.priceUsdRaw }
        val totalUsdt = if (usdtRate > BigDecimal.ZERO) totalUsd / usdtRate else BigDecimal.ZERO
        val totalIrr = totalUsd * irrRate

        // ۳. تصمیم‌گیری برای آپدیت خودکار بر اساس زمان آخرین همگام‌سازی
        val currentTime = System.currentTimeMillis()
        
        val lastPriceSync = cacheManager.get(LAST_PRICE_SYNC_TIME_KEY, Long::class.javaObjectType) ?: 0L
        val lastBalanceSync = cacheManager.get(LAST_BALANCE_SYNC_TIME_KEY, Long::class.javaObjectType) ?: 0L
        
        val isPriceStale = currentTime - lastPriceSync > RR_PRICE_REFRESH_INTERVAL
        val isBalanceStale = currentTime - lastBalanceSync > RR_BALANCE_REFRESH_INTERVAL
        
        val shouldUpdateOnline = forceUpdate || !hasAnyCache || isPriceStale || isBalanceStale
        
        _uiState.value = HomeUiState.Success(
            totalBalanceUsdt = if (totalUsdt > BigDecimal.ZERO) BalanceFormatter.formatUsdValue(totalUsdt, false) else "...",
            totalBalanceIrr = if (totalIrr > BigDecimal.ZERO) totalIrr.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true) else "...",
            tetherPriceIrr = irrRate.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true),
            isUpdating = shouldUpdateOnline,
            assets = initialAggregated,
            recentActivity = emptyList(),
            displayCurrency = (uiState.value as? HomeUiState.Success)?.displayCurrency ?: DisplayCurrency.USDT
        )

        // ۴. اجرای مستقل آپدیت‌ها
        if (forceUpdate || !hasAnyCache || isPriceStale) {
            refreshPrices()
        }
        
        if (forceUpdate || !hasAnyCache || isBalanceStale) {
            refreshBalances(activeWallet)
        }
    }

    /**
     * فقط رفرش قیمت ارزها از مارکت
     */
    private fun refreshPrices() {
        launchSafe {
            val (usdtRate, irrRate) = fetchExchangeRates().apply {
                cachedIrrRate = this.second
                lastIrrRateUpdateTime = System.currentTimeMillis()
                cacheManager.put("LAST_IRR_RATE", this.second.toPlainString())
            }

            val allAssets = assetRegistry.getAllAssets()
            val allCoinIds = allAssets.map { it.symbol }.distinct()
            
            val result = marketDataRepository.getLatestPrices(allCoinIds)
            if (result is ResultResponse.Success) {
                val pricesMap = result.data.associateBy { it.assetId }
                
                synchronized(assetsLock) {
                    fullRawAssets.forEachIndexed { index, assetItem ->
                        val config = allAssets.find { it.id == assetItem.id }
                        val priceInfo = config?.symbol?.let { pricesMap[it] }
                        
                        if (priceInfo != null) {
                            val currentPrice = priceInfo.priceUsd
                            val usdValue = assetItem.balanceRaw * currentPrice
                            val irrValue = usdValue * irrRate
                            
                            val updatedItem = assetItem.copy(
                                priceUsdRaw = currentPrice,
                                priceChange24h = priceInfo.priceChanges24h.toDouble(),
                                balanceUsdt = "${BalanceFormatter.formatUsdValue(usdValue)} ",
                                balanceIrr = "${irrValue.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true)} ",
                                formattedDisplayBalance = if ((uiState.value as? HomeUiState.Success)?.displayCurrency == DisplayCurrency.IRR) 
                                    "${irrValue.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true)} " else 
                                    "${BalanceFormatter.formatUsdValue(usdValue)} "
                            )
                            fullRawAssets[index] = updatedItem
                            
                            // همگام‌سازی با کش دیسک برای استفاده در صفحات دیگر
                            launchSafe {
                                cacheManager.put(getAssetCacheKey(currentWalletId ?: "", updatedItem.id), CachedAssetBalance(
                                    assetId = updatedItem.id, walletId = currentWalletId ?: "", 
                                    balanceRaw = updatedItem.balanceRaw,
                                    priceUsdRaw = updatedItem.priceUsdRaw,
                                    balance = updatedItem.balance, balanceUsdt = updatedItem.balanceUsdt,
                                    balanceIrr = updatedItem.balanceIrr, priceChange24h = updatedItem.priceChange24h
                                ), ttl = ASSETS_TTL)
                            }
                        }
                    }
                }
                cacheManager.put(LAST_PRICE_SYNC_TIME_KEY, System.currentTimeMillis())
                uiUpdateChannel.trySend(Unit)
            }
        }
    }

    /**
     * فقط رفرش موجودی‌ها از شبکه (RPC)
     */
    private fun refreshBalances(wallet: Wallet) {
        launchSafe {
            _uiState.update { if (it is HomeUiState.Success) it.copy(isUpdating = true) else it }
            
            // ابتدا از همان نرخ IRR کش شده استفاده می‌کنیم
            val irrRate = cachedIrrRate ?: cacheManager.get("LAST_IRR_RATE", String::class.java)?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val usdtRate = BigDecimal.ONE

            val jobs = blockchainRegistry.getAllNetworks().map { network ->
                launchSafe {
                    val result = walletRepository.getBalancesForMultipleWallets(network.name, listOf(wallet.id))
                    if (result is ResultResponse.Success) {
                        val walletAssets = result.data[wallet.id] ?: return@launchSafe
                        val assetsInNetwork = assetRegistry.getAssetsForNetwork(network.id)
                        
                        walletAssets.forEach { asset ->
                            val config = assetsInNetwork.find {
                                it.symbol.equals(asset.symbol, ignoreCase = true) && 
                                (it.contractAddress?.equals(asset.contractAddress, true) ?: (asset.contractAddress == null))
                            }
                            if (config != null) {
                                // به‌روزرسانی با استفاده از قیمت‌های فعلی (موجود در حافظه)
                                updateAssetItemAndTotal(wallet.id, config, asset.balance, emptyMap(), usdtRate, irrRate)
                            }
                        }
                    }
                }
            }
            
            jobs.filter { it.isActive }.joinAll()
            cacheManager.put(LAST_BALANCE_SYNC_TIME_KEY, System.currentTimeMillis())
            _uiState.update { if (it is HomeUiState.Success) it.copy(isUpdating = false) else it }
        }
    }

    private fun updateAssetItemAndTotal(
        walletId: String,
        assetConfig: AssetConfig,
        balance: BigDecimal,
        pricesMap: Map<String, AssetPrice>,
        usdtRate: BigDecimal,
        irrRate: BigDecimal
    ) {
        // امن‌سازی: فقط اگر والت فعلی هنوز همان است که درخواست داده بودیم، ادامه بده
        if (walletId != currentWalletId) return

        // ۱. به‌روزرسانی لیست خام در حافظه (با همگام‌سازی صحیح)
        synchronized(assetsLock) {
            val index = fullRawAssets.indexOfFirst { it.id == assetConfig.id }
            val priceInfo = assetConfig.symbol.let { pricesMap[it] }
            
            // ۱. تعیین قیمت فعلی (بسیار مهم: اگر قیمت جدید نداریم، قیمت قبلی را حفظ کن)
            val existingAsset = if (index != -1) fullRawAssets[index] else null
            
            var currentPrice = priceInfo?.priceUsd
            if (currentPrice == null || currentPrice == BigDecimal.ZERO) {
                currentPrice = existingAsset?.priceUsdRaw
            }
            
            // اگر هنوز قیمت پیدا نشد، از کش بخون (آخرین شانس)
            if (currentPrice == null || currentPrice == BigDecimal.ZERO) {
                 // توجه: این فراخوانی سینک است چون داخل لود اولیه هم چک شده
                 // اما برای اطمینان ما در این مرحله اگر صفر بود صفر می‌ماند تا آپدیت قیمت برسد
                 currentPrice = existingAsset?.priceUsdRaw ?: BigDecimal.ZERO
            }

            val currentChange = priceInfo?.priceChanges24h?.toDouble() ?: existingAsset?.priceChange24h ?: 0.0
            
            val usdValue = balance * currentPrice
            val irrValue = usdValue * irrRate

            if (index != -1) {
                val oldAsset = fullRawAssets[index]
                val updatedAsset = oldAsset.copy(
                    balance = BalanceFormatter.formatBalance(balance, assetConfig.decimals),
                    balanceUsdt = "${BalanceFormatter.formatUsdValue(usdValue)} ",
                    balanceIrr = "${irrValue.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true)} ",
                    formattedDisplayBalance = if ((uiState.value as? HomeUiState.Success)?.displayCurrency == DisplayCurrency.IRR) 
                        "${irrValue.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true)} " else 
                        "${BalanceFormatter.formatUsdValue(usdValue)} ",
                    balanceRaw = balance,
                    priceUsdRaw = currentPrice,
                    priceChange24h = currentChange
                )
                fullRawAssets[index] = updatedAsset
                
                // به‌روزرسانی در کش
                launchSafe {
                    cacheManager.put(getAssetCacheKey(walletId, assetConfig.id), CachedAssetBalance(
                        assetId = assetConfig.id, walletId = walletId, balanceRaw = balance,
                        priceUsdRaw = currentPrice,
                        balance = updatedAsset.balance, balanceUsdt = updatedAsset.balanceUsdt,
                        balanceIrr = updatedAsset.balanceIrr, priceChange24h = updatedAsset.priceChange24h
                    ), ttl = ASSETS_TTL)
                }
            } else {
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
                    balance = BalanceFormatter.formatBalance(balance, assetConfig.decimals),
                    balanceUsdt = "${BalanceFormatter.formatUsdValue(usdValue)} ",
                    balanceIrr = "${irrValue.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true)} ",
                    formattedDisplayBalance = if ((uiState.value as? HomeUiState.Success)?.displayCurrency == DisplayCurrency.IRR) 
                        "${irrValue.setScale(0, RoundingMode.HALF_UP).formatWithSeparator(true)} " else 
                        "${BalanceFormatter.formatUsdValue(usdValue)} ",
                    balanceRaw = balance,
                    priceUsdRaw = currentPrice,
                    priceChange24h = currentChange,
                    decimals = assetConfig.decimals,
                    contractAddress = assetConfig.contractAddress,
                    isNativeToken = assetConfig.contractAddress == null
                )
                fullRawAssets.add(newAsset)
            }
        }

        // ۲. درخواست به‌روزرسانی UI (بدون بلاک کردن ترد)
        // این درخواست در صف Channel قرار می‌گیرد و با نرخ مشخصی پردازش می‌شود
        uiUpdateChannel.trySend(Unit)
    }

    /**
     * این متد مسئول ساختن وضعیت نهایی UI از روی لیست خام دیتاهاست.
     * توسط Channel فراخوانی می‌شود تا بار پردازشی کاهش یابد.
     */
    private fun processUiUpdate() {
        val irrRate = cachedIrrRate ?: BigDecimal.ZERO
        val usdtRate = BigDecimal.ONE

        _uiState.update { currentState ->
            if (currentState is HomeUiState.Success) {
                // کپی امن از لیست برای محاسبات
                val currentRawAssets = synchronized(assetsLock) { fullRawAssets.toList() }
                
                // محاسبات سنگین تجمیع و گروه‌بندی
                val reAggregated = createAggregatedListWithRates(currentRawAssets, usdtRate, irrRate)
                
                val expansionState = currentState.assets.filter { it.isGroupHeader }.associate { it.symbol to it.isExpanded }
                val finalAssets = reAggregated.map { 
                    if (it.isGroupHeader && expansionState[it.symbol] == true) it.copy(isExpanded = true) else it 
                }

                // محاسبه مجموع کل به صورت صریح و امن
                var totalUsd = BigDecimal.ZERO
                currentRawAssets.forEach {
                    totalUsd = totalUsd.add(it.balanceRaw.multiply(it.priceUsdRaw))
                }
                
                val totalIrr = totalUsd.multiply(irrRate)

                currentState.copy(
                    assets = finalAssets,
                    totalBalanceUsdt = BalanceFormatter.formatUsdValue(totalUsd, false),
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
                        balance = BalanceFormatter.formatBalance(totalBal, first.decimals),
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

    private suspend fun fetchExchangeRates() = Pair(BigDecimal.ONE, getUsdToIrrRate())

    /**
     * دریافت نرخ تتر به تومان (برای استفاده در سایر کامپوننت‌ها)
     * با cache: اگر کمتر از 3 دقیقه از آخرین به‌روزرسانی گذشته باشد، مقدار cache شده را برمی‌گرداند
     * در غیر این صورت از API می‌گیرد و cache را به‌روز می‌کند
     */
    suspend fun getUsdToIrrRate(): BigDecimal {
        val currentTime = System.currentTimeMillis()
        
        // بررسی cache: اگر نرخ cache شده وجود دارد و کمتر از 3 دقیقه گذشته است
        if (cachedIrrRate != null && cachedIrrRate!= BigDecimal.ZERO && (currentTime - lastIrrRateUpdateTime) < IRR_RATE_CACHE_DURATION_MS) {
            return cachedIrrRate!!
        }
        
        // از API بگیر و cache را به‌روز کن
        val rate = (marketDataRepository.getUsdToIrrRate() as? ResultResponse.Success)?.data?.rate ?: BigDecimal.ZERO
        cachedIrrRate = rate
        lastIrrRateUpdateTime = currentTime
        return rate
    }

    private fun listenToGlobalEvents() {
        launchSafe {
            globalEventBus.events.collect { if (it is GlobalEvent.WalletNeedsRefresh) refreshData() }
        }
    }

    fun refreshData() {
        activeWalletManager.activeWallet.value?.let { 
            dataFetchJob?.cancel()
            dataFetchJob = launchSafe {
                // در رفرش دستی، هر دو را اجباری آپدیت می‌کنیم
                refreshPrices()
                refreshBalances(it)
            }
        }
    }
}
