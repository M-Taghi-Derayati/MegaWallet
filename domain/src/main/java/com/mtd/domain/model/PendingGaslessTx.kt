package com.mtd.domain.model

data class PendingGaslessTx(
    val chain: GaslessChain,
    val queueId: String,
    val networkId: String,
    val walletId: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)
