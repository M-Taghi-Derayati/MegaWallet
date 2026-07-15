package com.mtd.domain.model

import java.math.BigInteger

/**
 * Phase 3 — terminal/in-flight status of a broadcast (or relayed) on-chain transaction, polled from
 * the mobile proxy `…/transactions/{txId}/status`.
 */
data class ChainTxStatus(
    val txId: String,
    val txHash: String? = null,
    /** Raw status token from the proxy — CONFIRMED | PENDING | FAILED (branch via [isFinal]). */
    val status: String,
    val confirmations: Long? = null,
    val feeRaw: BigInteger? = null
) {
    val isConfirmed: Boolean get() = status.equals("CONFIRMED", ignoreCase = true)
    val isFailed: Boolean
        get() = status.equals("FAILED", ignoreCase = true) || status.equals("REJECTED", ignoreCase = true)
    val isFinal: Boolean get() = isConfirmed || isFailed
}
