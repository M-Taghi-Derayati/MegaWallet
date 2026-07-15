package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

/**
 * Phase 3 — Web3 challenge/verify auth (`/api/auth/...`).
 *
 * Flow: client asks for a [AuthChallengeResponseDto] for its (address, chain), signs the returned
 * `message`, and posts the signature to `/verify`, receiving a bearer JWT persisted by the
 * AuthInterceptor seam ([com.mtd.domain.interfaceRepository.ITokenStore]).
 */
data class AuthChallengeRequestDto(
    @SerializedName("address") val address: String,
    @SerializedName("chain") val chain: String
)

data class AuthChallengeResponseDto(
    @SerializedName("address") val address: String?,
    @SerializedName("chain") val chain: String?,
    @SerializedName("nonce") val nonce: String?,
    /** Human-readable message the wallet must sign. */
    @SerializedName("message") val message: String?
)

data class AuthVerifyRequestDto(
    @SerializedName("address") val address: String,
    @SerializedName("chain") val chain: String,
    @SerializedName("signature") val signature: String,
    @SerializedName("deviceId") val deviceId: String? = null,
    /** Single-use challenge nonce from `/api/auth/device-challenge`. */
    @SerializedName("nonce") val nonce: String? = null,
    /**
     * Two-level device attestation (see [com.mtd.core.crypto.HmacUtils.generateDeviceAttestation]):
     * `deviceKey = HMAC-SHA256(DEVICE_ATTEST_HMAC_SECRET, deviceId)`, then
     * `HMAC-SHA256(deviceKey, "$nonce-$deviceId")` (lowercase hex). Omitted on the baseline
     * (device-less) path; only device-bound features (gas credit, Mystery Box) require it.
     */
    @SerializedName("attestationSignature") val attestationSignature: String? = null
)

data class AuthVerifyResponseDto(
    @SerializedName("token") val token: String?,
    @SerializedName("expiresInSec") val expiresInSec: Long?,
    @SerializedName("scope") val scope: List<String>?,
    @SerializedName("deviceId") val deviceId: String?,
    /**
     * True only when the backend cryptographically attested the device (HMAC device-challenge path).
     * Absent / false on baseline login. Gates device-bound features (gas credit, Mystery Box).
     */
    @SerializedName("deviceVerified") val deviceVerified: Boolean? = false
)
