package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.MonitoringSubscribeResult
import com.mtd.domain.model.MonitoringSubscription
import com.mtd.domain.model.ResultResponse

/**
 * TASK-32 — durable, idempotent batch monitoring enrollment (`POST /api/mobile/v1/monitoring/subscribe`).
 * Enrolling an address puts it into the backend indexer's monitored set so realtime `tx.new` /
 * `balance.invalidated` signals + deposit FCM fire for it. Replaces relying on the `/history`
 * side-effect that only enrolled the currently-open wallet.
 */
interface IMonitoringRepository {

    /**
     * Enrolls up to the server's per-call bound (25) of `(address, networkId)` pairs. Per-pair
     * failures (e.g. an unknown `networkId`) are reported by the server without failing the batch;
     * callers must chunk sets larger than the bound.
     */
    suspend fun subscribe(pairs: List<MonitoringSubscription>): ResultResponse<MonitoringSubscribeResult>
}
