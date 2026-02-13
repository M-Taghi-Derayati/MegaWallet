package com.mtd.megawallet.viewmodel.news

import android.content.Intent
import com.mtd.core.manager.CacheManager
import com.mtd.core.manager.ErrorManager
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.BalanceFormatter
import com.mtd.data.datasource.ICloudDataSource
import com.mtd.data.repository.IBackupRepository
import com.mtd.data.repository.IWalletRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mtd.domain.model.Asset
import com.mtd.domain.model.AssetPrice
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.Wallet
import com.mtd.domain.repository.IAuthManager
import com.mtd.domain.repository.IMarketDataRepository
import com.mtd.domain.wallet.ActiveWalletManager
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.event.CachedAssetBalance
import com.mtd.megawallet.event.CloudWalletMetadata
import com.mtd.megawallet.viewmodel.news.HomeViewModel.Companion.CACHE_KEY_PREFIX
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class MultiWalletViewModel @Inject constructor(
    private val walletRepository: IWalletRepository,
    private val cloudDataSource: ICloudDataSource,
    private val authManager: IAuthManager,
    private val backupRepository: IBackupRepository,
    private val gson: Gson,
    private val marketDataRepository: IMarketDataRepository,
    private val activeWalletManager: ActiveWalletManager,
    private val cacheManager: CacheManager,
    private val assetRegistry: AssetRegistry,
    private val blockchainRegistry: BlockchainRegistry,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    private    companion object {
        const val LAST_WALLETS_SYNC_TIME_KEY = "last_balance_sync_time"
        const val LAST_PRICE_SYNC_TIME_KEY = "last_price_sync_time"
        const val MIN_UPDATE_INTERVAL = 10 * 60 * 1000L // 5 minutes for balance
        const val PRICE_UPDATE_INTERVAL = 5 * 60 * 1000L // 2 minutes for prices
    }

    private val _wallets = MutableStateFlow<List<WalletUiItem>>(emptyList())
    val wallets = _wallets.asStateFlow()

    private val _activeWalletId = activeWalletManager.activeWalletId
    val activeWalletId = _activeWalletId

    data class WalletUiItem(
        val wallet: Wallet,
        val totalBalance: String = "$0",
        val isActive: Boolean = false,
        val isManualBackedUp: Boolean = false,
        val isCloudBackedUp: Boolean = false
    )

    init {
        launchSafe {
            loadWallets()
        }
    }

    suspend fun loadWallets(forceRefresh: Boolean = false) {
        when (val result = walletRepository.getAllWallets()) {
            is ResultResponse.Success -> {
                val walletList = result.data
                val currentActiveId = activeWalletManager.activeWalletId.value
                
                // 1. نمایش سریع اطلاعات از کش (بدون درنگ)
                updateWalletsUi(walletList, currentActiveId)

                // 2. آپدیت اطلاعات از شبکه به صورت Batch (یکجا)
                refreshAllWalletsBalances(walletList, forceRefresh)
            }
            is ResultResponse.Error -> {
                errorManager.showSnackbar("خطا در دریافت لیست کیف پول‌ها")
            }
        }
    }

    private suspend fun updateWalletsUi(walletList: List<Wallet>, activeId: String?) {
        // انتقال محاسبات سنگین به ترد Default برای جلوگیری از لگ UI
        val items = withContext(kotlinx.coroutines.Dispatchers.Default) {
            val deferredItems = walletList.map { wallet ->
                async {
                    WalletUiItem(
                        wallet = wallet,
                        totalBalance = try { calculateWalletBalanceFromCache(wallet.id) } catch(e: Exception) { "$0" },
                        isActive = wallet.id == activeId,
                        isManualBackedUp = wallet.isManualBackedUp,
                        isCloudBackedUp = wallet.isCloudBackedUp
                    )
                }
            }
            deferredItems.awaitAll()
        }
        _wallets.value = items
    }

    private val isRefreshing = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * دریافت موجودی تمام کیف پول‌ها از شبکه به صورت بهینه (Batch Request)
     * این متد تمام کیف پول‌ها را جمع کرده و در قالب یک درخواست به نود اتریوم (یا سایر) می‌فرستد.
     */
    private suspend fun refreshAllWalletsBalances(wallets: List<Wallet>, forceResync: Boolean) {
        if (wallets.isEmpty()) return

        // جلوگیری از اجرای همزمان
        if (isRefreshing.getAndSet(true)) {
            return
        }

        try {
            val currentTime = System.currentTimeMillis()
            
            // ۱. بررسی آپدیت قیمت‌ها
            val lastPriceSync = cacheManager.get(LAST_PRICE_SYNC_TIME_KEY, Long::class.javaObjectType) ?: 0L
            if (forceResync || currentTime - lastPriceSync > PRICE_UPDATE_INTERVAL) {
                refreshAllPrices()
            }

            // ۲. بررسی آپدیت موجودی‌ها
            val lastBalanceSync = cacheManager.get(LAST_WALLETS_SYNC_TIME_KEY, Long::class.javaObjectType) ?: 0L
            if (forceResync || currentTime - lastBalanceSync > MIN_UPDATE_INTERVAL) {
                refreshAllBalances(wallets, forceResync)
            } else {
                // اگر فقط موجودی آپدیت نشد، باز هم UI را رفرش می‌کنیم (چون قیمت‌ها ممکن است آپدیت شده باشند)
                updateWalletsUi(wallets, activeWalletManager.activeWalletId.value)
            }
        } finally {
            isRefreshing.set(false)
        }
    }

    /**
     * رفرش تمام قیمت‌ها از مارکت
     */
    private suspend fun refreshAllPrices() {
        val allAssets = assetRegistry.getAllAssets()
        val allCoinGeckoIds = allAssets.map { it.symbol }.distinct()
        val pricesResult = marketDataRepository.getLatestPrices(allCoinGeckoIds)
        
        if (pricesResult is ResultResponse.Success) {
            val pricesMap = pricesResult.data.associateBy { it.assetId } // assetId = coinGeckoId
            val currentWallets = _wallets.value.map { it.wallet }
            
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                currentWallets.forEach { wallet ->
                    allAssets.forEach { config ->
                        val priceInfo = config.symbol.let { pricesMap[it] }
                        if (priceInfo != null) {
                            updateAssetPriceCache(wallet.id, config.id, priceInfo)
                        }
                    }
                }
            }
            cacheManager.put(LAST_PRICE_SYNC_TIME_KEY, System.currentTimeMillis())
            // بلافاصله UI را با قیمت‌های جدید آپدیت کن
            updateWalletsUi(currentWallets, activeWalletManager.activeWalletId.value)
        }
    }

    /**
     * رفرش موجودی تمام کیف پول‌ها از شبکه
     */
    private suspend fun refreshAllBalances(wallets: List<Wallet>, forceResync: Boolean) {
        val activeId = activeWalletManager.activeWalletId.value
        
        // تشخیص اینکه آیا کیف پول فعال دیتای معتبری در کش دارد یا خیر
        val isActiveWalletCached = if (activeId != null) {
            val total = calculateWalletBalanceFromCache(activeId)
            total != "$0" && total != "0"
        } else false

        // اگر forceResync باشد یا دیتای کش والت فعال موجود نباشد، آن را هم در لیست آپدیت قرار می‌دهیم
        val walletsToFetch = wallets.map { it.id }.filter { id ->
            forceResync || id != activeId || !isActiveWalletCached
        }

        if (walletsToFetch.isEmpty()) {
            Timber.d("BATCH_DEBUG: Cache is fresh and skip active wallet. No network fetch needed.")
            updateWalletsUi(wallets, activeId)
            return
        }

        Timber.d("BATCH_DEBUG: Fetching balances for ${walletsToFetch.size} wallets: $walletsToFetch")
        val allNetworks = blockchainRegistry.getAllNetworks()

        // اجرای موازی درخواست‌ها برای هر شبکه
        coroutineScope {
            val jobs = allNetworks.map { network ->
                launchSafe {
                    val result = walletRepository.getBalancesForMultipleWallets(network.name, walletsToFetch)
                    if (result is ResultResponse.Success) {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            result.data.forEach { (walletId, assets) ->
                                assets.forEach { asset ->
                                     updateAssetBalanceCache(walletId, asset, network.name)
                                }
                            }
                        }
                    }
                }
            }
            jobs.filter { it.isActive }.joinAll()
        }

        cacheManager.put(LAST_WALLETS_SYNC_TIME_KEY, System.currentTimeMillis())
        updateWalletsUi(wallets, activeId)
    }

    private suspend fun updateAssetPriceCache(walletId: String, assetId: String, priceInfo: AssetPrice) {
        val cacheKey = "${CACHE_KEY_PREFIX}${walletId}_${assetId}"
        val oldCache = cacheManager.get(cacheKey, CachedAssetBalance::class.java)
        
        // اگر دیتای قبلی نداریم و موجودی صفر است، فعلاً چیزی ذخیره نمی‌کنیم تا برای والت‌های خالی الکی فایل ساخته نشود.
        // اما اگر دیتای قبلی داریم، حتماً قیمت آن را آپدیت می‌کنیم.
        if (oldCache == null) return

        val updated = oldCache.copy(
            priceUsdRaw = priceInfo.priceUsd,
            priceChange24h = priceInfo.priceChanges24h.toDouble()
        )
        cacheManager.put(cacheKey, updated)
    }

    private suspend fun updateAssetBalanceCache(walletId: String, asset: Asset, networkName: com.mtd.core.model.NetworkName) {
        val networkId = blockchainRegistry.getNetworkByName(networkName)?.id ?: return
        val assetConfig = assetRegistry.getAssetsForNetwork(networkId).find { 
            if (it.contractAddress == null) asset.contractAddress == null && it.symbol.equals(asset.symbol, true)
            else it.contractAddress.equals(asset.contractAddress, true)
        } ?: return

        val cacheKey = "${CACHE_KEY_PREFIX}${walletId}_${assetConfig.id}"
        val oldCache = cacheManager.get(cacheKey, CachedAssetBalance::class.java)
        
        val priceUsd = oldCache?.priceUsdRaw ?: BigDecimal.ZERO
        val priceChange = oldCache?.priceChange24h ?: 0.0
        
        val newBalance = CachedAssetBalance(
            assetId = assetConfig.id,
            walletId = walletId,
            balance = BalanceFormatter.formatBalance(asset.balance, asset.decimals) + " " + asset.symbol,
            balanceRaw = asset.balance.movePointLeft(asset.decimals),
            priceUsdRaw = priceUsd,
            priceChange24h = priceChange,
            balanceUsdt = "...", // در لود بعدی محاسبه می‌شود
            balanceIrr = "..."
        )
        
        cacheManager.put(cacheKey, newBalance)
    }

    private suspend fun calculateWalletBalanceFromCache(walletId: String): String {
        var totalUsdValue = BigDecimal.ZERO
        val allAssets = assetRegistry.getAllAssets()
        
        // برای سرعت بالا، فقط دارایی‌هایی که در کش هستند را پردازش می‌کنیم
        allAssets.forEach { assetConfig ->
            val cacheKey = "${CACHE_KEY_PREFIX}${walletId}_${assetConfig.id}"
            // این تابع فعلاً تک به تک چک می‌کند. در آینده می‌توان کلیدهای والت را یکجا گرفت.
            cacheManager.get(cacheKey, CachedAssetBalance::class.java)?.let { cached ->
                if (cached.balanceRaw > BigDecimal.ZERO && cached.priceUsdRaw > BigDecimal.ZERO) {
                    totalUsdValue = totalUsdValue.add(cached.balanceRaw.multiply(cached.priceUsdRaw))
                }
            }
        }
        
        return if (totalUsdValue <= BigDecimal.ZERO) "$0" 
        else "$" + BalanceFormatter.formatUsdValue(totalUsdValue, false)
    }

    fun switchWallet(walletId: String) {
        launchSafe {
            when (val result = walletRepository.switchActiveWallet(walletId)) {
                is ResultResponse.Success -> {
                    // تغییر موفقیت‌آمیز بود
                }
                is ResultResponse.Error -> {
                    errorManager.showSnackbar("خطا در تغییر کیف پول")
                }
            }
        }
    }

    fun deleteWallet(walletId: String) {
        launchSafe {
            when (val result = walletRepository.deleteWallet(walletId)) {
                is ResultResponse.Success -> {
                    loadWallets(forceRefresh = true) 
                }
                is ResultResponse.Error -> {
                    errorManager.showSnackbar(result.toString() ?: "خطا در حذف کیف پول")
                }
            }
        }
    }

    fun updateBackupStatus(walletId: String, manual: Boolean? = null, cloud: Boolean? = null) {
        launchSafe {
            walletRepository.updateBackupStatus(walletId, manual, cloud)
            loadWallets(forceRefresh = false) // رفرش لیست برای نمایش سریع تغییر در UI
        }
    }

    suspend fun updateWalletName(walletId: String, newName: String) {
        walletRepository.updateWalletName(walletId, newName)
        loadWallets(forceRefresh = false)
    }

    suspend fun updateWalletColor(walletId: String, newColor: Int) {
        walletRepository.updateWalletColor(walletId, newColor)
        loadWallets(forceRefresh = false)
    }

    suspend fun getMnemonic(walletId: String): String? {
        return when (val result = walletRepository.getMnemonic(walletId)) {
            is ResultResponse.Success -> result.data
            else -> null
        }
    }

    suspend fun hasCloudBackup(): Boolean {
        return try {
            backupRepository.hasCloudBackup()
        } catch (_: Exception) {
            false
        }
    }

    fun isCloudConnected(): Boolean = cloudDataSource.isInitialized()

    fun getCloudSignInIntent(): Intent = authManager.getSignInIntent()

    suspend fun handleCloudGoogleSignInResult(data: Intent?): ResultResponse<Boolean> {
        return when (val signInResult = authManager.processSignInResult(data)) {
            is ResultResponse.Success -> {
                try {
                    cloudDataSource.initializeWithAuthCode(signInResult.data)
                    ResultResponse.Success(backupRepository.hasCloudBackup())
                } catch (e: Exception) {
                    ResultResponse.Error(e)
                }
            }

            is ResultResponse.Error -> ResultResponse.Error(signInResult.exception)
        }
    }

    suspend fun backupWalletToCloud(walletId: String, password: String): ResultResponse<Unit> {
        val wallet = when (val walletsResult = walletRepository.getAllWallets()) {
            is ResultResponse.Success -> walletsResult.data.firstOrNull { it.id == walletId }
                ?: return ResultResponse.Error(IllegalStateException("Wallet not found"))

            is ResultResponse.Error -> return ResultResponse.Error(walletsResult.exception)
        }

        val secret = when (val secretResult = walletRepository.getMnemonic(walletId)) {
            is ResultResponse.Success -> secretResult.data
                ?: return ResultResponse.Error(IllegalStateException("Wallet secret not found"))

            is ResultResponse.Error -> return ResultResponse.Error(secretResult.exception)
        }

        val hasExistingBackup = try {
            backupRepository.hasCloudBackup()
        } catch (e: Exception) {
            return ResultResponse.Error(e)
        }

        val existingWallets: List<CloudWalletMetadata> = if (hasExistingBackup) {
            when (val restoreResult = backupRepository.restoreData(password)) {
                is ResultResponse.Success -> {
                    val type = object : TypeToken<List<CloudWalletMetadata>>() {}.type
                    gson.fromJson<List<CloudWalletMetadata>>(restoreResult.data, type) ?: emptyList()
                }

                is ResultResponse.Error -> {
                    return ResultResponse.Error(
                        IllegalArgumentException("Incorrect cloud backup password", restoreResult.exception)
                    )
                }
            }
        } else {
            emptyList()
        }

        val walletMetadata = CloudWalletMetadata(
            id = wallet.id,
            name = wallet.name,
            key = secret,
            colorHex = String.format("#%06X", (0xFFFFFF and wallet.color)),
            isMnemonic = wallet.hasMnemonic
        )

        val mergedWallets = existingWallets
            .filterNot { it.id == walletMetadata.id }
            .plus(walletMetadata)

        val jsonData = gson.toJson(mergedWallets)
        return backupRepository.backupData(jsonData, password)
    }
}
