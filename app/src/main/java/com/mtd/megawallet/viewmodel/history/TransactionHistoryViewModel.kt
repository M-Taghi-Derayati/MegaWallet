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
import com.mtd.domain.model.HistoryAddress
import com.mtd.domain.model.HistoryNetworkOption
import com.mtd.domain.model.HistoryPage
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionFeeDetails
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import com.mtd.domain.model.TronTransaction
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.core.WalletKey
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import com.mtd.domain.usecase.history.BuildHistoryNetworkOptionsUseCase
import com.mtd.domain.usecase.history.BuildPendingHistoryTransactionUseCase
import com.mtd.domain.usecase.history.GetTransactionFeeDetailsUseCase
import com.mtd.domain.usecase.history.GetTransactionHistoryUseCase
import com.mtd.domain.usecase.history.GetUnifiedHistoryPageUseCase
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
import timber.log.Timber
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

private const val HISTORY_CACHE_SCHEMA_VERSION = 3
private const val HISTORY_NETWORK_STALE_MS = 10 * 60 * 1000L
private const val HISTORY_PAGE_LIMIT = 20
private const val MAX_HISTORY_PAIRS = 25

@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    private val networkCatalog: INetworkCatalog,
    private val assetCatalog: IAssetCatalog,
    private val appEventBus: IAppEventBus,
    private val cacheStore: IAppCacheStore,
    private val getTransactionHistoryUseCase: GetTransactionHistoryUseCase,
    private val getUnifiedHistoryPageUseCase: GetUnifiedHistoryPageUseCase,
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

    // Unified-history cursor pagination state (§1.7). Only meaningful while [unifiedActive].
    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    /** True when the current page's response carried `staleSources` ⇒ list may be incomplete. */
    private val _showStaleWarning = MutableStateFlow(false)
    val showStaleWarning = _showStaleWarning.asStateFlow()

    private var nextCursor: String? = null
    private var currentPairs: List<HistoryAddress> = emptyList()
    private var unifiedActive: Boolean = false

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

        val isAll = normalizedNetwork == null || normalizedAddress == null
        if (isAll) selectAllNetworks()

        launchSafe {
            // The network filter changed ⇒ any previous cursor is for a different request set.
            nextCursor = null
            _hasMore.value = false
            _isLoadingMore.value = false
            _showStaleWarning.value = false
            _isLoading.value = true
            _transactions.value = emptyList()
            _errorMessage.value = null
            _selectedTransaction.value = null

            // Build the (networkId,address) pair-set + matching refreshing markers.
            val wallet = if (isAll) getActiveWalletUseCase() else null
            val allKeys = wallet?.keys
                ?.distinctBy { key -> key.networkName.name to key.address.lowercase(Locale.US) }
                .orEmpty()
            val pairs = if (isAll) {
                buildHistoryPairs(allKeys)
            } else {
                buildSinglePair(normalizedNetwork, normalizedAddress)
            }
            currentPairs = pairs

            val refreshingIds = if (isAll) {
                allKeys.map { optionId(it.networkName.name, it.address) } + HISTORY_ALL_NETWORKS_OPTION_ID
            } else {
                listOfNotNull(optionIdFor(normalizedNetwork, normalizedAddress))
            }
            setNetworksRefreshing(refreshingIds, true)

            try {
                refreshWalletAddressBook()

                val walletId = getActiveWalletIdUseCase() ?: "unknown"
                val cacheKey = getHistoryCacheKey(walletId, normalizedNetwork, normalizedAddress)

                // Page-1 cache only (cursor pages are never cached).
                if (!forceRefresh) {
                    val cached = cacheStore.get(cacheKey, Array<TransactionRecord>::class.java)
                    if (cached != null && cached.isNotEmpty()) {
                        _transactions.value = normalizeTransactionHistoryUseCase(cached.toList(), normalizedAddress)
                        markNetworksUpdated(refreshingIds)
                        return@launchSafe
                    }
                }

                // Try the unified proxy page first (§1.7); fall back on PROXY-unsupported (DIRECT mode).
                val unified = if (pairs.isNotEmpty()) {
                    getUnifiedHistoryPageUseCase(pairs, cursor = null, limit = HISTORY_PAGE_LIMIT)
                } else {
                    null
                }

                when (unified) {
                    is ResultResponse.Success -> {
                        unifiedActive = true
                        applyUnifiedPage(unified.data, normalizedAddress, append = false)
                        markNetworksUpdated(refreshingIds)
                        cacheStore.put(cacheKey, _transactions.value.toTypedArray())
                    }

                    is ResultResponse.Error -> {
                        unifiedActive = false
                        val apiError = (unified.exception as? ApiException)?.apiError
                        if (apiError == ApiError.UnsupportedOperation) {
                            loadLegacy(isAll, normalizedNetwork, normalizedAddress, cacheKey, refreshingIds)
                        } else {
                            _transactions.value = emptyList()
                            _errorMessage.value = (unified.exception as? ApiException)?.reasonFa
                                ?: unified.exception.message
                                ?: "Failed to load transaction history"
                        }
                    }

                    null -> {
                        // No resolvable proxy pairs — use per-network aggregation.
                        unifiedActive = false
                        loadLegacy(isAll, normalizedNetwork, normalizedAddress, cacheKey, refreshingIds)
                    }
                }
            } finally {
                _isLoading.value = false
                setNetworksRefreshing(refreshingIds, false)
            }
        }
    }

    /** Loads the next cursor page (§1.7) for the active unified request. No-op in DIRECT/legacy mode. */
    fun loadMore() {
        if (!unifiedActive || !_hasMore.value || _isLoadingMore.value) return
        val cursor = nextCursor ?: return
        val pairs = currentPairs
        if (pairs.isEmpty()) return

        launchSafe {
            _isLoadingMore.value = true
            try {
                when (val result = getUnifiedHistoryPageUseCase(pairs, cursor = cursor, limit = HISTORY_PAGE_LIMIT)) {
                    is ResultResponse.Success -> applyUnifiedPage(result.data, currentUserAddress, append = true)
                    is ResultResponse.Error -> {
                        // Stop paginating; keep what we have and hint that the list may be incomplete.
                        _hasMore.value = false
                        _showStaleWarning.value = true
                    }
                }
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private fun applyUnifiedPage(page: HistoryPage, address: String?, append: Boolean) {
        nextCursor = page.nextCursor
        _hasMore.value = page.hasMore
        if (!page.staleSources.isNullOrEmpty()) {
            _showStaleWarning.value = true
            Timber.w("Unified history stale sources: ${page.staleSources}")
        }
        // TASK-10 — on append, merge only the new page into the already-normalized list instead of
        // re-normalizing the whole accumulation each page (was O(pages²) with per-item catalog lookups).
        _transactions.value = if (append) {
            normalizeTransactionHistoryUseCase.merge(_transactions.value, page.items, address)
        } else {
            normalizeTransactionHistoryUseCase(page.items, address)
        }
    }

    private fun buildSinglePair(networkName: String?, address: String?): List<HistoryAddress> {
        if (networkName.isNullOrBlank() || address.isNullOrBlank()) return emptyList()
        val resolved = NetworkName.entries.find { it.name.equals(networkName, ignoreCase = true) } ?: return emptyList()
        val networkId = networkCatalog.getNetworkInfoByName(resolved)?.id ?: return emptyList()
        return listOf(HistoryAddress(networkId, address))
    }

    private fun buildHistoryPairs(keys: List<WalletKey>): List<HistoryAddress> {
        val pairs = keys.mapNotNull { key ->
            val networkId = networkCatalog.getNetworkInfoByName(key.networkName)?.id ?: return@mapNotNull null
            HistoryAddress(networkId, key.address)
        }
        if (pairs.size > MAX_HISTORY_PAIRS) {
            Timber.w(
                "History request capped at $MAX_HISTORY_PAIRS pairs; dropping ${pairs.size - MAX_HISTORY_PAIRS} of ${pairs.size}"
            )
            return pairs.take(MAX_HISTORY_PAIRS)
        }
        return pairs
    }

    /** DIRECT-mode / unsupported-proxy fallback: per-network RPC aggregation (KAN-11 guardrail). */
    private suspend fun loadLegacy(
        isAll: Boolean,
        normalizedNetwork: String?,
        normalizedAddress: String?,
        cacheKey: String,
        refreshingIds: List<String>
    ) {
        if (isAll) {
            val wallet = getActiveWalletUseCase() ?: run {
                _transactions.value = emptyList()
                _errorMessage.value = "No active wallet selected"
                return
            }
            val keys = wallet.keys.distinctBy { key -> key.networkName.name to key.address.lowercase(Locale.US) }
            val aggregatedResults = mutableListOf<TransactionRecord>()
            val resultMutex = Mutex()

            supervisorScope {
                keys.map { key ->
                    async(Dispatchers.IO) {
                        val networkResults = fetchHistoryForWalletKey(key)
                        resultMutex.withLock {
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
            markNetworksUpdated(refreshingIds)
            cacheStore.put(cacheKey, finalHistory.toTypedArray())
        } else {
            val networkName = NetworkName.entries.find { it.name.equals(normalizedNetwork, ignoreCase = true) }
            if (networkName == null || normalizedAddress == null) {
                _transactions.value = emptyList()
                _errorMessage.value = "Network not found"
                return
            }
            when (val result = getTransactionHistoryUseCase(networkName, normalizedAddress)) {
                is ResultResponse.Success -> {
                    val history = normalizeTransactionHistoryUseCase(result.data, normalizedAddress)
                    _transactions.value = history
                    markNetworksUpdated(refreshingIds)
                    cacheStore.put(cacheKey, history.toTypedArray())
                }

                is ResultResponse.Error -> {
                    _transactions.value = emptyList()
                    _errorMessage.value = result.exception.message ?: "Failed to load transaction history"
                }
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
        val feeDecimal = rawFeeToDecimal(transaction, feeValue?: BigInteger.ZERO)
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
