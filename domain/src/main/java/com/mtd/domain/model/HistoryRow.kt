package com.mtd.domain.model

sealed interface HistoryRow {
    val stableKey: String

    data class Header(
        val title: String
    ) : HistoryRow {
        override val stableKey: String = "header_$title"
    }

    data class Transaction(
        val item: TransactionRecord
    ) : HistoryRow {
        override val stableKey: String =
            "${item.networkName?.name}:${item.hash}:${item.timestamp}:${item.fromAddress}:${item.toAddress}:${item.amount}"
    }
}
