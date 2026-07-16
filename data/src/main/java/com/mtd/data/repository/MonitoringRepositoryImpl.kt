package com.mtd.data.repository

import com.mtd.data.dto.MonitoringAddressDto
import com.mtd.data.dto.MonitoringSubscribeRequestDto
import com.mtd.data.service.MobileProxyApiService
import com.mtd.domain.interfaceRepository.IMonitoringRepository
import com.mtd.domain.model.MonitoringSubscribeResult
import com.mtd.domain.model.MonitoringSubscription
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TASK-32 — batch monitoring enrollment against the mobile proxy
 * (`POST /api/mobile/v1/monitoring/subscribe`). Durable + idempotent: puts each `(address, networkId)`
 * into the backend's monitored set so realtime `tx.new` / `balance.invalidated` signals + deposit FCM
 * fire for ALL wallets, not just the one whose history screen was opened.
 *
 * NOT BM-33-enveloped — the response is `{ ok, subscribed, total, results }` directly, so this does
 * not use [com.mtd.data.network.proxyCall]. Per-pair failures come back in `results[]` without failing
 * the batch; they're logged (not surfaced) so a bad `networkId` is visible on-device.
 */
@Singleton
class MonitoringRepositoryImpl @Inject constructor(
    private val proxyApi: MobileProxyApiService
) : IMonitoringRepository {

    override suspend fun subscribe(
        pairs: List<MonitoringSubscription>
    ): ResultResponse<MonitoringSubscribeResult> {
        if (pairs.isEmpty()) {
            return ResultResponse.Success(MonitoringSubscribeResult(subscribed = 0, total = 0))
        }
        return try {
            val body = MonitoringSubscribeRequestDto(
                addresses = pairs.map { MonitoringAddressDto(address = it.address, networkId = it.networkId) }
            )
            val response = proxyApi.monitoringSubscribe(body)
            val payload = response.body()
            if (response.isSuccessful && payload != null && payload.ok) {
                payload.results
                    ?.filterNot { it.ok }
                    ?.forEach { failed ->
                        Timber.w(
                            "[Monitoring] pair not enrolled: %s/%s — %s",
                            failed.networkId, failed.address, failed.error
                        )
                    }
                Timber.i("[Monitoring] subscribed ${payload.subscribed}/${payload.total} pairs")
                ResultResponse.Success(
                    MonitoringSubscribeResult(subscribed = payload.subscribed, total = payload.total)
                )
            } else {
                Timber.w("[Monitoring] subscribe failed http=${response.code()} ok=${payload?.ok}")
                ResultResponse.Error(
                    ApiException(ApiError.from(null, response.code()), httpStatus = response.code())
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[Monitoring] subscribe transport failure")
            ResultResponse.Error(ApiException(ApiError.Unknown(null, e.message), cause = e))
        }
    }
}
