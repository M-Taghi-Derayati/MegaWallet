package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

/**
 * Phase 2 — request body for `POST /api/auth/device-challenge`.
 *
 * Carries the resilient hardware [deviceId] so the backend can mint a single-use [DeviceChallengeResponse]
 * nonce bound to that device, closing the Device ID spoofing gap before `/verify`.
 */
data class DeviceChallengeRequest(
    @SerializedName("deviceId") val deviceId: String
)
