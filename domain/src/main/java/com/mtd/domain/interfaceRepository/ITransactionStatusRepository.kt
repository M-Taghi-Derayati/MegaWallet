package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.ChainTxStatus
import com.mtd.domain.model.ResultResponse
import kotlinx.coroutines.flow.Flow

/**
 * Phase 3 — tracks a transaction (from relay/broadcast) to a terminal state via the mobile proxy
 * status endpoint. [observeStatus] polls until the status is final (or attempts are exhausted);
 * a future WebSocket backfill can push the same [ChainTxStatus] through this seam.
 */
interface ITransactionStatusRepository {

    /** One-shot status read. */
    suspend fun getStatus(networkId: String, txId: String): ResultResponse<ChainTxStatus>

    /**
     * Emits status updates until [ChainTxStatus.isFinal] or [maxAttempts] is reached. Transient
     * errors are skipped (the poll retries); the flow completes after the final status is emitted.
     */
    fun observeStatus(
        networkId: String,
        txId: String,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
    ): Flow<ChainTxStatus>

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 4_000L
        const val DEFAULT_MAX_ATTEMPTS = 45
    }
}
