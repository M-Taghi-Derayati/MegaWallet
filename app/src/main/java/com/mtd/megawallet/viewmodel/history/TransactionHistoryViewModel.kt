package com.mtd.megawallet.viewmodel.history

import androidx.lifecycle.SavedStateHandle
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.BalanceFormatter
import com.mtd.core.wallet.ActiveWalletManager
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.BitcoinTransaction
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import com.mtd.domain.model.TronTransaction
import com.mtd.domain.model.core.NetworkName
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val walletRepository: IWalletRepository,
    private val activeWalletManager: ActiveWalletManager,
    private val blockchainRegistry: BlockchainRegistry,
    errorManager: com.mtd.core.manager.ErrorManager
) : BaseViewModel(errorManager) {

    private val _transactions = MutableStateFlow<List<TransactionRecord>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _selectedTransaction = MutableStateFlow<TransactionRecord?>(null)
    val selectedTransaction = _selectedTransaction.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private var lastLoadedKey: String? = null

    fun refresh(networkNameStr: String?, userAddress: String?) {
        lastLoadedKey = null
        loadHistory(networkNameStr, userAddress)
    }

    fun loadHistory(networkNameStr: String?, userAddress: String?) {
        val normalizedNetwork = networkNameStr?.trim().orEmpty().ifBlank { null }
        val normalizedAddress = userAddress?.trim().orEmpty().ifBlank { null }
        val requestKey = "${normalizedNetwork ?: "all"}|${normalizedAddress ?: "all"}"

        if (lastLoadedKey == requestKey && (_transactions.value.isNotEmpty() || _errorMessage.value != null)) {
            return
        }

        lastLoadedKey = requestKey

        if (normalizedNetwork == null || normalizedAddress == null) {
            loadAllHistory()
            return
        }

        launchSafe {
            _isLoading.value = true
            _errorMessage.value = null
            _selectedTransaction.value = null

            try {
                val networkName = NetworkName.entries.find {
                    it.name.equals(normalizedNetwork, ignoreCase = true)
                }

                if (networkName == null) {
                    _transactions.value = emptyList()
                    _errorMessage.value = "شبکه موردنظر پیدا نشد"
                    return@launchSafe
                }

                when (val result = walletRepository.getTransactionHistory(networkName, normalizedAddress)) {
                    is ResultResponse.Success -> {
                        _transactions.value = result.data
                            .distinctBy { "${it.networkName?.name}:${it.hash}:${it.isOutgoing}:${it.amount}" }
                            .sortedWith(
                                compareByDescending<TransactionRecord> { it.timestamp }
                                    .thenByDescending { it.submittedAt ?: 0L }
                            )
                    }

                    is ResultResponse.Error -> {
                        _transactions.value = emptyList()
                        _errorMessage.value = result.exception.message ?: "دریافت تاریخچه تراکنش ناموفق بود"
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadAllHistory() {
        launchSafe {
            _isLoading.value = true
            _errorMessage.value = null
            _selectedTransaction.value = null

            val wallet = activeWalletManager.activeWallet.value ?: run {
                _transactions.value = emptyList()
                _errorMessage.value = "کیف پول فعالی انتخاب نشده است"
                _isLoading.value = false
                return@launchSafe
            }

            val results = wallet.keys.map { key ->
                async {
                    val networkNameEnum = NetworkName.entries.find {
                        it.name.equals(key.networkName.toString(), ignoreCase = true)
                    }
                    if (networkNameEnum != null) {
                        try {
                            val result = walletRepository.getTransactionHistory(networkNameEnum, key.address)
                            if (result is ResultResponse.Success) result.data else emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()

            _transactions.value = results
                .distinctBy { "${it.networkName?.name}:${it.hash}:${it.isOutgoing}:${it.amount}" }
                .sortedWith(
                    compareByDescending<TransactionRecord> { it.timestamp }
                        .thenByDescending { it.submittedAt ?: 0L }
                )
            _isLoading.value = false
        }
    }

    fun selectTransaction(transaction: TransactionRecord?) {
        _selectedTransaction.value = transaction
    }

    fun getDateHeader(timestampSeconds: Long): String {
        if (timestampSeconds <= 0L) return "بدون تاریخ"

        val txCalendar = Calendar.getInstance().apply {
            timeInMillis = timestampSeconds * 1000
        }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            txCalendar.isSameDay(today) -> "امروز"
            txCalendar.isSameDay(yesterday) -> "دیروز"
            else -> SimpleDateFormat("d MMMM yyyy", Locale("fa")).format(Date(timestampSeconds * 1000))
        }
    }

    fun formatTransactionTime(timestampSeconds: Long): String {
        if (timestampSeconds <= 0L) return "--:--"
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampSeconds * 1000))
    }

    fun formatTimelineSubmitted(transaction: TransactionRecord): String {
        val value = transaction.submittedAt ?: transaction.timestamp
        if (value <= 0L) return "نامشخص"
        return "${getDateHeader(value)}، ${formatTransactionTime(value)}"
    }

    fun formatTimelineCompleted(transaction: TransactionRecord): String? {
        if (transaction.status != TransactionStatus.CONFIRMED || transaction.timestamp <= 0L) return null
        return "${getDateHeader(transaction.timestamp)}، ${formatTransactionTime(transaction.timestamp)}"
    }

    fun formatPendingDuration(transaction: TransactionRecord): String {
        val seconds = transaction.pendingDurationSeconds
        if (seconds == null || seconds <= 0L) {
            return if (transaction.status == TransactionStatus.PENDING) "در حال انتظار..." else "-"
        }

        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> "${hours} ساعت"
            minutes > 0 -> "${minutes} دقیقه"
            else -> "${seconds} ثانیه"
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
        val signed = if (transaction.isOutgoing) "-$formatted" else "+$formatted"
        return if (symbol.isBlank()) signed else "$signed $symbol"
    }

    fun formatTransactionFiat(transaction: TransactionRecord): String? {
        val value = transaction.fiatValue ?: return null
        return "$${BalanceFormatter.formatUsdValue(BigDecimal.valueOf(value), false)}"
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
        return if (transaction.isOutgoing) "برداشت" else "واریز"
    }

    fun getTransactionStatusLabel(status: TransactionStatus): String {
        return when (status) {
            TransactionStatus.PENDING -> "در انتظار"
            TransactionStatus.CONFIRMED -> "تأیید شده"
            TransactionStatus.FAILED -> "ناموفق"
        }
    }

    fun getNetworkDisplayName(transaction: TransactionRecord): String {
        return blockchainRegistry.getNetworkByName(transaction.networkName ?: return "شبکه")
            ?.faName
            ?.takeIf { it.isNotBlank() }
            ?: transaction.networkName?.name
            ?: "شبکه"
    }

    fun buildExplorerUrl(transaction: TransactionRecord): String? {
        val network = blockchainRegistry.getNetworkByName(transaction.networkName ?: return null) ?: return null
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
                    blockchainRegistry.getNetworkByName(transaction.networkName ?: return "")?.currencySymbol.orEmpty()
                }
            }
            is TronTransaction -> {
                if (!forFee && transaction.tokenTransferDetails != null) {
                    transaction.tokenTransferDetails!!.tokenSymbol
                } else {
                    blockchainRegistry.getNetworkByName(transaction.networkName ?: return "")?.currencySymbol.orEmpty()
                }
            }
            is BitcoinTransaction -> {
                blockchainRegistry.getNetworkByName(transaction.networkName ?: return "")?.currencySymbol.orEmpty()
            }
        }
    }

    private fun networkDecimals(transaction: TransactionRecord): Int {
        return blockchainRegistry.getNetworkByName(transaction.networkName ?: return 0)?.decimals ?: 0
    }

    private fun Calendar.isSameDay(other: Calendar): Boolean {
        return get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
            get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    }
}
