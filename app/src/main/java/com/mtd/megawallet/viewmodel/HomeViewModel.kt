package com.mtd.megawallet.viewmodel

import com.mtd.core.manager.ErrorManager
import com.mtd.core.manager.ErrorSeverity
import com.mtd.core.utils.BalanceFormatter
import com.mtd.core.utils.formatWithSeparator
import com.mtd.domain.interfaceRepository.IAppCacheStore
import com.mtd.domain.interfaceRepository.IAppCacheStore.Companion.ASSETS_TTL
import com.mtd.domain.interfaceRepository.IAppEventBus
import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.IUsdToIrrRateProvider
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.AppEvent
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.CachedAssetBalance
import com.mtd.domain.model.HomeUiState
import com.mtd.domain.model.HomeUiState.DisplayCurrency
import com.mtd.domain.model.NetworkShare
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.assets.AssetConfig
import com.mtd.domain.model.assets.AssetPriceDto
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.core.Wallet
import com.mtd.domain.model.error.ErrorSurface
import com.mtd.domain.usecase.asset.GetLatestAssetPricesUseCase
import com.mtd.domain.usecase.network.GetNetworkTypeByIdUseCase
import com.mtd.domain.usecase.network.GetNetworkTypeForAddressUseCase
import com.mtd.domain.usecase.wallet.GetActiveWalletUseCase
import com.mtd.domain.usecase.wallet.GetBalancesForMultipleWalletsUseCase
import com.mtd.domain.usecase.wallet.HasWalletUseCase
import com.mtd.domain.usecase.wallet.LoadExistingWalletUseCase
import com.mtd.domain.usecase.wallet.ObserveActiveWalletUseCase
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val assetCatalog: IAssetCatalog,
    private val networkCatalog: INetworkCatalog,
    private val appEventBus: IAppEventBus,
    private val cacheStore: IAppCacheStore,
    private val getLatestAssetPricesUseCase: GetLatestAssetPricesUseCase,
    /** TASK-54 — shared observable Toman rate; replaces this VM's private cache. */
    private val usdToIrrRateProvider: IUsdToIrrRateProvider,
    private val observeActiveWalletUseCase: ObserveActiveWalletUseCase,
    private val getActiveWalletUseCase: GetActiveWalletUseCase,
    private val loadExistingWalletUseCase: LoadExistingWalletUseCase,
    private val getBalancesForMultipleWalletsUseCase: GetBalancesForMultipleWalletsUseCase,
    private val hasWalletUseCase: HasWalletUseCase,
    private val getNetworkTypeForAddressUseCase: GetNetworkTypeForAddressUseCase,
    private val getNetworkTypeByIdUseCase: GetNetworkTypeByIdUseCase,
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

    /**
     * TASK-54 — نرخ تتر به تومان دیگر اینجا کش نمی‌شود.
     *
     * The private `cachedIrrRate` field this replaces was invisible to the UI: a fresh Wallex rate
     * updated the field, but nothing could observe a field, so screens kept rendering the value they
     * had pulled once. [usdToIrrRate] is the shared observable state; collect it.
     */
    val usdToIrrRate: StateFlow<CurrencyRate?> = usdToIrrRateProvider.rate

    /** Latest known rate, or ZERO when it is not known yet. For non-reactive internal math only. */
    private val currentIrrRate: BigDecimal
        get() = usdToIrrRateProvider.rate.value?.rate ?: BigDecimal.ZERO

    // زمان‌بندی‌های مجزا برای رفرش دیتای خودکار
    private val RR_PRICE_REFRESH_INTERVAL = 5 * 60 * 1000L // 2 دقیقه برای قیمت‌ها
    private val RR_BALANCE_REFRESH_INTERVAL = 10 * 60 * 1000L // 5 دقیقه برای موجودی‌ها

    // کلیدهای ذخیره زمان آخرین آپدیت در کش
    private val LAST_PRICE_SYNC_TIME_KEY = "last_price_sync_time"
    private val LAST_BALANCE_SYNC_TIME_KEY = "last_balance_sync_time"


    val activeWallet = observeActiveWalletUseCase()

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
        launchSafe(checkNetwork = false) {
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

    fun getNetworkTypeForAddress(address: String): NetworkType? {
        return getNetworkTypeForAddressUseCase(address)
    }

    fun getNetworkTypeForNetworkId(networkId: String): NetworkType? {
        return getNetworkTypeByIdUseCase(networkId)
    }

    private fun loadWalletIfNeeded() {
        launchSafe {
            if (getActiveWalletUseCase() != null) return@launchSafe
            when (val result = loadExistingWalletUseCase()) {
                is ResultResponse.Success -> {
                    result.data?.let { Timber.i("Wallet loaded: ${it.name}") }
                }
                is ResultResponse.Error -> {
                    _uiState.value = HomeUiState.Error("خطا در لود کیف پول")
                    // Nothing is on screen without a wallet — the user must be told.
                    reportError(
                        throwable = result.exception,
                        userAction = "loadWalletIfNeeded",
                        surface = ErrorSurface.SNACKBAR,
                        severity = ErrorSeverity.HIGH,
                        fallbackMessage = "خطا در لود کیف پول"
                    )
                }
            }
        }
    }

    private fun observeActiveWallet() {
        launchSafe {
            observeActiveWalletUseCase().collect { wallet ->
                // کنسل کردن کارهای قبلی برای جلوگیری از نشت دیتا بین والت‌ها
                dataFetchJob?.cancel()

                if (wallet != null) {
                    currentWalletId = wallet.id
                    dataFetchJob = launchSafe {
                        // در لحظه سوئیچ فقط کش را نشان بده و آپدیت خودکار نکن
                        loadHomePageData(wallet, forceUpdate = false)
                    }
                } else {
                    if (!hasWalletUseCase()) {
                        _uiState.value = HomeUiState.Error("کیف پولی یافت نشد.")
                        showErrorSnackbar("کیف پولی یافت نشد.")
                    }
                }
            }
        }
    }

    private suspend fun loadHomePageData(activeWallet: Wallet, forceUpdate: Boolean = false) {
        val allSupportedAssets = assetCatalog.getAllAssetConfigs()

        // ۱. لود از کش
        val cachedAssetsMap = mutableMapOf<String, CachedAssetBalance>()
        allSupportedAssets.forEach { config ->
            cacheStore.get(getAssetCacheKey(activeWallet.id, config.id), CachedAssetBalance::class.java)?.let {
                cachedAssetsMap[config.id] = it
            }
        }

        val hasAnyCache = cachedAssetsMap.isNotEmpty()

        // ۲. ساخت لیست بر اساس کش یا مقادیر اولیه
        val localAssets = allSupportedAssets.map { config ->
            val cached = cachedAssetsMap[config.id]
            val network = networkCatalog.getNetworkInfoById(config.networkId)
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
        // TASK-54 — seed-only: reads the persisted last-known rate, never the network, so this path
        // stays as fast as the direct cache read it replaces. The network refresh happens in
        // refreshPrices() and reaches the UI through the observable rate.
        usdToIrrRateProvider.ensureSeeded()
        val irrRate = currentIrrRate
        val usdtRate = BigDecimal.ONE

        // محاسبه اولیه برای نمایش سریع کش
        val initialAggregated = createAggregatedListWithRates(localAssets, usdtRate, irrRate)

        val totalUsd = localAssets.sumOf { it.balanceRaw * it.priceUsdRaw }
        val totalUsdt = if (usdtRate > BigDecimal.ZERO) totalUsd / usdtRate else BigDecimal.ZERO
        val totalIrr = totalUsd * irrRate

        // ۳. تصمیم‌گیری برای آپدیت خودکار بر اساس زمان آخرین همگام‌سازی
        val currentTime = System.currentTimeMillis()

        val lastPriceSync = cacheStore.get(LAST_PRICE_SYNC_TIME_KEY, Long::class.javaObjectType) ?: 0L
        val lastBalanceSync = cacheStore.get(LAST_BALANCE_SYNC_TIME_KEY, Long::class.javaObjectType) ?: 0L

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
            // TASK-54 — the provider owns the TTL, the persistence and the shared state; refreshing it
            // is what makes every observing screen update, which the old private field could not do.
            usdToIrrRateProvider.refresh()
            val usdtRate = BigDecimal.ONE
            val irrRate = currentIrrRate

            val allAssets = assetCatalog.getAllAssetConfigs()
            val symbols = allAssets.map { it.symbol }.distinct()
            val ids = allAssets.map { it.id }.distinct()

            val resultPair: Pair<List<String>, List<String>> = Pair(symbols, ids)

            val result = getLatestAssetPricesUseCase(resultPair)
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
                                cacheStore.put(getAssetCacheKey(currentWalletId ?: "", updatedItem.id), CachedAssetBalance(
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
                cacheStore.put(LAST_PRICE_SYNC_TIME_KEY, System.currentTimeMillis())
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
            val irrRate = currentIrrRate
            val usdtRate = BigDecimal.ONE

            val jobs = networkCatalog.getAllNetworkInfos().map { network ->
                launchSafe {
                    val result = getBalancesForMultipleWalletsUseCase(network.name, listOf(wallet.id))
                    if (result is ResultResponse.Success) {
                        val walletAssets = result.data[wallet.id] ?: return@launchSafe
                        val assetsInNetwork = assetCatalog.getAssetConfigsForNetwork(network.id)

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
            cacheStore.put(LAST_BALANCE_SYNC_TIME_KEY, System.currentTimeMillis())
            _uiState.update { if (it is HomeUiState.Success) it.copy(isUpdating = false) else it }
        }
    }

    private fun refreshSingleAssetBalance(event: AppEvent.WalletAssetNeedsRefresh) {
        val wallet = getActiveWalletUseCase() ?: return
        val network = networkCatalog.getNetworkInfoById(event.networkId) ?: return
        val networkConfigs = assetCatalog.getAssetConfigsForNetwork(event.networkId)
        // Match the invalidated asset by local id, by symbol (the server sends e.g. "usdt", never our
        // composite "USDT-SEPOLIA" id), or by contract when the event carries one. The previous logic fell
        // through to `contractAddress == null` whenever no contract was present, which silently refreshed the
        // NATIVE asset instead of the token — so a token balance.invalidated never updated its balance.
        val targetConfigs = networkConfigs.filter { config ->
            config.id.equals(event.assetId, ignoreCase = true) ||
                config.symbol.equals(event.assetId, ignoreCase = true) ||
                (!event.contractAddress.isNullOrBlank() &&
                    config.contractAddress.equals(event.contractAddress, ignoreCase = true))
        }.ifEmpty {
            // Unknown/blank assetId (vocabulary mismatch or a coarse signal) → refresh the whole network
            // rather than guess; a missing assetId must never silently skip the balance update.
            networkConfigs
        }

        if (targetConfigs.isEmpty()) return

        // Background (realtime-triggered) refresh — an offline device must not produce a snackbar
        // on every invalidation event.
        launchSafe(connectivitySurface = ErrorSurface.SILENT) {
            _uiState.update { if (it is HomeUiState.Success) it.copy(isUpdating = true) else it }

            try {
                val irrRate = currentIrrRate
                val usdtRate = BigDecimal.ONE

                when (val result = getBalancesForMultipleWalletsUseCase(network.name, listOf(wallet.id))) {
                    is ResultResponse.Success -> {
                        val walletAssets = result.data[wallet.id].orEmpty()
                        targetConfigs.forEach { config ->
                            val refreshed = walletAssets.firstOrNull { asset ->
                                asset.symbol.equals(config.symbol, ignoreCase = true) &&
                                    (config.contractAddress?.equals(asset.contractAddress, true)
                                        ?: asset.contractAddress.isNullOrBlank())
                            }
                            updateAssetItemAndTotal(
                                walletId = wallet.id,
                                assetConfig = config,
                                balance = refreshed?.balance ?: BigDecimal.ZERO,
                                pricesMap = emptyMap(),
                                usdtRate = usdtRate,
                                irrRate = irrRate
                            )
                        }
                    }

                    // Realtime-driven background refresh: the previous figure stays on screen and
                    // the next event retries, so this logs and stops (ErrorSurface.SILENT).
                    is ResultResponse.Error -> reportError(
                        throwable = result.exception,
                        userAction = "targetedBalanceRefresh(${event.networkId}/${event.assetId})",
                        surface = ErrorSurface.SILENT,
                        severity = ErrorSeverity.LOW
                    )
                }
            } finally {
                _uiState.update { if (it is HomeUiState.Success) it.copy(isUpdating = false) else it }
            }
        }
    }

    private fun updateAssetItemAndTotal(
        walletId: String,
        assetConfig: AssetConfig,
        balance: BigDecimal,
        pricesMap: Map<String, AssetPriceDto>,
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
                    cacheStore.put(getAssetCacheKey(walletId, assetConfig.id), CachedAssetBalance(
                        assetId = assetConfig.id, walletId = walletId, balanceRaw = balance,
                        priceUsdRaw = currentPrice,
                        balance = updatedAsset.balance, balanceUsdt = updatedAsset.balanceUsdt,
                        balanceIrr = updatedAsset.balanceIrr, priceChange24h = updatedAsset.priceChange24h
                    ), ttl = ASSETS_TTL)
                }
            } else {
                val network = networkCatalog.getNetworkInfoById(assetConfig.networkId)
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
        val irrRate = currentIrrRate
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
                        NetworkShare(
                            it.networkId, it.networkName.removePrefix("on ").trim(),
                            networkCatalog.getNetworkInfoById(it.networkId)?.color ?: "#888888", pct
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
                        decimals = first.decimals, isNativeToken = first.isNativeToken, isGroupHeader = true, groupAssets = assets, networkDistribution = dist
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

    /**
     * TASK-54 — نرخ تتر به تومان را تازه می‌کند؛ نتیجه روی [usdToIrrRate] منتشر می‌شود.
     *
     * This replaces the old `suspend fun getUsdToIrrRate(): BigDecimal`. Returning the rate invited
     * callers to snapshot it into a local `var` and never look again — which is precisely why a fresh
     * Wallex rate reached the log but not the screen. There is deliberately no return value: observe
     * [usdToIrrRate] instead.
     */
    suspend fun refreshUsdToIrrRate(force: Boolean = false) = usdToIrrRateProvider.refresh(force)

    // Item 4 (revised) — unlike the history screen, the wallet screen is the SINGLE writer of the shared
    // per-asset balance cache (asset_balance_*) that EVERY other screen reads on open (asset detail,
    // multi-wallet, …). Deferring its data refresh while the wallet tab is hidden would leave that cache
    // holding the pre-transaction balance, so freshly-opened screens would show a stale value — exactly the
    // "balance came correct but every screen still shows the old amount" bug. We therefore ALWAYS process
    // balance signals here — keeping the shared cache fresh — regardless of which tab is visible. Item 4's
    // intent ("see the update after entering the screen") is still met for the wallet UI itself: it reflects
    // the latest via its StateFlow on re-entry, no deferral needed.
    private fun listenToGlobalEvents() {
        launchSafe(checkNetwork = false) {
            appEventBus.events.collect { event ->
                when (event) {
                    is AppEvent.WalletNeedsRefresh -> refreshData()
                    is AppEvent.WalletAssetNeedsRefresh -> refreshSingleAssetBalance(event)
                    else -> Unit
                }
            }
        }
    }

    fun refreshData() {
        getActiveWalletUseCase()?.let {
            dataFetchJob?.cancel()
            dataFetchJob = launchSafe {
                // در رفرش دستی، هر دو را اجباری آپدیت می‌کنیم
                refreshPrices()
                refreshBalances(it)
            }
        }
    }
}


