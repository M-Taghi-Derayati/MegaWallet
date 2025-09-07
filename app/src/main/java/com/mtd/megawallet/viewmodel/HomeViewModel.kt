package com.mtd.megawallet.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtd.core.assets.AssetConfig
import com.mtd.core.model.NetworkName
import com.mtd.core.model.NetworkType
import com.mtd.core.model.WalletKey
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.BalanceFormatter
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.repository.IWalletRepository
import com.mtd.domain.model.BitcoinTransaction
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.Wallet
import com.mtd.domain.repository.IMarketDataRepository
import com.mtd.megawallet.event.HomeUiState
import com.mtd.megawallet.event.HomeUiState.ActivityItem
import com.mtd.megawallet.event.HomeUiState.AssetItem
import com.mtd.megawallet.event.HomeUiState.AssetWithBalance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject


@HiltViewModel
class HomeViewModel @Inject constructor(
    private val walletRepository: IWalletRepository,
    private val marketDataRepository: IMarketDataRepository,
    private val dataSourceFactory: ChainDataSourceFactory,
    private val assetRegistry: AssetRegistry,
    private val blockchainRegistry: BlockchainRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        loadHomePageData()
    }

    fun refreshData() {
        loadHomePageData()
    }

    private suspend fun fetchAllTransactions(wallet: Wallet): List<TransactionRecord> {
        // استفاده از coroutineScope برای ایجاد یک حیطه امن برای فراخوانی‌های موازی
        return coroutineScope {
            wallet.keys.map { key ->
                // هر درخواست در یک Coroutine جداگانه (async) اجرا می‌شود
                async(Dispatchers.IO) { // بهتر است روی ترد IO اجرا شود
                    try {
                        // ۱. اطمینان از وجود chainId (برای شبکه‌های غیر EVM می‌توان از یک مقدار پیش‌فرض استفاده کرد)
                        val chainId = key.chainId ?: -1
                        val dataSource = dataSourceFactory.create(chainId)

                        // ۲. فراخوانی getTransactionHistory برای هر کلید/شبکه
                        when (val result = dataSource.getTransactionHistory(key.address)) {
                            is ResultResponse.Success -> {
                                // --- بخش کامل شده ---
                                // ۳. به هر تراکنش، نام شبکه (NetworkName) مربوط به کلید را اضافه می‌کنیم
                                result.data.forEach { transaction ->
                                    transaction.networkName = key.networkName
                                }
                                // حالا لیست تراکنش‌ها با اطلاعات کامل شبکه برگردانده می‌شود
                                result.data
                                // ---
                            }
                            is ResultResponse.Error -> {
                                // اگر برای یک شبکه خطا رخ داد، لاگ می‌گیریم و یک لیست خالی برمی‌گردانیم
                                Log.e("FetchTransactions", "Failed to fetch history for ${key.networkName}: ${result.exception.message}")
                                emptyList<TransactionRecord>()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FetchTransactions", "Exception while fetching history for ${key.networkName}", e)
                        emptyList<TransactionRecord>()
                    }
                }
            }.awaitAll().flatten() // منتظر همه نتایج می‌مانیم و آنها را در یک لیست واحد ادغام می‌کنیم
        }
    }

    /**
     * متد اصلی برای بارگذاری تمام داده‌های صفحه اصلی.
     */
    private fun loadHomePageData() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                // ۱. بارگذاری کیف پول فعال
                val walletResult = walletRepository.loadExistingWallet()
                if (walletResult !is ResultResponse.Success || walletResult.data == null) {
                    _uiState.value = HomeUiState.Error("کیف پول پیدا نشد.")
                    return@launch
                }
                val activeWallet = walletResult.data!!

                // ۲. نمایش فوری اسکلت UI
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
                        balanceUsd = "...",
                        balanceRaw = BigDecimal.ZERO,
                        priceUsdRaw = BigDecimal.ZERO,
                        decimals = assetConfig.decimals,
                        contractAddress = assetConfig.contractAddress,
                        isNativeToken = assetConfig.contractAddress == null
                    )
                }
                _uiState.value = HomeUiState.Success(
                    totalBalanceUsd = "...",
                    isUpdating = true,
                    assets = initialAssetItems,
                    recentActivity = emptyList()
                )

                // ۳. بارگذاری داده‌های واقعی در پس‌زمینه به صورت موازی
                val assetsWithBalanceDeferred =
                    async { fetchAllBalances(activeWallet, allSupportedAssets) }
                val transactionsDeferred = async { fetchAllTransactions(activeWallet) }

                val assetsWithBalance = assetsWithBalanceDeferred.await()
                val allTransactions = transactionsDeferred.await()

                // ۴. گرفتن قیمت‌ها
                val assetIdsForPricing = allSupportedAssets.mapNotNull { it.coinGeckoId }.distinct()
                val pricesResult = if (assetIdsForPricing.isNotEmpty()) {
                    marketDataRepository.getLatestPrices(assetIdsForPricing)
                } else {
                    ResultResponse.Success(emptyList())
                }
                val priceMap =
                    (pricesResult as? ResultResponse.Success)?.data?.associateBy { it.assetId }
                        ?: emptyMap()

                // ۵. تبدیل نهایی داده‌ها و محاسبه موجودی کل
                var totalBalanceUsd = BigDecimal.ZERO
                val finalAssetItems = assetsWithBalance.map { assetWithBalance ->
                    val config = assetWithBalance.config
                    val priceInfo = priceMap[config.coinGeckoId]
                    val priceUsd = priceInfo?.priceUsd ?: BigDecimal.ZERO
                    val balanceDecimal =
                        BigDecimal(assetWithBalance.balance).divide(BigDecimal.TEN.pow(config.decimals))
                    val balanceUsd = balanceDecimal * priceUsd
                    totalBalanceUsd += balanceUsd

                    AssetItem(
                        id = config.id,
                        name = config.name,
                        symbol = config.symbol,
                        networkName = "on ${blockchainRegistry.getNetworkById(config.networkId)?.name?.name?.lowercase()}",
                        networkId = config.networkId,
                        iconUrl = config.iconUrl,
                        balance = BalanceFormatter.formatBalance(
                            assetWithBalance.balance,
                            config.decimals
                        ),
                        balanceUsd = BalanceFormatter.formatUsdValue(balanceUsd),
                        balanceRaw = balanceDecimal,
                        priceUsdRaw = priceUsd,
                        decimals = config.decimals,
                        contractAddress = config.contractAddress,
                        isNativeToken = config.contractAddress == null,
                        priceChange24h = priceInfo?.priceChanges24h?.toDouble() ?: 0.0
                    )
                }

                // تبدیل تراکنش‌ها به ActivityItem
                val activityItems = allTransactions
                    .sortedByDescending { it.timestamp } // جدیدترین‌ها اول
                    .take(20) // فقط ۲۰ تای آخر
                    .mapNotNull { transaction -> // استفاده از mapNotNull برای نادیده گرفتن تراکنش‌های ناشناخته
                        mapTransactionToActivityItem(transaction, activeWallet.keys)
                    }

                // ۶. انتشار State نهایی
                _uiState.value = HomeUiState.Success(
                    totalBalanceUsd = BalanceFormatter.formatUsdValue(totalBalanceUsd),
                    isUpdating = false,
                    assets = finalAssetItems.sortedByDescending {
                        it.balanceUsd.removePrefix("$").replace(",", "").toDouble()
                    },
                    recentActivity = activityItems
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "خطای ناشناخته")
            }
        }
    }


    /*  private fun loadHomePageData() {
        viewModelScope.launch {
            // --- مرحله ۱: بارگذاری کیف پول و نمایش فوری اسکلت UI ---
            _uiState.value = HomeUiState.Loading
            val walletResult = walletRepository.loadExistingWallet()
            if (walletResult !is ResultResponse.Success || walletResult.data == null) {
                _uiState.value = HomeUiState.Error("کیف پول پیدا نشد.")
                return@launch
            }
            val activeWallet = walletResult.data!!

            // تمام دارایی‌های پشتیبانی شده را از رجیستری می‌خوانیم
            val allSupportedAssets = assetRegistry.getAllAssets()

            // یک لیست اولیه از AssetItem ها با مقادیر "در حال بارگذاری" می‌سازیم
            val initialAssetItems = allSupportedAssets.map { assetConfig ->
                AssetItem(
                    id = assetConfig.id,
                    name = assetConfig.name,
                    networkName = "on ${blockchainRegistry.getNetworkById(assetConfig.networkId)?.name?.name?.lowercase()}",
                    networkId = "",
                    symbol = assetConfig.symbol,
                    iconUrl = assetConfig.iconUrl,
                    balance = "...",
                    balanceUsd = "...",
                    priceChange24h = 0.0,
                    balanceRaw = BigDecimal.ZERO
                )
            }

            // **اولین انتشار State:** UI را با اسکلت اولیه نمایش می‌دهیم
            _uiState.value = HomeUiState.Success(
                totalBalanceUsd = "...",
                isUpdating = true,
                assets = initialAssetItems,
                recentActivity = emptyList() // تاریخچه بعداً لود می‌شود
            )

            // --- مرحله ۲: بارگذاری موجودی‌ها و قیمت‌ها در پس‌زمینه ---

            // گرفتن قیمت‌ها
            val assetIdsForPricing = allSupportedAssets.mapNotNull { it.coinGeckoId }.distinct()
            val pricesResult = marketDataRepository.getLatestPrices(assetIdsForPricing)
            val priceMap = (pricesResult as? ResultResponse.Success)?.data?.associateBy { it.assetId } ?: emptyMap()

            // گرفتن موجودی‌ها
            val assetsWithBalance = fetchAllBalances(activeWallet, allSupportedAssets)

            // --- مرحله ۳: آپدیت نهایی UI با داده‌های واقعی ---
            var totalBalanceUsd = BigDecimal.ZERO
            val finalAssetItems = assetsWithBalance.map { assetWithBalance ->
                val priceInfo = priceMap[assetWithBalance.config.coinGeckoId]
                val priceUsd = priceInfo?.priceUsd ?: BigDecimal.ZERO
                val balanceDecimal = BigDecimal(assetWithBalance.balance).divide(BigDecimal.TEN.pow(assetWithBalance.config.decimals))
                val balanceUsd = balanceDecimal * priceUsd
                totalBalanceUsd += balanceUsd

                AssetItem(
                    id = assetWithBalance.config.id,
                    name = assetWithBalance.config.name,
                    networkName = "on ${blockchainRegistry.getNetworkById(assetWithBalance.config.networkId)?.name?.name?.lowercase()}",
                    iconUrl = assetWithBalance.config.iconUrl,
                    networkId = "",
                    symbol = assetWithBalance.config.symbol,
                    balance = BalanceFormatter.formatBalance(assetWithBalance.balance, assetWithBalance.config.decimals),
                    balanceUsd = BalanceFormatter.formatUsdValue(balanceUsd),
                    priceChange24h = priceInfo?.priceChanges24h?.toDouble() ?: 0.0,
                    balanceRaw = BigDecimal.ZERO
                )
            }

            // **دومین انتشار State:** UI را با داده‌های کامل و نهایی آپدیت می‌کنیم
            _uiState.value = HomeUiState.Success(
                totalBalanceUsd = BalanceFormatter.formatUsdValue(totalBalanceUsd),
                isUpdating = false,
                assets = finalAssetItems.sortedByDescending { it.balanceUsd.removePrefix("$").replace(",", "").toDouble() },
                recentActivity = emptyList() // TODO: Fetch recent activity
            )
        }
    }*/

    private suspend fun fetchAllBalances(
        wallet: Wallet,
        assets: List<AssetConfig>
    ): List<AssetWithBalance> {
        return coroutineScope {
            // دارایی‌ها را بر اساس شبکه گروه‌بندی می‌کنیم تا درخواست‌ها بهینه شوند
            val assetsByNetwork = assets.groupBy { it.networkId }

            assetsByNetwork.map { (networkId, assetsInNetwork) ->
                async {
                    val networkInfo =
                        blockchainRegistry.getNetworkById(networkId) ?: return@async emptyList()
                    val key =
                        wallet.keys.find { it.networkName.name.lowercase() == networkInfo.name.name.lowercase() }
                            ?: return@async emptyList()
                    val dataSource = dataSourceFactory.create(networkInfo.chainId ?: -1)

                    if (networkInfo.networkType == NetworkType.EVM) {
                        val result = dataSource.getBalanceEVM(key.address)
                        if (result is ResultResponse.Success) {
                            result.data.mapNotNull { fetchedAsset ->
                                val config = assetsInNetwork.find {
                                    it.symbol == fetchedAsset.symbol && (it.contractAddress == null || it.contractAddress.equals(
                                        fetchedAsset.contractAddress,
                                        true
                                    ))
                                }
                                config?.let {
                                    AssetWithBalance(it, fetchedAsset.balance)
                                }
                            }
                        } else emptyList()
                    } else { // Bitcoin and other non-EVM chains
                        val result = dataSource.getBalance(key.address)
                        if (result is ResultResponse.Success) {
                            assetsInNetwork.firstOrNull()
                                ?.let { listOf(AssetWithBalance(it, result.data)) } ?: emptyList()
                        } else emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
    }


    private fun mapTransactionToActivityItem(
        transaction: TransactionRecord,
        userKeys: List<WalletKey> // لیست تمام کلیدهای کاربر
    ): ActivityItem? {

        return when (transaction) {
            is EvmTransaction -> {
                // ۱. پیدا کردن شبکه مربوط به این تراکنش
                // ما آدرس کاربر رو از لیست کلیدها پیدا می‌کنیم که در این تراکنش حضور داره
                val userAddressInTx = userKeys.find {
                    it.address.equals(transaction.fromAddress, true) || it.address.equals(transaction.toAddress, true)
                }?.address

                val keyForNetwork = userKeys.find {
                    it.address.equals(userAddressInTx, true) && it.networkName == transaction.networkName
                }
                val network = blockchainRegistry.getNetworkByName(keyForNetwork?.networkName ?: return null) ?: return null

                // --- منطق جدید و اصلاح شده برای پیدا کردن دارایی ---
                var identifiedAsset: AssetConfig?

                // ابتدا چک می‌کنیم آیا این یک تراکنش توکن ERC20 است یا نه.
                // تراکنش‌های ERC20 همیشه به یک آدرس قرارداد ارسال میشن.
                // و مقدار value (ارسال ETH) معمولا صفر است.
                val possibleToken = assetRegistry.getAllAssets().find {
                    it.contractAddress != null && it.contractAddress.equals(transaction.toAddress, true)
                }

                identifiedAsset = // این یک تراکنش توکن ERC20 است (مثل ارسال USDC)
                    possibleToken ?: // این یک تراکنش توکن اصلی شبکه است (Native ETH, BNB, etc.)
                            // یا یک دریافت توکن ERC20 است که باید لاگ‌ها رو بررسی کنیم (فعلا ساده‌سازی می‌کنیم)
                            assetRegistry.getAssetsForNetwork(network.id).find { it.contractAddress == null }

                if (identifiedAsset == null) return null // اگر دارایی پیدا نشد، این تراکنش را نادیده بگیر

                // --- بقیه منطق بدون تغییر، فقط از identifiedAsset استفاده می‌کند ---
                val amountDecimal = BigDecimal(transaction.amount)
                    .divide(BigDecimal.TEN.pow(identifiedAsset.decimals))


                val title: String
                val subtitle: String
                val type: HomeUiState.ActivityType

                if (transaction.isOutgoing) {
                    type = HomeUiState.ActivityType.SEND
                    title = "ارسال ${identifiedAsset.symbol}"
                    // برای ارسال توکن، گیرنده واقعی در Input Data هست، اما برای سادگی فعلا از toAddress استفاده می‌کنیم
                    // در آینده باید Input Data رو پارس کنیم تا گیرنده واقعی رو پیدا کنیم.
                    subtitle = "به: ${transaction.toAddress.take(6)}...${transaction.toAddress.takeLast(4)}"
                } else {
                    type = HomeUiState.ActivityType.RECEIVE
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
                val network =
                    blockchainRegistry.getNetworkByName(NetworkName.BITCOINTESTNET) // فرض تست نت
                val asset = assetRegistry.getAssetsForNetwork(network?.id ?: "")
                    .first() // بیت کوین فقط یک دارایی اصلی دارد

                val amountDecimal = BigDecimal(transaction.amount).divide(BigDecimal.TEN.pow(asset.decimals))

                val title: String
                val subtitle: String
                val type: HomeUiState.ActivityType

                if (transaction.isOutgoing) {
                    type = HomeUiState.ActivityType.SEND
                    title = "ارسال ${asset.symbol}"
                    subtitle =
                        "به: ${transaction.toAddress?.take(6)}...${transaction.toAddress?.takeLast(4)}"
                } else {
                    type = HomeUiState.ActivityType.RECEIVE
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

            else -> null // برای انواع تراکنش ناشناخته
        }
    }
}