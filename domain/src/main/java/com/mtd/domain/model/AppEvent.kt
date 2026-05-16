package com.mtd.domain.model

sealed class AppEvent {
    data object WalletNeedsRefresh : AppEvent()

    data class WalletAssetNeedsRefresh(
        val assetId: String,
        val networkId: String,
        val contractAddress: String? = null
    ) : AppEvent()

    data class TransactionHistoryNeedsRefresh(
        val networkName: String? = null,
        val userAddress: String? = null,
        val pendingTransaction: PendingTransactionHint? = null
    ) : AppEvent()
}

data class PendingTransactionHint(
    val hash: String,
    val networkName: String,
    val networkType: String,
    val fromAddress: String?,
    val toAddress: String?,
    val amount: String,
    val fee: String = "0",
    val tokenSymbol: String?,
    val tokenDecimals: Int?,
    val contractAddress: String?,
    val isOutgoing: Boolean = true,
    val submittedAtSeconds: Long = System.currentTimeMillis() / 1000L
)
