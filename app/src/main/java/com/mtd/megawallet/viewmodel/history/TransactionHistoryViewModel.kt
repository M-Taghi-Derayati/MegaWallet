package com.mtd.megawallet.viewmodel.history

import androidx.lifecycle.viewModelScope
import com.mtd.core.manager.ErrorManager
import com.mtd.core.manager.ErrorSeverity
import com.mtd.data.formatter.TransactionDisplayFormatter
import com.mtd.data.formatter.WalletAddressReference
import com.mtd.domain.interfaceRepository.IAppCacheStore
import com.mtd.domain.interfaceRepository.IAppEventBus
import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.model.AppEvent
import com.mtd.domain.model.AssetItem
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
import com.mtd.domain.model.error.ErrorSurface
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.math.BigDecimal
import java.util.Locale
import javax.inject.Inject

private const val HISTORY_CACHE_SCHEMA_VERSION = 3
private const val HISTORY_NETWORK_STALE_MS = 10 * 60 * 1000L
private const val HISTORY_PAGE_LIMIT = 20
private const val MAX_HISTORY_PAIRS = 25

@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    private val networkCatalog: INetworkCatalog,
    private val assetCatalog: IAssetCatalog,
    private val displayFormatter: TransactionDisplayFormatter,
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

    // Item 2 — locally-created optimistic pendings from a just-sent tx. Kept SEPARATE from the loaded
    // list so they (a) show the moment History opens, (b) survive the lazy-history reset + every reload,
    // and (c) disappear automatically once the backend returns the same hash or they age out. Never
    // cached or paginated — only [_transactions] is.
    private val _localPending = MutableStateFlow<List<TransactionRecord>>(emptyList())
    private val pendingLocalTtlSeconds = 30L * 60 // 30 min safety cap for an un-indexed pending

    val transactions: StateFlow<List<TransactionRecord>> =
        combine(_transactions, _localPending) { loaded, pending -> mergeLocalPending(loaded, pending) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    // TASK item 1 & 6 — history is loaded lazily (only while the screen is on-screen) and always for
    // the currently-active wallet. [isScreenVisible] gates the fetch; [observedWalletId] detects a
    // wallet switch so we invalidate the old wallet's data instead of showing it under the new wallet.
    private var isScreenVisible = false
    private var observedWalletId: String? = null

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
                            // The empty-state text on the list is the surface here; a snackbar on
                            // top of a full-screen error message would just be noise.
                            _transactions.value = emptyList()
                            _errorMessage.value = userMessageFor(
                                unified.exception,
                                "دریافت تاریخچه تراکنش‌ها ناموفق بود"
                            )
                            reportError(
                                throwable = unified.exception,
                                userAction = "loadUnifiedHistory",
                                surface = ErrorSurface.SILENT,
                                severity = ErrorSeverity.MEDIUM
                            )
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
                        // The stale banner is the user-visible signal (ErrorSurface.SILENT).
                        _hasMore.value = false
                        _showStaleWarning.value = true
                        reportError(
                            throwable = result.exception,
                            userAction = "loadMoreHistory",
                            surface = ErrorSurface.SILENT,
                            severity = ErrorSeverity.LOW
                        )
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
                    _errorMessage.value = userMessageFor(
                        result.exception,
                        "دریافت تاریخچه تراکنش‌ها ناموفق بود"
                    )
                    reportError(
                        throwable = result.exception,
                        userAction = "loadLegacyHistory",
                        surface = ErrorSurface.SILENT,
                        severity = ErrorSeverity.MEDIUM
                    )
                }
            }
        }
    }

    private fun observeActiveWallet() {
        launchSafe(checkNetwork = false) {
            observeActiveWalletUseCase().collect { wallet ->
                if (wallet != null) {
                    val walletChanged = wallet.id != observedWalletId
                    observedWalletId = wallet.id
                    rebuildNetworkOptions(wallet.keys)

                    if (walletChanged) {
                        // A switch: drop the previous wallet's data + filter and reset the load key so
                        // the new wallet's history is fetched fresh. We only fetch here if the history
                        // screen is actually on-screen — otherwise the fetch is deferred to onScreenShown
                        // (item 1: no history calls before the screen is opened; item 6: never show the
                        // old wallet's data under the new wallet).
                        resetHistoryStateForWalletChange()
                        if (isScreenVisible) {
                            loadHistory(currentNetworkNameStr, currentUserAddress, forceRefresh = true)
                        }
                    }
                }
            }
        }
    }

    /**
     * Called when the history screen becomes visible (History tab selected). Loads the active wallet's
     * history on demand — this is the ONLY entry point that triggers the initial fetch, so history
     * services are never called until the user opens the screen (item 1).
     */
    fun onScreenShown() {
        isScreenVisible = true
        loadHistory(currentNetworkNameStr, currentUserAddress)
    }

    /** Called when the history screen is no longer visible, so a later wallet switch defers its fetch. */
    fun onScreenHidden() {
        isScreenVisible = false
    }

    private fun resetHistoryStateForWalletChange() {
        lastLoadedKey = null
        currentNetworkNameStr = null
        currentUserAddress = null
        nextCursor = null
        currentPairs = emptyList()
        unifiedActive = false
        _transactions.value = emptyList()
        _errorMessage.value = null
        _selectedTransaction.value = null
        _hasMore.value = false
        _showStaleWarning.value = false
        selectAllNetworks()
    }

    private fun listenToGlobalEvents() {
        launchSafe(checkNetwork = false) {
            appEventBus.events.collect { event ->
                if (event is AppEvent.TransactionHistoryNeedsRefresh && shouldRefreshFor(event)) {
                    // Item 2 — capture the optimistic pending regardless of screen visibility (it's local,
                    // no network) so a tx sent from the Send screen still appears the moment History opens
                    // and survives the reload. Only the NETWORK refresh below is gated by visibility.
                    event.pendingTransaction
                        ?.let(buildPendingHistoryTransactionUseCase::invoke)
                        ?.let(::addLocalPending)
                    if (isScreenVisible) {
                        applyHistoryRefreshEvent(event)
                    } else {
                        // Item 1 — don't hit history services while the screen is hidden. Invalidate so
                        // the next onScreenShown fetches fresh; the signal (socket/FCM) isn't lost, just
                        // deferred until the user actually looks at the history.
                        lastLoadedKey = null
                    }
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

    /**
     * Records an optimistic pending (from a just-sent tx) into the durable local set, pruning any that
     * expired or that the backend has already returned. Not gated by screen visibility (item 2).
     */
    private fun addLocalPending(pending: TransactionRecord) {
        val nowSec = System.currentTimeMillis() / 1000
        val loadedHashes = _transactions.value.mapTo(HashSet()) { it.hash.lowercase(Locale.US) }
        _localPending.value = (_localPending.value + pending)
            .distinctBy { it.hash.lowercase(Locale.US) }
            .filter { rec ->
                rec.hash.lowercase(Locale.US) !in loadedHashes && !isPendingExpired(rec, nowSec)
            }
    }

    /**
     * Display-time merge: prepend the still-relevant local pendings to the loaded list. Drops any the
     * backend already returned (same hash), that aged out, or that don't belong to the current filter.
     */
    private fun mergeLocalPending(
        loaded: List<TransactionRecord>,
        pending: List<TransactionRecord>
    ): List<TransactionRecord> {
        if (pending.isEmpty()) return loaded
        val nowSec = System.currentTimeMillis() / 1000
        val loadedHashes = loaded.mapTo(HashSet()) { it.hash.lowercase(Locale.US) }
        val active = pending.filter { rec ->
            rec.hash.lowercase(Locale.US) !in loadedHashes &&
                !isPendingExpired(rec, nowSec) &&
                pendingMatchesCurrentFilter(rec)
        }
        if (active.isEmpty()) return loaded
        return normalizeTransactionHistoryUseCase(active + loaded, currentUserAddress)
    }

    private fun isPendingExpired(rec: TransactionRecord, nowSec: Long): Boolean {
        // A pending with an unset/zero timestamp is treated as fresh (never expire on a missing ts).
        return rec.timestamp > 0L && (nowSec - rec.timestamp) >= pendingLocalTtlSeconds
    }

    /** Only surface a local pending in views it belongs to: all-networks, or its own network. */
    private fun pendingMatchesCurrentFilter(rec: TransactionRecord): Boolean {
        val net = currentNetworkNameStr ?: return true // "all networks" view shows every pending
        return rec.networkName?.name?.equals(net, ignoreCase = true) == true
    }

    private fun applyHistoryRefreshEvent(event: AppEvent.TransactionHistoryNeedsRefresh) {
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
        // Per-address fan-out: one chain failing must not blank the whole merged list, and the
        // stale-warning banner already tells the user the view may be incomplete.
        return try {
            when (val result = getTransactionHistoryUseCase(key.networkName, key.address)) {
                is ResultResponse.Success -> normalizeTransactionHistoryUseCase(result.data, key.address)
                is ResultResponse.Error -> {
                    reportError(
                        throwable = result.exception,
                        userAction = "fetchHistoryFor(${key.networkName})",
                        surface = ErrorSurface.SILENT,
                        severity = ErrorSeverity.LOW
                    )
                    emptyList()
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            reportError(
                throwable = e,
                userAction = "fetchHistoryFor(${key.networkName})",
                surface = ErrorSurface.SILENT,
                severity = ErrorSeverity.LOW
            )
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

            // Cosmetic lookup — rows fall back to the raw counterparty label.
            is ResultResponse.Error -> {
                reportError(
                    throwable = result.exception,
                    userAction = "loadWalletAddressBook",
                    surface = ErrorSurface.SILENT,
                    severity = ErrorSeverity.LOW
                )
                walletAddressBook
            }
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

                    // Optional enrichment of an already-rendered row (ErrorSurface.SILENT).
                    is ResultResponse.Error -> reportError(
                        throwable = result.exception,
                        userAction = "loadTransactionFeeDetails",
                        surface = ErrorSurface.SILENT,
                        severity = ErrorSeverity.LOW
                    )
                }
            } finally {
                _transactionFeeDetailsLoading.update { it - key }
            }
        }
    }

    fun getDateHeaders(timestampSeconds: Long): String =
        displayFormatter.dateHeader(timestampSeconds)

    fun getHistoryDateHeader(transaction: TransactionRecord): String =
        displayFormatter.historyDateHeader(transaction)

    fun formatTransactionTime(timestampSeconds: Long): String =
        displayFormatter.transactionTime(timestampSeconds)

    fun formatTimelineSubmitted(transaction: TransactionRecord): String =
        displayFormatter.timelineSubmitted(transaction)

    fun formatTimelineCompleted(transaction: TransactionRecord): String? =
        displayFormatter.timelineCompleted(transaction)

    fun formatPendingDuration(transaction: TransactionRecord): String =
        displayFormatter.pendingDuration(transaction)

    fun formatTransactionAmount(transaction: TransactionRecord): String =
        displayFormatter.transactionAmount(transaction)

    fun formatListAmount(transaction: TransactionRecord): String =
        displayFormatter.listAmount(transaction)

    fun formatTransactionFiat(transaction: TransactionRecord): String? =
        displayFormatter.transactionFiat(transaction)

    fun formatTransactionFiatDetail(transaction: TransactionRecord): String? =
        displayFormatter.transactionFiatDetail(transaction, assetUsdPrices)

    fun formatTransactionFee(transaction: TransactionRecord): String =
        displayFormatter.transactionFee(transaction, transactionDetailFor(transaction))

    fun isTransactionFeeDetailsLoading(transaction: TransactionRecord): Boolean {
        val key = transactionDetailKey(transaction) ?: return false
        return _transactionFeeDetailsLoading.value.contains(key)
    }

    fun formatTronEnergyFee(transaction: TransactionRecord): String? =
        displayFormatter.tronEnergyFee(transaction, transactionDetailFor(transaction))

    fun formatTronNetworkFee(transaction: TransactionRecord): String? =
        displayFormatter.tronNetworkFee(transaction, transactionDetailFor(transaction))

    fun getNetworkDisplayName(transaction: TransactionRecord): String =
        displayFormatter.networkDisplayName(transaction)

    fun getHistoryPrimaryLabel(transaction: TransactionRecord): String =
        displayFormatter.historyPrimaryLabel(transaction)

    fun getHistoryCounterpartyLabel(transaction: TransactionRecord): String =
        displayFormatter.historyCounterpartyLabel(transaction, walletAddressBook)

    fun isCounterpartyInternal(transaction: TransactionRecord): Boolean =
        displayFormatter.isCounterpartyInternal(transaction, walletAddressBook)

    fun getCounterpartyAccentColor(transaction: TransactionRecord): Int? =
        displayFormatter.counterpartyAccentColor(transaction, walletAddressBook)

    fun getHistoryAssetTitle(transaction: TransactionRecord): String =
        displayFormatter.historyAssetTitle(transaction)

    fun getHistoryAssetIconUrl(transaction: TransactionRecord): String? =
        displayFormatter.historyAssetIconUrl(transaction)

    fun buildExplorerUrl(transaction: TransactionRecord): String? =
        displayFormatter.buildExplorerUrl(transaction)

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


    fun networkId(transaction: TransactionRecord): String =
        displayFormatter.networkId(transaction)
}
