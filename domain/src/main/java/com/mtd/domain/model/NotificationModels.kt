package com.mtd.domain.model

/**
 * Phase 4 — Push notification domain models (`/api/notifications`).
 *
 * [NotificationStatus] drives the startup WS-vs-polling decision; [pollingFallbackRecommended]
 * tells the client to fall back to smart-polling when realtime delivery is degraded.
 */
data class NotificationStatus(
    val webSocketAvailable: Boolean,
    val webSocketPath: String?,
    val pushAvailable: Boolean,
    val pollingFallbackRecommended: Boolean
)

/** Payload registered/refreshed against the relayer so it can deliver FCM pushes to this install. */
data class DeviceRegistration(
    val fcmToken: String,
    val platform: String = "android",
    val appVersion: String?,
    val locale: String?
)
