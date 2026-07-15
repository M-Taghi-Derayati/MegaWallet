package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.capability.CapabilitySnapshot
import com.mtd.domain.model.capability.NetworkCapability

/**
 * Capability Platform (Android Migration, Step 1) — the PURE domain seam for
 * server-driven feature availability.
 *
 * Contract guarantees (so Server-Enhanced Mode can never destabilise Local Wallet Mode):
 *  - **Never throws.** A backend that is offline, slow, or returning errors resolves to
 *    the last-known snapshot, or [CapabilitySnapshot.EMPTY] when nothing is known.
 *  - **Offline-first.** Implementations serve cache and revalidate cheaply (ETag), so a
 *    capability lookup is fast and works without connectivity.
 *  - **Advisory only.** This describes what optional features to *offer*; it does not gate
 *    or perform any wallet-core flow (keys, signing, send/receive, history).
 *
 * The implementation lives in the data layer ([com.mtd.data.config.CapabilityManager]);
 * the wallet core depends only on this interface (and may not depend on it at all).
 */
interface ICapabilityProvider {

    /** Latest capability snapshot. Offline-first and fail-safe — returns [CapabilitySnapshot.EMPTY] if unknown. */
    suspend fun getCapabilities(): CapabilitySnapshot

    /** Convenience lookup for one network; [NetworkCapability.unavailable] when unknown. */
    suspend fun getNetworkCapability(networkId: String): NetworkCapability
}
