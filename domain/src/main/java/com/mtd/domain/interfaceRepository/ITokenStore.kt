package com.mtd.domain.interfaceRepository

/**
 * Phase 1 — minimal persistence seam for the Web3 session JWT + attested deviceId.
 *
 * Phase 1 only *reads* this (the [com.mtd.domain] consumer is the AuthInterceptor). The
 * challenge → verify → refresh flow that *writes* it is Phase 3 (Growth/gas-credit need it).
 * Backed by the existing AndroidKeystore-encrypted SecureStorage.
 */
interface ITokenStore {

    /** The current Bearer JWT, or null if the user has no session. */
    fun getTokenDevice(): String?

    /** The attested Play Integrity deviceId claim embedded at /verify, or null. */
    fun getDeviceId(): String?

    /** True if a token exists and has not expired (with a small skew margin). */
    fun isTokenValid(nowEpochSec: Long): Boolean

    /** Absolute expiry (epoch seconds) of the stored session, or null if there is none. */
    fun getExpiresAtEpochSec(): Long?

    /** Persist a freshly minted/refreshed session. */
    fun save(token: String, expiresAtEpochSec: Long, deviceId: String?)

    /** Drop the session (logout / 401). */
    fun clear()
}
