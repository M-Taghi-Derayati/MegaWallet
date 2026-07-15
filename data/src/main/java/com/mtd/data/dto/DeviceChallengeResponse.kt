package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

/**
 * Phase 2 — response body for `POST /api/auth/device-challenge`.
 *
 * [nonce] is the single-use challenge the client folds into the attestation HMAC (`"$nonce-$deviceId"`),
 * while [attestationMessage] is the human-readable echo the backend associates with the challenge.
 */
data class DeviceChallengeResponse(
    @SerializedName("nonce") val nonce: String,
    @SerializedName("attestationMessage") val attestationMessage: String
)
