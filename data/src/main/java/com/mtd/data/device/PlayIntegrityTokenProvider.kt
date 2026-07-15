package com.mtd.data.device

/**
 * Phase 2 — thin seam over the Google Play Integrity API.
 *
 * Kept as an interface so [ResilientDeviceIdProvider] is decoupled from GMS and fully unit-testable:
 * tests inject a fake that either returns a token or throws to exercise the deterministic fallback.
 * The production binding is [UnavailablePlayIntegrityTokenProvider] until a Play-services-configured
 * implementation (cloudProjectNumber) is wired — on the target market (network restrictions, non-GMS
 * devices) Play Integrity is unreliable by design, so the local fallback is the primary path.
 */
interface PlayIntegrityTokenProvider {

    /**
     * Requests a Play Integrity token. Throws on any failure (GMS missing, timeout, network error) —
     * callers are expected to catch and fall back rather than treat this as fatal.
     */
    suspend fun requestIntegrityToken(): String
}
