package com.mtd.data.device

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2 — default production [PlayIntegrityTokenProvider] for this build/market.
 *
 * The target market is dominated by network-restricted and non-GMS devices where Play Integrity
 * cannot be relied upon, and the API additionally requires a configured `cloudProjectNumber`. Rather
 * than block legitimate users, this binding always reports Play Integrity as unavailable, which routes
 * [ResilientDeviceIdProvider] to its deterministic local-hash fallback. Swap this binding for a real
 * GMS-backed implementation later without touching [ResilientDeviceIdProvider] or the auth flow.
 */
@Singleton
class UnavailablePlayIntegrityTokenProvider @Inject constructor() : PlayIntegrityTokenProvider {

    override suspend fun requestIntegrityToken(): String =
        throw PlayIntegrityUnavailableException()
}

/** Signals that Play Integrity could not be obtained — always caught and replaced by the local fallback. */
class PlayIntegrityUnavailableException(
    message: String = "Play Integrity unavailable on this device/build"
) : Exception(message)
