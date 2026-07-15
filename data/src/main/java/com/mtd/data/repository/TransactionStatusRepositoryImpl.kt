package com.mtd.data.repository

import com.mtd.data.dto.TxStatusDto
import com.mtd.data.network.proxyCall
import com.mtd.data.service.MobileProxyApiService
import com.mtd.data.utils.ExponentialBackoff
import com.mtd.domain.interfaceRepository.ITransactionStatusRepository
import com.mtd.domain.model.ChainTxStatus
import com.mtd.domain.model.ResultResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 — polls the mobile proxy `…/transactions/{txId}/status` (BM-33 envelope) until the
 * transaction reaches a terminal state. Unwraps via [proxyCall], so all failures surface as the
 * Phase 1 typed [com.mtd.domain.model.error.ApiException]. A WebSocket backfill can later feed the
 * same [ChainTxStatus] through [observeStatus] without touching callers.
 */
@Singleton
class TransactionStatusRepositoryImpl @Inject constructor(
    private val proxyApi: MobileProxyApiService
) : ITransactionStatusRepository {

    override suspend fun getStatus(networkId: String, txId: String): ResultResponse<ChainTxStatus> =
        proxyCall(
            call = { proxyApi.transactionStatus(networkId, txId) },
            map = { it.toDomain(txId) }
        )

    override fun observeStatus(
        networkId: String,
        txId: String,
        pollIntervalMs: Long,
        maxAttempts: Int
    ): Flow<ChainTxStatus> = flow {
        // Shared capped exponential backoff (Sprint 3) — pollIntervalMs is the base; the interval
        // doubles each attempt up to a 30s ceiling so a slow tx doesn't hammer the proxy.
        val base = pollIntervalMs.coerceAtLeast(1L)
        val backoff = ExponentialBackoff(baseDelayMs = base, maxDelayMs = MAX_POLL_INTERVAL_MS.coerceAtLeast(base))
        var attempt = 0
        while (attempt < maxAttempts) {
            when (val result = getStatus(networkId, txId)) {
                is ResultResponse.Success -> {
                    emit(result.data)
                    if (result.data.isFinal) return@flow
                }
                is ResultResponse.Error -> {
                    // Transient — keep polling until the attempt budget is spent.
                }
            }
            attempt++
            if (attempt < maxAttempts) delay(backoff.nextDelayMs())
        }
    }

    private fun TxStatusDto.toDomain(fallbackTxId: String): ChainTxStatus = ChainTxStatus(
        txId = txId ?: fallbackTxId,
        status = status ?: "PENDING",
        confirmations = confirmations
    )

    private companion object {
        private const val MAX_POLL_INTERVAL_MS = 30_000L
    }
}
