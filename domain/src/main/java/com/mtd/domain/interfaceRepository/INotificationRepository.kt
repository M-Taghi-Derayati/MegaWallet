package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.DeviceRegistration
import com.mtd.domain.model.NotificationStatus
import com.mtd.domain.model.ResultResponse

/**
 * Phase 4 — Push notifications (`/api/notifications`). [getStatus] drives the startup WS-vs-polling
 * decision; register/unregister manage the FCM token (identity from the JWT). Failures map to the
 * typed [com.mtd.domain.model.error.ApiException].
 */
interface INotificationRepository {

    /** WS + push capability for the WS-vs-polling decision (no auth). */
    suspend fun getStatus(): ResultResponse<NotificationStatus>

    /** Register/refresh this device's FCM token. */
    suspend fun registerDevice(registration: DeviceRegistration): ResultResponse<Unit>

    /** Unregister a token (logout / opt-out). */
    suspend fun unregisterDevice(fcmToken: String): ResultResponse<Unit>
}
