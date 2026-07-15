package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

/**
 * Phase 4 — Push notification DTOs (`/api/notifications`). The status response is wrapped in the
 * relayer's `{ ok, data:{…} }` shape; register/unregister return only `{ ok }`.
 */

data class RegisterDeviceRequestDto(
    @SerializedName("fcmToken") val fcmToken: String,
    @SerializedName("platform") val platform: String,
    @SerializedName("appVersion") val appVersion: String? = null,
    @SerializedName("locale") val locale: String? = null
)

data class NotificationOkDto(
    @SerializedName("ok") val ok: Boolean = true
)

data class NotificationStatusResponseDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("data") val data: NotificationStatusDataDto? = null
)

data class NotificationStatusDataDto(
    @SerializedName("websocket") val websocket: NotificationWebSocketDto? = null,
    @SerializedName("push") val push: NotificationPushDto? = null,
    @SerializedName("pollingFallbackRecommended") val pollingFallbackRecommended: Boolean? = null
)

data class NotificationWebSocketDto(
    @SerializedName("available") val available: Boolean? = null,
    @SerializedName("path") val path: String? = null
)

data class NotificationPushDto(
    @SerializedName("available") val available: Boolean? = null
)
