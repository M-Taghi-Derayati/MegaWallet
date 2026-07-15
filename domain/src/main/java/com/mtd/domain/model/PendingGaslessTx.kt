package com.mtd.domain.model

// Phase 4: keyed by networkId (+ queueId) — the GaslessChain field is gone.
data class PendingGaslessTx(
    val queueId: String,
    val networkId: String,
    val walletId: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)
