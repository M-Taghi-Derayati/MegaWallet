package com.mtd.domain.model

/**
 * Phase 3 — Web3 auth domain models for the challenge → verify session flow.
 */
/** The wallet account a Web3 sign-in is performed for (the EVM address + its `"EVM"` chain tag). */
data class AuthAccount(
    val address: String,
    val chain: String
)

data class AuthChallenge(
    val address: String,
    val chain: String,
    val nonce: String,
    /** The message the wallet must sign to prove key ownership. */
    val message: String
)

data class AuthSession(
    val token: String,
    val expiresInSec: Long,
    val expiresAtEpochSec: Long,
    val scope: List<String>?,
    val deviceId: String?,
    /**
     * Whether the backend cryptographically attested this device (HMAC device-challenge path).
     * Baseline login does NOT attest the device, so this is `false` — device-bound features
     * (gas credit, Mystery Box) remain unreachable until the attested path is provisioned.
     * See `docs/BACKEND_GAP_DEVICE_KEY_PROVISIONING.md`.
     */
    val deviceVerified: Boolean = false
)
