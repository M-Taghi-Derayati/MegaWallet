package com.mtd.data.repository.notification

import com.mtd.data.dto.NotificationStatusResponseDto
import com.mtd.data.dto.RegisterDeviceRequestDto
import com.mtd.data.network.relayApiError
import com.mtd.data.service.NotificationApiService
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.interfaceRepository.INotificationRepository
import com.mtd.domain.model.DeviceRegistration
import com.mtd.domain.model.NotificationStatus
import com.mtd.domain.model.ResultResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4 — Push notifications (`/api/notifications`). Unwraps the relayer's `{ ok, … }` responses;
 * failures map to the typed [com.mtd.domain.model.error.ApiException] via [relayApiError]. Register/
 * unregister rely on the bearer JWT (identity) attached by the AuthInterceptor.
 */
@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationApiService: NotificationApiService
) : INotificationRepository {

    override suspend fun getStatus(): ResultResponse<NotificationStatus> = safeApiCall {
        val response = notificationApiService.getStatus()
        val body = response.body()
        if (!response.isSuccessful || body == null) throw relayApiError(response, "notification status failed (${response.code()})")
        body.toDomain()
    }

    override suspend fun registerDevice(registration: DeviceRegistration): ResultResponse<Unit> = safeApiCall {
        val response = notificationApiService.registerDevice(
            RegisterDeviceRequestDto(
                fcmToken = registration.fcmToken,
                platform = registration.platform,
                appVersion = registration.appVersion,
                locale = registration.locale
            )
        )
        if (!response.isSuccessful) throw relayApiError(response, "notification register failed (${response.code()})")
        Unit
    }

    override suspend fun unregisterDevice(fcmToken: String): ResultResponse<Unit> = safeApiCall {
        val response = notificationApiService.unregisterDevice(fcmToken)
        if (!response.isSuccessful) throw relayApiError(response, "notification unregister failed (${response.code()})")
        Unit
    }

    private fun NotificationStatusResponseDto.toDomain(): NotificationStatus {
        val d = data
        return NotificationStatus(
            webSocketAvailable = d?.websocket?.available ?: false,
            webSocketPath = d?.websocket?.path,
            pushAvailable = d?.push?.available ?: false,
            pollingFallbackRecommended = d?.pollingFallbackRecommended ?: false
        )
    }
}
