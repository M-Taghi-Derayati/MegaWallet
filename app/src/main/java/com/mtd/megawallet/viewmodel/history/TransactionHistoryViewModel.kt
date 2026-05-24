package com.mtd.megawallet.viewmodel.history

import com.mtd.core.manager.ErrorManager
import com.mtd.core.utils.AddressUtils
import com.mtd.core.utils.BalanceFormatter
import com.mtd.core.utils.DateTimeUtils.getDateHeader
import com.mtd.domain.interfaceRepository.IAppCacheStore
import com.mtd.domain.interfaceRepository.IAppEventBus
import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.model.AppEvent
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.BitcoinTransaction
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.HISTORY_ALL_NETWORKS_OPTION_ID
import com.mtd.domain.model.HistoryNetworkOption
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionFeeDetails
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import com.mtd.domain.model.TronTransaction
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.core.WalletKey
import com.mtd.domain.usecase.history.BuildHistoryNetworkOptionsUseCase
import com.mtd.domain.usecase.history.BuildPendingHistoryTransactionUseCase
import com.mtd.domain.usecase.history.GetTransactionFeeDetailsUseCase
import com.mtd.domain.usecase.history.GetTransactionHistoryUseCase
import com.mtd.domain.usecase.history.GetWalletAddressBookUseCase
import com.mtd.domain.usecase.history.NormalizeTransactionHistoryUseCase
import com.mtd.domain.usecase.wallet.GetActiveWalletIdUseCase
import com.mtd.domain.usecase.wallet.GetActiveWalletUseCase
import com.mtd.domain.usecase.wallet.ObserveActiveWalletUseCase
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private data class WalletAddressReference(
    val name: String,
    val color: Int
)

private const val HISTORY_CACHE_SCHEMA_VERSION = 2
private const val HISTORY_NETWORK_STALE_MS = 10 * 60 * 1000L

@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    private val networkCatalog: INetworkCatalog,
    private val assetCatalog: IAssetCatalog,
    private val appEventBus: IAppEventBus,
    private val cacheStore: IAppCacheStore,
    private val getTransactionHistoryUseCase: GetTransactionHistoryUseCase,
    private val getTransactionFeeDetailsUseCase: GetTransactionFeeDetailsUseCase,
    private val buildHistoryNetworkOptionsUseCase: BuildHistoryNetworkOptionsUseCase,
    private val buildPendingHistoryTransactionUseCase: BuildPendingHistoryTransactionUseCase,
    private val getWalletAddressBookUseCase: GetWalletAddressBookUseCase,
    private val normalizeTransactionHistoryUseCase: NormalizeTransactionHistoryUseCase,
    private val observeActiveWalletUseCase: ObserveActiveWalletUseCase,
    private val getActiveWalletUseCase: GetActiveWalletUseCase,
    private val getActiveWalletIdUseCase: GetActiveWalletIdUseCase,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    private val _transactions = MutableStateFlow<List<TransactionRecord>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private val _networkOptions = MutableStateFlow<List<HistoryNetworkOption>>(emptyList())
    val networkOptions = _networkOptions.asStateFlow()

    val activeWallet = observeActiveWalletUseCase()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _selectedTransaction = MutableStateFlow<TransactionRecord?>(null)
    val selectedTransaction = _selectedTransaction.asStateFlow()

    private val _transactionFeeDetails = MutableStateFlow<Map<String, TransactionFeeDetails>>(emptyMap())
    val transactionFeeDetails = _transactionFeeDetails.asStateFlow()

    private val _transactionFeeDetailsLoading = MutableStateFlow<Set<String>>(emptySet())
    val transactionFeeDetailsLoading = _transactionFeeDetailsLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private var lastLoadedKey: String? = null
    private var currentNetworkNameStr: String? = null
    private var currentUserAddress: String? = null
    private var walletAddressBook: Map<String, WalletAddressReference> = emptyMap()
    private var assetUsdPrices: Map<String, BigDecimal> = emptyMap()
    private var currentWalletKeys: List<WalletKey> = emptyList()
    private var selectedNetworkOptionId: String? = null
    private var refreshingNetworkIds: Set<String> = emptySet()
    private val lastNetworkRefreshTimes = mutableMapOf<String, Long>()

    init {
        listenToGlobalEvents()
        observeActiveWallet()
    }

    fun refresh(networkNameStr: String?, userAddress: String?) {
        lastLoadedKey = null
        loadHistory(networkNameStr, userAddress, forceRefresh = true)
    }

    fun refreshSelectedNetwork(fallbackNetworkNameStr: String?, fallbackUserAddress: String?) {
        val selected = selectedNetworkSource()
        if (selected == null || selected.isAllNetworks) {
            refresh(null, null)
            return
        }

        refresh(
            networkNameStr = selected.networkName.takeIf { it.isNotBlank() } ?: fallbackNetworkNameStr,
            userAddress = selected.address.takeIf { it.isNotBlank() } ?: fallbackUserAddress
        )
    }

    fun selectNetwork(option: HistoryNetworkOption) {
        if (selectedNetworkOptionId == option.id && lastLoadedKey != null) return
        selectedNetworkOptionId = option.id
        publishNetworkOptions()
        if (option.isAllNetworks) {
            loadHistory(null, null, forceRefresh = false)
        } else {
            loadHistory(option.networkName, option.address, forceRefresh = false)
        }
    }

    fun syncAssetPricesFromHomeAssets(assets: List<AssetItem>) {
        val prices = flattenAssetItems(assets)
            .filter { it.priceUsdRaw > BigDecimal.ZERO }
            .associate { it.symbol.uppercase(Locale.US) to it.priceUsdRaw }

        if (prices != assetUsdPrices) {
            assetUsdPrices = prices
        }
    }

    fun loadHistory(networkNameStr: String?, userAddress: String?, forceRefresh: Boolean = false) {
        val fallbackSource = selectedNetworkSource()
        val explicitNetwork = networkNameStr?.trim().orEmpty().ifBlank { null }
        val explicitAddress = userAddress?.trim().orEmpty().ifBlank { null }
        val normalizedNetwork = explicitNetwork ?: fallbackSource?.takeUnless { it.isAllNetworks }?.networkName
        val normalizedAddress = explicitAddress ?: fallbackSource?.takeUnless { it.isAllNetworks }?.address
        currentNetworkNameStr = normalizedNetwork
        currentUserAddress = normalizedAddress
        selectNetworkFor(normalizedNetwork, normalizedAddress)
        val requestKey = "${normalizedNetwork ?: "all"}|${normalizedAddress ?: "all"}"

        if (!forceRefresh && lastLoadedKey == requestKey && (_transactions.value.isNotEmpty() || _errorMessage.value != null)) {
            return
        }

        lastLoadedKey = requestKey

        if (normalizedNetwork == null || normalizedAddress == null) {
            selectAllNetworks()
            loadAllHistory(forceRefresh)
            return
        }

        launchSafe {
            val refreshingId = optionIdFor(normalizedNetwork, normalizedAddress)
            setNetworkRefreshing(refreshingId, true)
            _isLoading.value = true
            _transactions.value = emptyList()
            _errorMessage.value = null
            _selectedTransaction.value = null

            try {
                refreshWalletAddressBook()

                // Check Cache
                val walletId = getActiveWalletIdUseCase() ?: "unknown"
                val cacheKey = getHistoryCacheKey(walletId, normalizedNetwork, normalizedAddress)
                
                if (!forceRefresh) {
                    val cached = cacheStore.get(cacheKey, Array<TransactionRecord>::class.java)
                    if (cached != null && cached.isNotEmpty()) {
                        val history = normalizeTransactionHistoryUseCase(cached.toList(), normalizedAddress)
                        _transactions.value = history
                        markNetworkUpdated(refreshingId)
                        _isLoading.value = false
                        // Optional: Still fetch in background if cache is old? 
                        // For now, let's just return if cache found.
                        return@launchSafe 
                    }
                }

                val networkName = NetworkName.entries.find {
                    it.name.equals(normalizedNetwork, ignoreCase = true)
                }

                if (networkName == null) {
                    _transactions.value = emptyList()
                    _errorMessage.value = "Network not found"
                    return@launchSafe
                }

                when (val result = getTransactionHistoryUseCase(networkName, normalizedAddress)) {
                    is ResultResponse.Success -> {
                        val history = normalizeTransactionHistoryUseCase(result.data, normalizedAddress)
                        _transactions.value = history
                        markNetworkUpdated(refreshingId)
                        // Save to Cache
                        cacheStore.put(cacheKey, history.toTypedArray())
                    }

                    is ResultResponse.Error -> {
                        _transactions.value = emptyList()
                        _errorMessage.value = result.exception.message ?: "Failed to load transaction history"
                    }
                }
            } finally {
                _isLoading.value = false
                setNetworkRefreshing(refreshingId, false)
            }
        }
    }

    private fun observeActiveWallet() {
        launchSafe(checkNetwork = false) {
            observeActiveWalletUseCase().collect { wallet ->
                if (wallet != null) {
                    rebuildNetworkOptions(wallet.keys)
                    // وقتی کیف پول عوض می‌شود، کلید لود قبلی را ریست می‌کنیم تا لود مجدد اجباری شود
                    lastLoadedKey = null
                    loadHistory(currentNetworkNameStr, currentUserAddress)
                }
            }
        }
    }

    private fun listenToGlobalEvents() {
        launchSafe(checkNetwork = false) {
            appEventBus.events.collect { event ->
                if (event is AppEvent.TransactionHistoryNeedsRefresh && shouldRefreshFor(event)) {
                    applyHistoryRefreshEvent(event)
                }
            }
        }
    }

    private fun shouldRefreshFor(event: AppEvent.TransactionHistoryNeedsRefresh): Boolean {
        val currentNetwork = currentNetworkNameStr
        val currentAddress = currentUserAddress
        if (currentNetwork == null || currentAddress == null) return true

        val networkMatches = event.networkName.isNullOrBlank() ||
            event.networkName.equals(currentNetwork, ignoreCase = true)
        val addressMatches = event.userAddress.isNullOrBlank() ||
            event.userAddress.equals(currentAddress, ignoreCase = true)
        return networkMatches && addressMatches
    }

    private fun applyHistoryRefreshEvent(event: AppEvent.TransactionHistoryNeedsRefresh) {
        event.pendingTransaction?.let(buildPendingHistoryTransactionUseCase::invoke)?.let { pending ->
            _transactions.value = normalizeTransactionHistoryUseCase(listOf(pending) + _transactions.value)
        }

        val networkName = event.networkName?.let { raw ->
            NetworkName.entries.find { it.name.equals(raw, ignoreCase = true) }
        }
        val address = event.userAddress?.trim().orEmpty().ifBlank { null }

        if (networkName == null || address == null) {
            refresh(currentNetworkNameStr, currentUserAddress)
            return
        }

        launchSafe {
            _isLoading.value = true
            try {
                refreshWalletAddressBook()
                val key = getActiveWalletUseCase()
                    ?.keys
                    ?.firstOrNull {
                        it.networkName == networkName &&
                            it.address.equals(address, ignoreCase = true)
                    }
                    ?: WalletKey(
                        networkName = networkName,
                        networkType = NetworkType.OTHER,
                        chainId = null,
                        derivationPath = null,
                        address = address,
                        publicKeyHex = ""
                    )

                val fresh = normalizeTransactionHistoryUseCase(fetchHistoryForWalletKey(key))
                val merged = mergeHistoryForKey(
                    current = _transactions.value,
                    key = key,
                    fresh = fresh
                )
                _transactions.value = merged

                val walletId = getActiveWalletIdUseCase() ?: "unknown"
                val mergedForNetwork = merged.filter { matchesWalletKey(it, key) }
                cacheStore.put(
                    getHistoryCacheKey(walletId, networkName.name, address),
                    mergedForNetwork.toTypedArray()
                )

                if (currentNetworkNameStr == null || currentUserAddress == null) {
                    cacheStore.put(getHistoryCacheKey(walletId, null, null), merged.toTypedArray())
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadAllHistory(forceRefresh: Boolean = false) {
        launchSafe {
            setNetworkRefreshing(HISTORY_ALL_NETWORKS_OPTION_ID, true)
            _isLoading.value = true
            _transactions.value = emptyList()
            _errorMessage.value = null
            _selectedTransaction.value = null

            try {
                val wallet = getActiveWalletUseCase() ?: run {
                    _transactions.value = emptyList()
                    _errorMessage.value = "No active wallet selected"
                    return@launchSafe
                }

                val keys = wallet.keys
                    .distinctBy { key -> key.networkName.name to key.address.lowercase(Locale.US) }
                val networkOptionIds = keys.map { key -> optionId(key.networkName.name, key.address) }
                setNetworksRefreshing(networkOptionIds, true)

                val walletId = wallet.id
                val cacheKey = getHistoryCacheKey(walletId, null, null)

                try {
                    if (!forceRefresh) {
                        val cached = cacheStore.get(cacheKey, Array<TransactionRecord>::class.java)
                        if (cached != null && cached.isNotEmpty()) {
                            val history = normalizeTransactionHistoryUseCase(cached.toList())
                            _transactions.value = history
                            markNetworksUpdated(networkOptionIds + HISTORY_ALL_NETWORKS_OPTION_ID)
                            return@launchSafe
                        }
                    }

                    refreshWalletAddressBook()

                    val aggregatedResults = mutableListOf<TransactionRecord>()
                    val resultMutex = Mutex()

                    supervisorScope {
                        keys.map { key ->
                            async(Dispatchers.IO) {
                                val networkResults = fetchHistoryForWalletKey(key)
                                val networkOptionId = optionId(key.networkName.name, key.address)
                                resultMutex.withLock {
                                    markNetworkUpdated(networkOptionId)
                                    if (networkResults.isNotEmpty()) {
                                        aggregatedResults.addAll(networkResults)
                                        _transactions.value = normalizeTransactionHistoryUseCase(aggregatedResults.toList())
                                    }
                                }
                            }
                        }.joinAll()
                    }

                    val finalHistory = normalizeTransactionHistoryUseCase(aggregatedResults)
                    _transactions.value = finalHistory
                    markNetworkUpdated(HISTORY_ALL_NETWORKS_OPTION_ID)

                    // Save to Cache
                    cacheStore.put(cacheKey, finalHistory.toTypedArray())
                } finally {
                    setNetworksRefreshing(networkOptionIds, false)
                }
            } finally {
                _isLoading.value = false
                setNetworkRefreshing(HISTORY_ALL_NETWORKS_OPTION_ID, false)
            }
        }
    }

    private fun getHistoryCacheKey(walletId: String, networkName: String?, address: String?): String {
        return "history_v${HISTORY_CACHE_SCHEMA_VERSION}_${walletId}_${networkName ?: "all"}_${address ?: "all"}"
    }

    private fun rebuildNetworkOptions(keys: List<WalletKey>) {
        currentWalletKeys = keys
        publishNetworkOptions()
        if (selectedNetworkOptionId == null || _networkOptions.value.none { it.id == selectedNetworkOptionId }) {
            selectedNetworkOptionId = HISTORY_ALL_NETWORKS_OPTION_ID
        }
        publishNetworkOptions()
    }

    private fun selectedNetworkSource(): HistoryNetworkOption? {
        val selectedId = selectedNetworkOptionId
        return _networkOptions.value.firstOrNull { it.id == selectedId }
            ?: _networkOptions.value.firstOrNull()
    }

    private fun selectNetworkFor(networkName: String?, address: String?) {
        val id = optionIdFor(networkName, address) ?: return
        if (_networkOptions.value.any { it.id == id }) {
            selectedNetworkOptionId = id
            publishNetworkOptions()
        }
    }

    private fun selectAllNetworks() {
        selectedNetworkOptionId = HISTORY_ALL_NETWORKS_OPTION_ID
        publishNetworkOptions()
    }

    private fun optionIdFor(networkName: String?, address: String?): String? {
        if (networkName.isNullOrBlank() || address.isNullOrBlank()) return null
        return optionId(networkName, address)
    }

    private fun optionId(networkName: String, address: String): String {
        return buildHistoryNetworkOptionsUseCase.optionId(networkName, address)
    }

    private fun setNetworkRefreshing(optionId: String?, refreshing: Boolean) {
        if (optionId == null) return
        refreshingNetworkIds = if (refreshing) {
            refreshingNetworkIds + optionId
        } else {
            refreshingNetworkIds - optionId
        }
        publishNetworkOptions()
    }

    private fun setNetworksRefreshing(optionIds: List<String>, refreshing: Boolean) {
        if (optionIds.isEmpty()) return
        refreshingNetworkIds = if (refreshing) {
            refreshingNetworkIds + optionIds
        } else {
            refreshingNetworkIds - optionIds.toSet()
        }
        publishNetworkOptions()
    }

    private fun markNetworkUpdated(optionId: String?) {
        if (optionId == null) return
        lastNetworkRefreshTimes[optionId] = System.currentTimeMillis()
        publishNetworkOptions()
    }

    private fun markNetworksUpdated(optionIds: List<String>) {
        val now = System.currentTimeMillis()
        optionIds.forEach { optionId ->
            lastNetworkRefreshTimes[optionId] = now
        }
        publishNetworkOptions()
    }

    private fun publishNetworkOptions() {
        _networkOptions.value = buildHistoryNetworkOptionsUseCase(
            keys = currentWalletKeys,
            selectedId = selectedNetworkOptionId,
            refreshingIds = refreshingNetworkIds,
            lastRefreshTimes = lastNetworkRefreshTimes,
            now = System.currentTimeMillis(),
            staleAfterMs = HISTORY_NETWORK_STALE_MS
        )
    }

    private fun flattenAssetItems(assets: List<AssetItem>): List<AssetItem> {
        return assets.flatMap { asset ->
            if (asset.groupAssets.isEmpty()) {
                listOf(asset)
            } else {
                listOf(asset) + asset.groupAssets
            }
        }
    }

    private suspend fun fetchHistoryForWalletKey(key: WalletKey): List<TransactionRecord> {
        return try {
            when (val result = getTransactionHistoryUseCase(key.networkName, key.address)) {
                is ResultResponse.Success -> normalizeTransactionHistoryUseCase(result.data, key.address)
                is ResultResponse.Error -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mergeHistoryForKey(
        current: List<TransactionRecord>,
        key: WalletKey,
        fresh: List<TransactionRecord>
    ): List<TransactionRecord> {
        val freshHashes = fresh.map { it.hash.lowercase(Locale.US) }.toSet()
        val retained = current.filter { record ->
            if (!matchesWalletKey(record, key)) return@filter true
            record.status == TransactionStatus.PENDING &&
                record.hash.lowercase(Locale.US) !in freshHashes
        }
        return normalizeTransactionHistoryUseCase(retained + fresh)
    }

    private fun matchesWalletKey(record: TransactionRecord, key: WalletKey): Boolean {
        if (record.networkName != key.networkName) return false
        val address = key.address.lowercase(Locale.US)
        return record.fromAddress?.lowercase(Locale.US) == address ||
            record.toAddress?.lowercase(Locale.US) == address
    }

    private suspend fun refreshWalletAddressBook() {
        walletAddressBook = when (val result = getWalletAddressBookUseCase()) {
            is ResultResponse.Success -> {
                result.data
                    .associate { entry ->
                        entry.address.lowercase(Locale.US) to WalletAddressReference(
                            name = entry.walletName,
                            color = entry.walletColor
                        )
                    }
            }

            is ResultResponse.Error -> walletAddressBook
        }
    }

    fun selectTransaction(transaction: TransactionRecord?) {
        _selectedTransaction.value = transaction
        transaction?.let(::loadTransactionDetails)
    }

    fun loadTransactionDetails(transaction: TransactionRecord) {
        if (transaction !is TronTransaction) return
        val networkName = transaction.networkName ?: return
        val key = transactionDetailKey(transaction) ?: return
        if (_transactionFeeDetails.value.containsKey(key) ||
            _transactionFeeDetailsLoading.value.contains(key)
        ) {
            return
        }

        _transactionFeeDetailsLoading.update { it + key }
        launchSafe {
            try {
                when (val result = getTransactionFeeDetailsUseCase(networkName, transaction.hash)) {
                    is ResultResponse.Success -> {
                        _transactionFeeDetails.update { it + (key to result.data) }
                        applyTransactionFeeDetails(key, result.data)
                    }

                    is ResultResponse.Error -> Unit
                }
            } finally {
                _transactionFeeDetailsLoading.update { it - key }
            }
        }
    }

    fun getDateHeaders(timestampSeconds: Long): String {
        return getDateHeader(timestampSeconds)
    }

    fun getHistoryDateHeader(transaction: TransactionRecord): String {
        return if (transaction.status == TransactionStatus.PENDING) {
            "در انتظار"
        } else {
            getDateHeader(transaction.timestamp)
        }
    }

    fun formatTransactionTime(timestampSeconds: Long): String {
        if (timestampSeconds <= 0L) return "--:--"
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampSeconds * 1000))
    }

    fun formatTimelineSubmitted(transaction: TransactionRecord): String {
        val value = transaction.submittedAt ?: transaction.timestamp
        if (value <= 0L) return "نامشخص"
        return "${getDateHeader(value)}, ${formatTransactionTime(value)}"
    }

    fun formatTimelineCompleted(transaction: TransactionRecord): String? {
        if (transaction.status != TransactionStatus.CONFIRMED || transaction.timestamp <= 0L) return null
        return "${getDateHeader(transaction.timestamp)}, ${formatTransactionTime(transaction.timestamp)}"
    }

    fun formatPendingDuration(transaction: TransactionRecord): String {
        val seconds = transaction.pendingDurationSeconds
        if (seconds == null || seconds <= 0L) {
            return if (transaction.status == TransactionStatus.PENDING) "در حال انجام ..." else "-"
        }

        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    fun formatTransactionAmount(transaction: TransactionRecord): String {
        val amountDecimal = rawAmountToDecimal(transaction)
        val symbol = transactionSymbol(transaction)
        val formatted = BalanceFormatter.formatBalance(
            balance = amountDecimal,
            decimals = transactionDecimals(transaction),
            usePersianSeparator = true
        )
        val signed = if (transaction.isOutgoing) formatted else formatted
        return if (symbol.isBlank()) signed else "$signed $symbol"
    }

    fun formatListAmount(transaction: TransactionRecord): String {
        val amountDecimal = rawAmountToDecimal(transaction)
        val symbol = transactionSymbol(transaction)
        val formatted = BalanceFormatter.formatBalance(
            balance = amountDecimal,
            decimals = transactionDecimals(transaction),
            usePersianSeparator = true
        )
        val display = if (transaction.isOutgoing) formatted else "+$formatted"
        return if (symbol.isBlank()) display else "$display $symbol"
    }

    fun formatTransactionFiat(transaction: TransactionRecord): String? {
        val value = transaction.fiatValue ?: return null
        return BalanceFormatter.formatUsdValue(BigDecimal.valueOf(value), false)
    }

    fun formatTransactionFiatDetail(transaction: TransactionRecord): String? {
        val amount = transactionFiatValue(transaction) ?: return null
        val formatted = BalanceFormatter.formatUsdValue(amount, false)
        return formatted
    }

    private fun transactionFiatValue(transaction: TransactionRecord): BigDecimal? {
        transaction.fiatValue?.let { return BigDecimal.valueOf(it).abs() }
        val symbol = transactionSymbol(transaction).uppercase(Locale.US)
        val price = assetUsdPrices[symbol]?.takeIf { it > BigDecimal.ZERO } ?: return null
        return rawAmountToDecimal(transaction).abs().multiply(price)
    }

    fun formatTransactionFee(transaction: TransactionRecord): String {
        val feeValue = transactionDetailFor(transaction)?.fee ?: transaction.fee
        val feeDecimal = rawFeeToDecimal(transaction, feeValue)
        val symbol = transactionSymbol(transaction, forFee = true)

        return if (feeValue == BigInteger.ZERO) {
            "0 $symbol".trim()
        } else {
            val formatted = BalanceFormatter.formatBalance(
                balance = feeDecimal,
                decimals = networkDecimals(transaction),
                usePersianSeparator = true
            )
            "$formatted $symbol".trim()
        }
    }

    fun isTransactionFeeDetailsLoading(transaction: TransactionRecord): Boolean {
        val key = transactionDetailKey(transaction) ?: return false
        return _transactionFeeDetailsLoading.value.contains(key)
    }

    fun formatTronEnergyUsed(transaction: TransactionRecord): String? {
        val tron = transaction as? TronTransaction ?: return null
        val value = transactionDetailFor(tron)?.energyUsed ?: tron.energyUsed
        return value?.toString()
    }

    fun formatTronBandwidthUsed(transaction: TransactionRecord): String? {
        val tron = transaction as? TronTransaction ?: return null
        val value = transactionDetailFor(tron)?.bandwidthUsed ?: tron.bandwidthUsed
        return value?.toString()
    }

    fun formatTronEnergyFee(transaction: TransactionRecord): String? {
        val fee = transactionDetailFor(transaction)?.energyFee ?: return null
        return formatNativeFeeAmount(transaction, fee)
    }

    fun formatTronNetworkFee(transaction: TransactionRecord): String? {
        val fee = transactionDetailFor(transaction)?.networkFee ?: return null
        return formatNativeFeeAmount(transaction, fee)
    }

    fun getTransactionTypeLabel(transaction: TransactionRecord): String {
        return if (transaction.isOutgoing) "Withdraw" else "Deposit"
    }

    fun getTransactionStatusLabel(status: TransactionStatus): String {
        return when (status) {
            TransactionStatus.PENDING -> "Pending"
            TransactionStatus.CONFIRMED -> "Confirmed"
            TransactionStatus.FAILED -> "Failed"
        }
    }

    fun getNetworkDisplayName(transaction: TransactionRecord): String {
        return networkCatalog.getNetworkInfoByName(transaction.networkName ?: return "Network")
            ?.faName
            ?.takeIf { it.isNotBlank() }
            ?: transaction.networkName?.name
            ?: "Network"
    }

    fun getHistoryPrimaryLabel(transaction: TransactionRecord): String {
        return when {
            transaction.status == TransactionStatus.PENDING && transaction.isOutgoing ->     "در حال ارسال به"
            transaction.status == TransactionStatus.PENDING ->  "در حال دریافت از"
            transaction.isOutgoing ->  "ارسال به"
            else ->  "دریافت از"
        }
    }

    fun getHistoryCounterpartyLabel(transaction: TransactionRecord): String {
        val address = getCounterpartyAddress(transaction) ?: return getNetworkDisplayName(transaction)
        return walletAddressBook[address.lowercase(Locale.US)]?.name ?: shortenAddress(address)
    }

    fun isCounterpartyInternal(transaction: TransactionRecord): Boolean {
        val address = getCounterpartyAddress(transaction) ?: return false
        return walletAddressBook.containsKey(address.lowercase(Locale.US))
    }

    fun getCounterpartyAccentColor(transaction: TransactionRecord): Int? {
        val address = getCounterpartyAddress(transaction) ?: return null
        return walletAddressBook[address.lowercase(Locale.US)]?.color
    }

    fun getHistoryAssetTitle(transaction: TransactionRecord): String {
        val network = networkCatalog.getNetworkInfoByName(transaction.networkName ?: return "Asset")
        val networkId = network?.id.orEmpty()
        val symbol = transactionSymbol(transaction)
        
        val contractAddr = when (transaction) {
            is EvmTransaction -> transaction.contractAddress ?: transaction.tokenTransferDetails?.contractAddress
            is TronTransaction -> transaction.contractAddress ?: transaction.tokenTransferDetails?.contractAddress
            else -> null
        }
        
        return resolveAssetName(networkId, contractAddr, symbol)
    }

    fun getHistoryAssetIconUrl(transaction: TransactionRecord): String? {
        val network = networkCatalog.getNetworkInfoByName(transaction.networkName ?: return null)
        val networkId = network?.id.orEmpty()
        val symbol = transactionSymbol(transaction)

        val contractAddr = when (transaction) {
            is EvmTransaction -> transaction.contractAddress ?: transaction.tokenTransferDetails?.contractAddress
            is TronTransaction -> transaction.contractAddress ?: transaction.tokenTransferDetails?.contractAddress
            else -> null
        }

        return resolveAssetConfig(networkId, contractAddr, symbol)?.symbol
    }

    fun buildExplorerUrl(transaction: TransactionRecord): String? {
        val network = networkCatalog.getNetworkInfoByName(transaction.networkName ?: return null) ?: return null
        val base = network.explorers.firstOrNull()?.trimEnd('/') ?: return null
        return when {
            "blockscout" in base.lowercase() -> "$base/tx/${transaction.hash}"
            "tronscan" in base.lowercase() -> "$base/#/transaction/${transaction.hash}"
            "mempool.space" in base.lowercase() -> base.removeSuffix("/api") + "/tx/${transaction.hash}"
            "blockchair.com" in base.lowercase() -> "$base/transaction/${transaction.hash}"
            "xrpscan" in base.lowercase() -> "$base/tx/${transaction.hash}"
            "solscan" in base.lowercase() -> "$base/tx/${transaction.hash}"
            "tonscan" in base.lowercase() -> "$base/tx/${transaction.hash}"
            "basescan" in base.lowercase() || "etherscan" in base.lowercase() -> "$base/tx/${transaction.hash}"
            else -> null
        }
    }

    private fun rawAmountToDecimal(transaction: TransactionRecord): BigDecimal {
        return BigDecimal(transaction.amount).movePointLeft(transactionDecimals(transaction))
    }

    private fun transactionDetailFor(transaction: TransactionRecord): TransactionFeeDetails? {
        val key = transactionDetailKey(transaction) ?: return null
        return _transactionFeeDetails.value[key]
    }

    private fun transactionDetailKey(transaction: TransactionRecord): String? {
        val networkName = transaction.networkName?.name ?: return null
        val hash = transaction.hash.takeIf { it.isNotBlank() } ?: return null
        return "${networkName.lowercase(Locale.US)}:${hash.lowercase(Locale.US)}"
    }

    private fun applyTransactionFeeDetails(
        transactionKey: String,
        details: TransactionFeeDetails
    ) {
        _transactions.update { transactions ->
            transactions.map { transaction ->
                if (transactionDetailKey(transaction) == transactionKey) {
                    transaction.withFeeDetails(details)
                } else {
                    transaction
                }
            }
        }

        _selectedTransaction.update { transaction ->
            if (transaction != null && transactionDetailKey(transaction) == transactionKey) {
                transaction.withFeeDetails(details)
            } else {
                transaction
            }
        }
    }

    private fun TransactionRecord.withFeeDetails(details: TransactionFeeDetails): TransactionRecord {
        return when (this) {
            is TronTransaction -> copy(
                fee = details.fee,
                energyUsed = details.energyUsed ?: energyUsed,
                bandwidthUsed = details.bandwidthUsed ?: bandwidthUsed
            )

            else -> this
        }
    }

    private fun rawFeeToDecimal(transaction: TransactionRecord): BigDecimal {
        return rawFeeToDecimal(transaction, transaction.fee)
    }

    private fun rawFeeToDecimal(transaction: TransactionRecord, fee: BigInteger): BigDecimal {
        return BigDecimal(fee).movePointLeft(networkDecimals(transaction))
    }

    private fun formatNativeFeeAmount(transaction: TransactionRecord, fee: BigInteger): String {
        val formatted = BalanceFormatter.formatBalance(
            balance = rawFeeToDecimal(transaction, fee),
            decimals = networkDecimals(transaction),
            usePersianSeparator = true
        )
        val symbol = transactionSymbol(transaction, forFee = true)
        return "$formatted $symbol".trim()
    }

    private fun transactionDecimals(transaction: TransactionRecord): Int {
        return when (transaction) {
            is EvmTransaction -> transaction.tokenTransferDetails?.tokenDecimals ?: networkDecimals(transaction)
            is TronTransaction -> transaction.tokenTransferDetails?.tokenDecimals ?: networkDecimals(transaction)
            is BitcoinTransaction -> networkDecimals(transaction)
        }
    }

    private fun transactionSymbol(transaction: TransactionRecord, forFee: Boolean = false): String {
        return when (transaction) {
            is EvmTransaction -> {
                if (!forFee && transaction.tokenTransferDetails != null) {
                    transaction.tokenTransferDetails!!.tokenSymbol
                } else {
                    networkCatalog.getNetworkInfoByName(transaction.networkName ?: return "")?.currencySymbol.orEmpty()
                }
            }
            is TronTransaction -> {
                if (!forFee && transaction.tokenTransferDetails != null) {
                    transaction.tokenTransferDetails!!.tokenSymbol
                } else {
                    networkCatalog.getNetworkInfoByName(transaction.networkName ?: return "")?.currencySymbol.orEmpty()
                }
            }
            is BitcoinTransaction -> {
                networkCatalog.getNetworkInfoByName(transaction.networkName ?: return "")?.currencySymbol.orEmpty()
            }
        }
    }

    private fun networkDecimals(transaction: TransactionRecord): Int {
        return networkCatalog.getNetworkInfoByName(transaction.networkName ?: return 0)?.decimals ?: 0
    }
     fun networkId(transaction: TransactionRecord): String {
        return networkCatalog.getNetworkInfoByName(transaction.networkName ?: return "")?.id ?: ""
    }

    private fun getCounterpartyAddress(transaction: TransactionRecord): String? {
        return if (transaction.isOutgoing) transaction.toAddress else transaction.fromAddress
    }

    private fun shortenAddress(address: String): String {
        return AddressUtils.shortenAddress(address)
    }

    private fun resolveAssetName(networkId: String, contractAddress: String?, fallbackSymbol: String): String {
        return resolveAssetConfig(networkId, contractAddress, fallbackSymbol)?.faName
            ?.takeIf { it.isNotBlank() }
            ?: resolveAssetConfig(networkId, contractAddress, fallbackSymbol)?.name
            ?: fallbackSymbol
    }

    private fun resolveAssetIconUrl(networkId: String, contractAddress: String?, fallbackSymbol: String): String? {
        return resolveAssetConfig(networkId, contractAddress, fallbackSymbol)?.symbol
    }

    private fun resolveAssetConfig(networkId: String, contractAddress: String?, fallbackSymbol: String) =
        assetCatalog.getAssetConfigsForNetwork(networkId).find { asset ->
            when {
                !contractAddress.isNullOrBlank() -> asset.contractAddress.equals(contractAddress, ignoreCase = true)
                else -> asset.contractAddress == null && asset.symbol.equals(fallbackSymbol, ignoreCase = true)
            }
        }


}
