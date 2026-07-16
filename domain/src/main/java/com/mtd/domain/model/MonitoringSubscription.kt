package com.mtd.domain.model

/**
 * One `(address, networkId)` pair to enroll into the server's realtime/deposit monitoring set
 * (TASK-32 · `POST /api/mobile/v1/monitoring/subscribe`). `networkId` is the bundle id verbatim
 * (e.g. `sepolia`, `base_sepolia`, `shasta_testnet`).
 */
data class MonitoringSubscription(
    val address: String,
    val networkId: String
)

/** Server-reported outcome of a batch monitoring enrollment call. */
data class MonitoringSubscribeResult(
    val subscribed: Int,
    val total: Int
)
