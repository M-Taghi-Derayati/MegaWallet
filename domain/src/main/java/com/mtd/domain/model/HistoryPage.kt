package com.mtd.domain.model

/**
 * A cursor page of unified, multi-network history. [items] are the existing polymorphic
 * [TransactionRecord] domain models; [nextCursor] is opaque (null ⇒ end of stream).
 * [staleSources] (non-empty ⇒ the page may be incomplete) lists the network sources that failed.
 */
data class HistoryPage(
    val items: List<TransactionRecord>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val staleSources: List<String>? = null
)

/**
 * A single (networkId, address) source for the unified history request (§1.7).
 * [networkId] is the backend config id (e.g. "evm", "tron"), NOT the [core.NetworkName] enum name.
 */
data class HistoryAddress(
    val networkId: String,
    val address: String
)
