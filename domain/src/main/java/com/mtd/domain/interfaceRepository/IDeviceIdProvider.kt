package com.mtd.domain.interfaceRepository

/**
 * Phase 2 — supplies the `deviceId` string sent in the `/api/auth/verify` request.
 *
 * Implementations MUST be resilient on the target market (network restrictions, non-GMS devices):
 * they attempt a strong attestation (Play Integrity) first and fall back to a deterministic,
 * locally-derived device identifier when it is unavailable. Either way the result is a plain String
 * that maps into the **existing** `deviceId` field — no new request fields are introduced.
 */
interface IDeviceIdProvider {

    /** Best-available device identifier: Play Integrity token if obtainable, else a deterministic local hash. */
    suspend fun getDeviceId(): String
}
