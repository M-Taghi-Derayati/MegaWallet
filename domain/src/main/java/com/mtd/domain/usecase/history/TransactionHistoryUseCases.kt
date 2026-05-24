package com.mtd.domain.usecase.history

import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.BitcoinTransaction
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.PendingTransactionHint
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TokenTransferDetails
import com.mtd.domain.model.TransactionFeeDetails
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import com.mtd.domain.model.TronTransaction
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import java.math.BigInteger
import javax.inject.Inject

data class WalletAddressBookEntry(
    val address: String,
    val walletName: String,
    val walletColor: Int
)

class GetTransactionHistoryUseCase @Inject constructor(
    private val walletRepository: IWalletRepository
) {
    suspend operator fun invoke(
        networkName: NetworkName,
        userAddress: String
    ): ResultResponse<List<TransactionRecord>> {
        return walletRepository.getTransactionHistory(networkName, userAddress)
    }
}

class GetTransactionFeeDetailsUseCase @Inject constructor(
    private val walletRepository: IWalletRepository
) {
    suspend operator fun invoke(
        networkName: NetworkName,
        txId: String
    ): ResultResponse<TransactionFeeDetails> {
        return walletRepository.getTransactionFeeDetails(networkName, txId)
    }
}

class GetWalletAddressBookUseCase @Inject constructor(
    private val walletRepository: IWalletRepository
) {
    suspend operator fun invoke(): ResultResponse<List<WalletAddressBookEntry>> {
        return when (val result = walletRepository.getAllWallets()) {
            is ResultResponse.Success -> ResultResponse.Success(
                result.data.flatMap { wallet ->
                    wallet.keys.map { key ->
                        WalletAddressBookEntry(
                            address = key.address,
                            walletName = wallet.name,
                            walletColor = wallet.color
                        )
                    }
                }
            )

            is ResultResponse.Error -> ResultResponse.Error(result.exception)
        }
    }
}

class NormalizeTransactionHistoryUseCase @Inject constructor(
    private val networkCatalog: INetworkCatalog,
    private val assetCatalog: IAssetCatalog
) {
    operator fun invoke(
        items: List<TransactionRecord>,
        userAddress: String? = null
    ): List<TransactionRecord> {
        val reconciled = userAddress
            ?.takeIf { it.isNotBlank() }
            ?.let { address -> items.map { reconcileTransactionDirection(it, address) } }
            ?: items

        return reconciled
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

        if (transaction is TronTransaction && !transaction.isTransferRecord()) {
            return false
        }

        val networkName = transaction.networkName ?: return true
        val network = networkCatalog.getNetworkInfoByName(networkName) ?: return true
        val networkId = network.id

        val contractAddress = when (transaction) {
            is EvmTransaction -> transaction.contractAddress ?: transaction.tokenTransferDetails?.contractAddress
            is TronTransaction -> transaction.contractAddress ?: transaction.tokenTransferDetails?.contractAddress
            else -> null
        }

        if (!contractAddress.isNullOrBlank()) {
            val supportedAssets = assetCatalog.getAssetConfigsForNetwork(networkId)
            return supportedAssets.any {
                it.contractAddress.equals(contractAddress, ignoreCase = true)
            }
        }

        return true
    }

    private fun TronTransaction.isTransferRecord(): Boolean {
        val tokenTransfer = tokenTransferDetails
        if (tokenTransfer != null) {
            return tokenTransfer.amount > BigInteger.ZERO &&
                tokenTransfer.from.isNotBlank() &&
                tokenTransfer.to.isNotBlank()
        }

        return contractAddress.isNullOrBlank() &&
            fromAddress.isNotBlank() &&
            toAddress.isNotBlank()
    }

    private fun reconcileTransactionDirection(
        transaction: TransactionRecord,
        userAddress: String
    ): TransactionRecord {
        val tokenTransfer = when (transaction) {
            is EvmTransaction -> transaction.tokenTransferDetails
            is TronTransaction -> transaction.tokenTransferDetails
            is BitcoinTransaction -> null
        }
        val fromAddress = tokenTransfer?.from ?: transaction.fromAddress
        val toAddress = tokenTransfer?.to ?: transaction.toAddress
        val fromMatches = fromAddress.equals(userAddress, ignoreCase = true)
        val toMatches = toAddress.equals(userAddress, ignoreCase = true)
        val correctedIsOutgoing = when {
            fromMatches && !toMatches -> true
            toMatches && !fromMatches -> false
            else -> transaction.isOutgoing
        }

        if (correctedIsOutgoing == transaction.isOutgoing) {
            return transaction
        }

        return when (transaction) {
            is BitcoinTransaction -> transaction.copy(isOutgoing = correctedIsOutgoing)
            is TronTransaction -> transaction.copy(isOutgoing = correctedIsOutgoing)
            is EvmTransaction -> transaction.copy(isOutgoing = correctedIsOutgoing)
        }
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
}

class BuildPendingHistoryTransactionUseCase @Inject constructor() {
    operator fun invoke(hint: PendingTransactionHint): TransactionRecord? {
        val resolvedNetworkName = NetworkName.entries.find {
            it.name.equals(hint.networkName, ignoreCase = true)
        } ?: return null
        val amountValue = hint.amount.toBigIntegerOrNull() ?: return null
        val feeValue = hint.fee.toBigIntegerOrNull() ?: BigInteger.ZERO
        val tokenDetails = hint.contractAddress?.takeIf { it.isNotBlank() }?.let { contract ->
            TokenTransferDetails(
                from = hint.fromAddress.orEmpty(),
                to = hint.toAddress.orEmpty(),
                amount = amountValue,
                tokenSymbol = hint.tokenSymbol.orEmpty(),
                tokenDecimals = hint.tokenDecimals ?: 0,
                contractAddress = contract
            )
        }

        return when (hint.networkType.uppercase()) {
            NetworkType.TVM.name -> TronTransaction(
                hash = hint.hash,
                timestamp = 0L,
                submittedAt = hint.submittedAtSeconds,
                pendingDurationSeconds = 0L,
                fee = feeValue,
                status = TransactionStatus.PENDING,
                networkName = resolvedNetworkName,
                fromAddress = hint.fromAddress.orEmpty(),
                toAddress = hint.toAddress.orEmpty(),
                amount = amountValue,
                isOutgoing = hint.isOutgoing,
                contractAddress = hint.contractAddress,
                tokenTransferDetails = tokenDetails
            )

            NetworkType.BITCOIN.name,
            NetworkType.UTXO.name -> BitcoinTransaction(
                hash = hint.hash,
                timestamp = 0L,
                submittedAt = hint.submittedAtSeconds,
                pendingDurationSeconds = 0L,
                fee = feeValue,
                status = TransactionStatus.PENDING,
                networkName = resolvedNetworkName,
                fromAddress = hint.fromAddress,
                toAddress = hint.toAddress,
                amount = amountValue,
                isOutgoing = hint.isOutgoing
            )

            else -> EvmTransaction(
                hash = hint.hash,
                timestamp = 0L,
                submittedAt = hint.submittedAtSeconds,
                pendingDurationSeconds = 0L,
                fee = feeValue,
                status = TransactionStatus.PENDING,
                networkName = resolvedNetworkName,
                fromAddress = hint.fromAddress.orEmpty(),
                toAddress = hint.toAddress.orEmpty(),
                amount = amountValue,
                isOutgoing = hint.isOutgoing,
                contractAddress = hint.contractAddress,
                tokenTransferDetails = tokenDetails
            )
        }
    }
}
