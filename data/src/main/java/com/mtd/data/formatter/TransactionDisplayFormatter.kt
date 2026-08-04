package com.mtd.data.formatter

import com.mtd.core.utils.AddressUtils
import com.mtd.core.utils.BalanceFormatter
import com.mtd.core.utils.DateTimeUtils.getDateHeader
import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.model.BitcoinTransaction
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.FiatCurrency
import com.mtd.domain.model.TransactionFeeDetails
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import com.mtd.domain.model.TronTransaction
import java.math.BigDecimal
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** A resolved local wallet address, used to label a transaction's counterparty. */
data class WalletAddressReference(
    val name: String,
    val color: Int
)

/**
 * Pure display formatting for transaction-history rows and the detail sheet (TASK-14).
 *
 * Extracted out of [TransactionHistoryViewModel] so the presentation logic is unit-testable in
 * isolation and the ViewModel keeps only state + orchestration. Every method here is a pure
 * function of its inputs and the injected read-only catalogs — it holds no mutable state. Runtime
 * context that lives on the ViewModel (the on-demand fee detail, USD prices, the wallet address
 * book) is therefore passed in explicitly, so a caller can `remember(...)` a result off the
 * relevant keys instead of recomputing it on every recomposition.
 */
class TransactionDisplayFormatter @Inject constructor(
    private val networkCatalog: INetworkCatalog,
    private val assetCatalog: IAssetCatalog
) {

    // ---- Date / time ----

    fun dateHeader(timestampSeconds: Long): String = getDateHeader(timestampSeconds)

    fun historyDateHeader(transaction: TransactionRecord): String {
        return if (transaction.status == TransactionStatus.PENDING) {
            "در انتظار"
        } else {
            getDateHeader(transaction.timestamp)
        }
    }

    fun transactionTime(timestampSeconds: Long): String {
        if (timestampSeconds <= 0L) return "--:--"
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampSeconds * 1000))
    }

    fun timelineSubmitted(transaction: TransactionRecord): String {
        val value = transaction.submittedAt ?: transaction.timestamp
        if (value <= 0L) return "نامشخص"
        return "${getDateHeader(value)}, ${transactionTime(value)}"
    }

    fun timelineCompleted(transaction: TransactionRecord): String? {
        if (transaction.status != TransactionStatus.CONFIRMED || transaction.timestamp <= 0L) return null
        return "${getDateHeader(transaction.timestamp)}, ${transactionTime(transaction.timestamp)}"
    }

    fun pendingDuration(transaction: TransactionRecord): String {
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

    // ---- Amount / fiat ----

    fun transactionAmount(transaction: TransactionRecord): String {
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

    fun listAmount(transaction: TransactionRecord): String {
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

    /**
     * TASK-56 — history-row fiat, in the user's selected currency.
     *
     * [currency] and [rate] are parameters rather than injected state on purpose: this class is a pure
     * formatter with no mutable state, so the caller passes the values it observes and can
     * `remember(...)` the result off them. `null` [rate] with [FiatCurrency.TOMAN] yields
     * [com.mtd.core.utils.FiatConversion.UNKNOWN_PLACEHOLDER], never a zero.
     *
     * Unlike before, the symbol is included: a bare "1,234" is legible as USD but ambiguous once
     * تومان is selectable.
     */
    fun transactionFiat(
        transaction: TransactionRecord,
        currency: FiatCurrency,
        rate: CurrencyRate?
    ): String? {
        val value = transaction.fiatValue ?: return null
        return BalanceFormatter.formatFiatValue(
            usdAmount = BigDecimal.valueOf(value),
            currency = currency,
            rate = rate
        )
    }

    /**
     * Detail-sheet fiat. Returns the **bare** amount — the receipt draws the currency symbol as its own
     * composable — so [BalanceFormatter.formatFiatValue] is called with `withSymbol = false`.
     */
    fun transactionFiatDetail(
        transaction: TransactionRecord,
        usdPrices: Map<String, BigDecimal>,
        currency: FiatCurrency,
        rate: CurrencyRate?
    ): String? {
        val amount = transactionFiatValue(transaction, usdPrices) ?: return null
        return BalanceFormatter.formatFiatValue(
            usdAmount = amount,
            currency = currency,
            rate = rate,
            withSymbol = false
        )
    }

    private fun transactionFiatValue(
        transaction: TransactionRecord,
        usdPrices: Map<String, BigDecimal>
    ): BigDecimal? {
        transaction.fiatValue?.let { return BigDecimal.valueOf(it).abs() }
        val symbol = transactionSymbol(transaction).uppercase(Locale.US)
        val price = usdPrices[symbol]?.takeIf { it > BigDecimal.ZERO } ?: return null
        return rawAmountToDecimal(transaction).abs().multiply(price)
    }

    // ---- Fee (the caller resolves the on-demand fee detail and passes it in) ----

    fun transactionFee(
        transaction: TransactionRecord,
        feeDetail: TransactionFeeDetails?
    ): String {
        val feeValue = feeDetail?.fee ?: transaction.fee
        val symbol = transactionSymbol(transaction, forFee = true)

        return when {
            // TASK-16 — an unknown fee (not yet fetched, or not provided by the source) must NOT read as
            // "0": that conflated "we don't know" with a genuine zero-fee tx. Show a neutral placeholder.
            feeValue == null -> FEE_UNKNOWN_PLACEHOLDER
            feeValue == BigInteger.ZERO -> "0 $symbol".trim()
            else -> {
                val feeDecimal = rawFeeToDecimal(transaction, feeValue)
                val formatted = BalanceFormatter.formatBalance(
                    balance = feeDecimal,
                    decimals = networkDecimals(transaction),
                    usePersianSeparator = true
                )
                "$formatted $symbol".trim()
            }
        }
    }

    fun tronEnergyUsed(
        transaction: TransactionRecord,
        feeDetail: TransactionFeeDetails?
    ): String? {
        val tron = transaction as? TronTransaction ?: return null
        val value = feeDetail?.energyUsed ?: tron.energyUsed
        return value?.toString()
    }

    fun tronBandwidthUsed(
        transaction: TransactionRecord,
        feeDetail: TransactionFeeDetails?
    ): String? {
        val tron = transaction as? TronTransaction ?: return null
        val value = feeDetail?.bandwidthUsed ?: tron.bandwidthUsed
        return value?.toString()
    }

    fun tronEnergyFee(
        transaction: TransactionRecord,
        feeDetail: TransactionFeeDetails?
    ): String? {
        val fee = feeDetail?.energyFee ?: return null
        return formatNativeFeeAmount(transaction, fee)
    }

    fun tronNetworkFee(
        transaction: TransactionRecord,
        feeDetail: TransactionFeeDetails?
    ): String? {
        val fee = feeDetail?.networkFee ?: return null
        return formatNativeFeeAmount(transaction, fee)
    }

    // ---- Labels / names ----

    fun transactionTypeLabel(transaction: TransactionRecord): String {
        return if (transaction.isOutgoing) "Withdraw" else "Deposit"
    }

    fun statusLabel(status: TransactionStatus): String {
        return when (status) {
            TransactionStatus.PENDING -> "Pending"
            TransactionStatus.CONFIRMED -> "Confirmed"
            TransactionStatus.FAILED -> "Failed"
        }
    }

    /**
     * TASK-53 — شبکهٔ یک رکورد را با هویتِ کانونی پیدا می‌کند و فقط در نبودِ آن به alias قدیمی
     * برمی‌گردد (رکوردهای کش‌شدهٔ قبل از این تغییر `networkId` ندارند).
     */
    private fun networkOf(transaction: TransactionRecord) =
        transaction.networkId?.let { networkCatalog.getNetworkInfoById(it) }
            ?: transaction.networkName?.let { networkCatalog.getNetworkInfoByName(it) }

    fun networkDisplayName(transaction: TransactionRecord): String {
        return networkOf(transaction)
            ?.faName
            ?.takeIf { it.isNotBlank() }
            ?: transaction.networkName?.name
            ?: transaction.networkId
            ?: "Network"
    }

    fun historyPrimaryLabel(transaction: TransactionRecord): String {
        return when {
            transaction.status == TransactionStatus.PENDING && transaction.isOutgoing -> "در حال ارسال به"
            transaction.status == TransactionStatus.PENDING -> "در حال دریافت از"
            transaction.isOutgoing -> "ارسال به"
            else -> "دریافت از"
        }
    }

    fun historyCounterpartyLabel(
        transaction: TransactionRecord,
        addressBook: Map<String, WalletAddressReference>
    ): String {
        val address = counterpartyAddress(transaction) ?: return networkDisplayName(transaction)
        return addressBook[address.lowercase(Locale.US)]?.name ?: shortenAddress(address)
    }

    fun isCounterpartyInternal(
        transaction: TransactionRecord,
        addressBook: Map<String, WalletAddressReference>
    ): Boolean {
        val address = counterpartyAddress(transaction) ?: return false
        return addressBook.containsKey(address.lowercase(Locale.US))
    }

    fun counterpartyAccentColor(
        transaction: TransactionRecord,
        addressBook: Map<String, WalletAddressReference>
    ): Int? {
        val address = counterpartyAddress(transaction) ?: return null
        return addressBook[address.lowercase(Locale.US)]?.color
    }

    fun historyAssetTitle(transaction: TransactionRecord): String {
        val networkId = networkOf(transaction)?.id ?: transaction.networkId.orEmpty()
        val symbol = transactionSymbol(transaction)
        val contractAddr = contractAddressOf(transaction)
        return resolveAssetName(networkId, contractAddr, symbol)
    }

    fun historyAssetIconUrl(transaction: TransactionRecord): String? {
        val networkId = networkOf(transaction)?.id ?: transaction.networkId.orEmpty()
        val symbol = transactionSymbol(transaction)
        val contractAddr = contractAddressOf(transaction)
        return resolveAssetConfig(networkId, contractAddr, symbol)?.iconUrl
    }

    /**
     * نمادِ ارزِ تراکنش — برای fallbackِ آفلاینِ آیکون وقتی `historyAssetIconUrl` چیزی ندارد یا
     * تصویرش لود نمی‌شود. اگر ارز در کاتالوگ نبود، نمادِ خودِ تراکنش برگردانده می‌شود.
     */
    fun historyAssetSymbol(transaction: TransactionRecord): String {
        val networkId = networkOf(transaction)?.id ?: transaction.networkId.orEmpty()
        val symbol = transactionSymbol(transaction)
        val contractAddr = contractAddressOf(transaction)
        return resolveAssetConfig(networkId, contractAddr, symbol)?.symbol ?: symbol
    }

    /**
     * TASK-51 — web-explorer URL for a transaction, or null when the network has no explorer we can
     * link to (callers must hide the action rather than show a dead link).
     *
     * Prefers the network's own `explorerTxUrl` template from `networks.json`. That field exists
     * because `explorers` holds the explorer **API** base URLs the data sources call, which is not
     * the page a human opens — the guess-from-API-base path below silently produced *no link at all*
     * for BSC, TRON and DOGE (their API hosts are nodereal/trongrid/blockcypher, none of which match
     * a known web-explorer host) and a malformed one for Solana devnet, whose base carries a query
     * string that a path was then appended to.
     */
    fun buildExplorerUrl(transaction: TransactionRecord): String? {
        val network = networkOf(transaction) ?: return null
        val hash = transaction.hash.takeIf { it.isNotBlank() } ?: return null

        network.explorerTxUrl?.takeIf { it.isNotBlank() }?.let { template ->
            return template.replace(HASH_PLACEHOLDER, hash)
        }

        // Fallback for networks without a template (and for anything arriving from the server config
        // bundle). Tries every configured explorer in order, not just the first, so one dead entry
        // doesn't cost the link entirely.
        return network.explorers.firstNotNullOfOrNull { explorer ->
            explorerUrlFromApiBase(explorer.trimEnd('/'), hash)
        }
    }

    private fun explorerUrlFromApiBase(base: String, hash: String): String? {
        val host = base.lowercase()
        return when {
            "blockscout" in host -> "$base/tx/$hash"
            "tronscan" in host -> "$base/#/transaction/$hash"
            "mempool.space" in host -> base.removeSuffix("/api") + "/tx/$hash"
            "blockchair.com" in host -> "$base/transaction/$hash"
            "xrpscan" in host -> "$base/tx/$hash"
            "solscan" in host -> "$base/tx/$hash"
            "tonscan" in host -> "$base/tx/$hash"
            "basescan" in host || "etherscan" in host -> "$base/tx/$hash"
            else -> null
        }
    }

    /**
     * TASK-53 — آیکونِ شبکهٔ یک تراکنش، از کانفیگ. `null` یعنی شبکه ناشناخته است یا آیکون
     * اعلام نکرده؛ UI در آن حالت placeholder می‌گذارد، نه یک آیکونِ per-network هاردکد.
     */
    fun networkIconUrl(transaction: TransactionRecord): String? =
        networkOf(transaction)?.iconUrl

    fun networkId(transaction: TransactionRecord): String {
        return networkOf(transaction)?.id ?: transaction.networkId.orEmpty()
    }

    // ---- private helpers ----

    private fun rawAmountToDecimal(transaction: TransactionRecord): BigDecimal {
        return BigDecimal(transaction.amount).movePointLeft(transactionDecimals(transaction))
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
                    networkOf(transaction)?.currencySymbol.orEmpty()
                }
            }
            is TronTransaction -> {
                if (!forFee && transaction.tokenTransferDetails != null) {
                    transaction.tokenTransferDetails!!.tokenSymbol
                } else {
                    networkOf(transaction)?.currencySymbol.orEmpty()
                }
            }
            is BitcoinTransaction -> {
                networkOf(transaction)?.currencySymbol.orEmpty()
            }
        }
    }

    private fun networkDecimals(transaction: TransactionRecord): Int {
        return networkOf(transaction)?.decimals ?: 0
    }

    private fun contractAddressOf(transaction: TransactionRecord): String? {
        return when (transaction) {
            is EvmTransaction -> transaction.contractAddress ?: transaction.tokenTransferDetails?.contractAddress
            is TronTransaction -> transaction.contractAddress ?: transaction.tokenTransferDetails?.contractAddress
            else -> null
        }
    }

    private fun counterpartyAddress(transaction: TransactionRecord): String? {
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

    private fun resolveAssetConfig(networkId: String, contractAddress: String?, fallbackSymbol: String) =
        assetCatalog.getAssetConfigsForNetwork(networkId).find { asset ->
            when {
                !contractAddress.isNullOrBlank() -> asset.contractAddress.equals(contractAddress, ignoreCase = true)
                else -> asset.contractAddress == null && asset.symbol.equals(fallbackSymbol, ignoreCase = true)
            }
        }

    companion object {
        // Neutral "no data" glyph shown when a transaction's fee is unknown (not yet fetched / not
        // reported), so it can't be mistaken for a genuine zero-fee transaction.
        const val FEE_UNKNOWN_PLACEHOLDER = "—"

        /** Placeholder substituted with the tx hash in a network's `explorerTxUrl` template. */
        const val HASH_PLACEHOLDER = "{hash}"
    }
}
