package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.capability.FeatureAvailability
import com.mtd.domain.model.capability.FeatureAvailabilityContext

/**
 * Capability Platform (Android Migration, Phase A) — the PURE domain seam that
 * decides whether an optional, backend-enhanced feature should be OFFERED in the UI.
 *
 * It combines the backend-authoritative capability snapshot ([ICapabilityProvider])
 * with the per-token / per-user signals the app already fetches (passed in via
 * [FeatureAvailabilityContext]).
 *
 * Hard boundaries (this is a DECISION layer only):
 *  - Does NOT send / prepare / quote / relay transactions.
 *  - Does NOT replace the gasless repositories or UnifiedTransferCoordinator.
 *  - Does NOT touch DIRECT/PROXY transport or the wallet core.
 *  - Fail-safe: when capability is unknown/offline it preserves today's legacy behavior.
 */
interface IFeatureAvailabilityResolver {

    suspend fun isGaslessAvailable(context: FeatureAvailabilityContext): FeatureAvailability

    suspend fun isSponsorAvailable(context: FeatureAvailabilityContext): FeatureAvailability

    suspend fun isSwapAvailable(context: FeatureAvailabilityContext): FeatureAvailability
}
