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
import com.mtd.domain.model.BitcoinTransaction
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.PendingTransactionHint
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TokenTransferDetails
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import com.mtd.domain.model.TronTransaction
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.core.WalletKey
import com.mtd.domain.usecase.history.GetTransactionHistoryUseCase
import com.mtd.domain.usecase.history.GetWalletAddressBookUseCase
import com.mtd.domain.usecase.asset.GetLatestAssetPricesUseCase
import com.mtd.domain.usecase.wallet.GetActiveWalletIdUseCase
import com.mtd.domain.usecase.wallet.GetActiveWalletUseCase
import com.mtd.domain.usecase.wallet.ObserveActiveWalletUseCase
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    private val networkCatalog: INetworkCatalog,
    private val assetCatalog: IAssetCatalog,
    private val appEventBus: IAppEventBus,
    private val cacheStore: IAppCacheStore,
    private val getTransactionHistoryUseCase: GetTransactionHistoryUseCase,
    private val getWalletAddressBookUseCase: GetWalletAddressBookUseCase,
    private val getLatestAssetPricesUseCase: GetLatestAssetPricesUseCase,
    private val observeActiveWalletUseCase: ObserveActiveWalletUseCase,
    private val getActiveWalletUseCase: GetActiveWalletUseCase,
    private val getActiveWalletIdUseCase: GetActiveWalletIdUseCase,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    private val _transactions = MutableStateFlow<List<TransactionRecord>>(emptyList())
    val transactions = _transactions.asStateFlow()

    val activeWallet = observeActiveWalletUseCase()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _selectedTransaction = MutableStateFlow<TransactionRecord?>(null)
    val selectedTransaction = _selectedTransaction.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private var lastLoadedKey: String? = null
    private var currentNetworkNameStr: String? = null
    private var currentUserAddress: String? = null
    private var walletAddressBook: Map<String, WalletAddressReference> = emptyMap()
    private var assetUsdPrices: Map<String, BigDecimal> = emptyMap()

    init {
        listenToGlobalEvents()
        observeActiveWallet()
    }

    fun refresh(networkNameStr: String?, userAddress: String?) {
        lastLoadedKey = null
        loadHistory(networkNameStr, userAddress, forceRefresh = true)
    }

    fun loadHistory(networkNameStr: String?, userAddress: String?, forceRefresh: Boolean = false) {
        val normalizedNetwork = networkNameStr?.trim().orEmpty().ifBlank { null }
        val normalizedAddress = userAddress?.trim().orEmpty().ifBlank { null }
        currentNetworkNameStr = normalizedNetwork
        currentUserAddress = normalizedAddress
        val requestKey = "${normalizedNetwork ?: "all"}|${normalizedAddress ?: "all"}"

        if (!forceRefresh && lastLoadedKey == requestKey && (_transactions.value.isNotEmpty() || _errorMessage.value != null)) {
            return
        }

        lastLoadedKey = requestKey

        if (normalizedNetwork == null || normalizedAddress == null) {
            loadAllHistory(forceRefresh)
            return
        }

        launchSafe {
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
                        val history = normalizeHistory(cached.toList())
                        refreshAssetPrices(history)
                        _transactions.value = history
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
                        val history = normalizeHistory(result.data)
                        refreshAssetPrices(history)
                        _transactions.value = history
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
            }
        }
    }

    private fun observeActiveWallet() {
        launchSafe(checkNetwork = false) {
            observeActiveWalletUseCase().collect { wallet ->
                if (wallet != null) {
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
        event.pendingTransaction?.toTransactionRecord()?.let { pending ->
            _transactions.value = normalizeHistory(listOf(pending) + _transactions.value)
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

                val fresh = normalizeHistory(fetchHistoryForWalletKey(key))
                val merged = mergeHistoryForKey(
                    current = _transactions.value,
                    key = key,
                    fresh = fresh
                )
                refreshAssetPrices(merged)
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
            _isLoading.value = true
            _transactions.value = emptyList()
            _errorMessage.value = null
            _selectedTransaction.value = null

            val wallet = getActiveWalletUseCase() ?: run {
                _transactions.value = emptyList()
                _errorMessage.value = "No active wallet selected"
                _isLoading.value = false
                return@launchSafe
            }

            val walletId = wallet.id
            val cacheKey = getHistoryCacheKey(walletId, null, null)

            if (!forceRefresh) {
                val cached = cacheStore.get(cacheKey, Array<TransactionRecord>::class.java)
                if (cached != null && cached.isNotEmpty()) {
                    val history = normalizeHistory(cached.toList())
                    refreshAssetPrices(history)
                    _transactions.value = history
                    _isLoading.value = false
                    return@launchSafe
                }
            }

            refreshWalletAddressBook()

            val keys = wallet.keys
                .distinctBy { key -> key.networkName.name to key.address.lowercase(Locale.US) }

            val aggregatedResults = mutableListOf<TransactionRecord>()
            val resultMutex = Mutex()

            supervisorScope {
                keys.map { key ->
                    async(Dispatchers.IO) {
                        val networkResults = fetchHistoryForWalletKey(key)
                        if (networkResults.isNotEmpty()) {
                            resultMutex.withLock {
                                aggregatedResults.addAll(networkResults)
                                _transactions.value = normalizeHistory(aggregatedResults.toList())
                            }
                        }
                    }
                }.joinAll()
            }

            val finalHistory = normalizeHistory(aggregatedResults)
            refreshAssetPrices(finalHistory)
            _transactions.value = finalHistory
            _isLoading.value = false
            
            // Save to Cache
            cacheStore.put(cacheKey, finalHistory.toTypedArray())
        }
    }

    private fun getHistoryCacheKey(walletId: String, networkName: String?, address: String?): String {
        return "history_${walletId}_${networkName ?: "all"}_${address ?: "all"}"
    }

    private suspend fun fetchHistoryForWalletKey(key: WalletKey): List<TransactionRecord> {
        return try {
            when (val result = getTransactionHistoryUseCase(key.networkName, key.address)) {
                is ResultResponse.Success -> result.data
                is ResultResponse.Error -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun refreshAssetPrices(history: List<TransactionRecord>) {
        val symbols = history
            .map { transactionSymbol(it) }
            .filter { it.isNotBlank() }
            .distinctBy { it.uppercase(Locale.US) }

        if (symbols.isEmpty()) {
            assetUsdPrices = emptyMap()
            return
        }

        val existing = assetUsdPrices.keys
        val missing = symbols.filterNot { symbol ->
            existing.any { it.equals(symbol, ignoreCase = true) }
        }
        if (missing.isEmpty()) return

        when (val result = getLatestAssetPricesUseCase(missing)) {
            is ResultResponse.Success -> {
                assetUsdPrices = assetUsdPrices + result.data.associate { price ->
                    price.assetId.uppercase(Locale.US) to price.priceUsd
                }
            }

            is ResultResponse.Error -> Unit
        }
    }

    private fun PendingTransactionHint.toTransactionRecord(): TransactionRecord? {
        val resolvedNetworkName = NetworkName.entries.find {
            it.name.equals(networkName, ignoreCase = true)
        } ?: return null
        val amountValue = amount.toBigIntegerOrNull() ?: return null
        val feeValue = fee.toBigIntegerOrNull() ?: BigInteger.ZERO
        val tokenDetails = contractAddress?.takeIf { it.isNotBlank() }?.let { contract ->
            TokenTransferDetails(
                from = fromAddress.orEmpty(),
                to = toAddress.orEmpty(),
                amount = amountValue,
                tokenSymbol = tokenSymbol.orEmpty(),
                tokenDecimals = tokenDecimals ?: 0,
                contractAddress = contract
            )
        }

        return when (networkType.uppercase(Locale.US)) {
            NetworkType.TVM.name -> TronTransaction(
                hash = hash,
                timestamp = 0L,
                submittedAt = submittedAtSeconds,
                pendingDurationSeconds = 0L,
                fee = feeValue,
                status = TransactionStatus.PENDING,
                networkName = resolvedNetworkName,
                fromAddress = fromAddress.orEmpty(),
                toAddress = toAddress.orEmpty(),
                amount = amountValue,
                isOutgoing = isOutgoing,
                contractAddress = contractAddress,
                tokenTransferDetails = tokenDetails
            )

            NetworkType.BITCOIN.name,
            NetworkType.UTXO.name -> BitcoinTransaction(
                hash = hash,
                timestamp = 0L,
                submittedAt = submittedAtSeconds,
                pendingDurationSeconds = 0L,
                fee = feeValue,
                status = TransactionStatus.PENDING,
                networkName = resolvedNetworkName,
                fromAddress = fromAddress,
                toAddress = toAddress,
                amount = amountValue,
                isOutgoing = isOutgoing
            )

            else -> EvmTransaction(
                hash = hash,
                timestamp = 0L,
                submittedAt = submittedAtSeconds,
                pendingDurationSeconds = 0L,
                fee = feeValue,
                status = TransactionStatus.PENDING,
                networkName = resolvedNetworkName,
                fromAddress = fromAddress.orEmpty(),
                toAddress = toAddress.orEmpty(),
                amount = amountValue,
                isOutgoing = isOutgoing,
                contractAddress = contractAddress,
                tokenTransferDetails = tokenDetails
            )
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
        return normalizeHistory(retained + fresh)
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
    }

    private fun normalizeHistory(items: List<TransactionRecord>): List<TransactionRecord> {
        return items
            .filter { isTransactionSupported(it) }
            .distinctBy { transactionIdentity(it) }
            .sortedWith(
                compareByDescending<TransactionRecord> { it.status == TransactionStatus.PENDING }
                    .thenByDescending { it.submittedAt ?: it.timestamp }
                    .thenByDescending { it.timestamp }
            )
    }

    private fun isTransactionSupported(transaction: TransactionRecord): Boolean {
        if (transaction.amount <= BigInteger.ZERO) {
            return false
        }

        val networkName = transaction.networkName ?: return true
        val network = networkCatalog.getNetworkInfoByName(networkName) ?: return true
        val networkId = network.id

        val contractAddr = when (transaction) {
            is EvmTransaction -> transaction.contractAddress ?: transaction.tokenTransferDetails?.contractAddress
            is TronTransaction -> transaction.contractAddress ?: transaction.tokenTransferDetails?.contractAddress
            else -> null
        }

        if (!contractAddr.isNullOrBlank()) {
            val supportedAssets = assetCatalog.getAssetConfigsForNetwork(networkId)
            return supportedAssets.any { 
                it.contractAddress.equals(contractAddr, ignoreCase = true) 
            }
        }

        return true
    }

    private fun transactionIdentity(transaction: TransactionRecord): String {
        return buildString {
            append(transaction.networkName?.name ?: "unknown")
            append('|')
            append(transaction.hash)
            append('|')
            append(transaction.timestamp)
            append('|')
            append(transaction.submittedAt ?: 0L)
            append('|')
            append(transaction.fromAddress.orEmpty())
            append('|')
            append(transaction.toAddress.orEmpty())
            append('|')
            append(transaction.amount.toString())
            append('|')
            append(transaction.status.name)
            when (transaction) {
                is EvmTransaction -> {
                    append('|')
                    append(transaction.contractAddress.orEmpty())
                    append('|')
                    append(transaction.tokenTransferDetails?.contractAddress.orEmpty())
                }
                is TronTransaction -> {
                    append('|')
                    append(transaction.contractAddress.orEmpty())
                    append('|')
                    append(transaction.tokenTransferDetails?.contractAddress.orEmpty())
                }
                is BitcoinTransaction -> Unit
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
        val sign = if (transaction.isOutgoing) "-" else "+"
        return "$sign\$$formatted"
    }

    private fun transactionFiatValue(transaction: TransactionRecord): BigDecimal? {
        transaction.fiatValue?.let { return BigDecimal.valueOf(it).abs() }
        val symbol = transactionSymbol(transaction).uppercase(Locale.US)
        val price = assetUsdPrices[symbol]?.takeIf { it > BigDecimal.ZERO } ?: return null
        return rawAmountToDecimal(transaction).abs().multiply(price)
    }

    fun formatTransactionFee(transaction: TransactionRecord): String {
        val feeDecimal = rawFeeToDecimal(transaction)
        val symbol = transactionSymbol(transaction, forFee = true)

        return if (transaction.fee == BigInteger.ZERO) {
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

    private fun rawFeeToDecimal(transaction: TransactionRecord): BigDecimal {
        return BigDecimal(transaction.fee).movePointLeft(networkDecimals(transaction))
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
