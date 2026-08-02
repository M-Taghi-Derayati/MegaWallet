package com.mtd.domain.model

sealed class AppEvent {
    /** On-chain data actually moved (socket invalidation, a send completing) — refetch now. */
    data object WalletNeedsRefresh : AppEvent()

    /**
     * The network/asset **catalog** changed (a signed config bundle replaced the local seed), not the
     * chain data. Screens should rebuild their lists against the new catalog, but the balances and
     * history they already hold are still valid, so this must NOT force a network round-trip.
     *
     * Separate from [WalletNeedsRefresh] because the bootstrapper used to publish that one: the seed →
     * bundle swap changes the network set on every cold start, so every launch triggered a full
     * unconditional refresh and defeated every cache TTL in the app.
     */
    data object CatalogChanged : AppEvent()

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
