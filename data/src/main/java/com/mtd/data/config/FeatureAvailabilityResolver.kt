package com.mtd.data.config

import com.mtd.domain.interfaceRepository.ICapabilityProvider
import com.mtd.domain.interfaceRepository.IFeatureAvailabilityResolver
import com.mtd.domain.model.capability.AvailabilitySource
import com.mtd.domain.model.capability.CapabilitySnapshot
import com.mtd.domain.model.capability.FeatureAvailability
import com.mtd.domain.model.capability.FeatureAvailabilityContext
import com.mtd.domain.model.capability.FeatureIds
import com.mtd.domain.model.capability.FeatureReasonCodes
import com.mtd.domain.model.capability.NetworkCapability
import com.mtd.domain.model.core.NetworkType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Capability Platform (Android Migration, Phase A) — implements [IFeatureAvailabilityResolver].
 *
 * Its ONLY collaborator is [ICapabilityProvider]; every per-token/per-user signal is
 * supplied by the caller via [FeatureAvailabilityContext], so this class never calls
 * `/tokens`, the gasless repositories, or UnifiedTransferCoordinator, and has no
 * coupling to DIRECT/PROXY transport or transaction execution.
 *
 * Decision precedence (safe + additive):
 *  1. Universal client rule (e.g. gasless requires a contract token).
 *  2. If the capability snapshot is AUTHORITATIVE for the network → it decides
 *     (this is how a network with no relayer, e.g. BSC, is correctly hidden), then
 *     a known per-token/per-user deny can still veto.
 *  3. If capability is UNKNOWN / offline → fall back to today's legacy behavior so
 *     existing gasless/sponsor flows are never broken.
 *
 * Pure (no IO beyond the single capability read); the per-feature resolve functions
 * are deterministic over (snapshot, context) and individually unit-tested.
 */
@Singleton
class FeatureAvailabilityResolver @Inject constructor(
    private val capabilityProvider: ICapabilityProvider
) : IFeatureAvailabilityResolver {

    override suspend fun isGaslessAvailable(context: FeatureAvailabilityContext): FeatureAvailability =
        resolveGasless(capabilityProvider.getCapabilities(), context)

    override suspend fun isSponsorAvailable(context: FeatureAvailabilityContext): FeatureAvailability =
        resolveSponsor(capabilityProvider.getCapabilities(), context)

    override suspend fun isSwapAvailable(context: FeatureAvailabilityContext): FeatureAvailability =
        resolveSwap(capabilityProvider.getCapabilities(), context)

    // --- gasless -------------------------------------------------------------
    private fun resolveGasless(snapshot: CapabilitySnapshot, ctx: FeatureAvailabilityContext): FeatureAvailability {
        // 1) Universal rule: gasless is a contract-token feature.
        if (!ctx.isContractToken) {
            return unavailable(FeatureIds.GASLESS, FeatureReasonCodes.NATIVE_TOKEN_NOT_SUPPORTED, AvailabilitySource.CLIENT_RULE)
        }
        val net = authoritativeNetwork(snapshot, ctx.networkId)
        if (net != null) {
            // 2) Capability is authoritative.
            if (!net.gasless.available) {
                return unavailable(FeatureIds.GASLESS, net.gasless.reasonCode ?: FeatureReasonCodes.SERVICE_DOWN, AvailabilitySource.CAPABILITY, net.relayPrefix)
            }
            tokenOrEligibilityVeto(FeatureIds.GASLESS, ctx, ctx.tokenGaslessEnabled, net.relayPrefix)?.let { return it }
            return available(FeatureIds.GASLESS, AvailabilitySource.CAPABILITY, ctx.tokenNote, net.relayPrefix)
        }
        // 3) Capability unknown → legacy fallback (today's behavior).
        return legacyGasless(ctx)
    }

    private fun legacyGasless(ctx: FeatureAvailabilityContext): FeatureAvailability {
        if (!isRelayableNetworkType(ctx.networkType)) {
            return unavailable(FeatureIds.GASLESS, FeatureReasonCodes.NETWORK_TYPE_UNSUPPORTED, AvailabilitySource.FALLBACK_LEGACY)
        }
        return when (ctx.tokenGaslessEnabled) {
            true -> available(FeatureIds.GASLESS, AvailabilitySource.FALLBACK_LEGACY, ctx.tokenNote)
            false -> unavailable(FeatureIds.GASLESS, FeatureReasonCodes.TOKEN_DISABLED, AvailabilitySource.FALLBACK_LEGACY, note = ctx.tokenNote)
            null -> unavailable(FeatureIds.GASLESS, FeatureReasonCodes.CAPABILITY_UNKNOWN, AvailabilitySource.FALLBACK_LEGACY)
        }
    }

    // --- sponsor (depends on gasless) ---------------------------------------
    private fun resolveSponsor(snapshot: CapabilitySnapshot, ctx: FeatureAvailabilityContext): FeatureAvailability {
        val gasless = resolveGasless(snapshot, ctx)
        if (!gasless.available) {
            return unavailable(FeatureIds.SPONSOR, FeatureReasonCodes.DEPENDENCY_DOWN, gasless.source, gasless.relayPrefix)
        }
        val net = authoritativeNetwork(snapshot, ctx.networkId)
        if (net != null) {
            if (!net.sponsor.available) {
                return unavailable(FeatureIds.SPONSOR, net.sponsor.reasonCode ?: FeatureReasonCodes.SERVICE_DOWN, AvailabilitySource.CAPABILITY, net.relayPrefix)
            }
            if (ctx.tokenSponsorEnabled == false) {
                return unavailable(FeatureIds.SPONSOR, FeatureReasonCodes.TOKEN_DISABLED, AvailabilitySource.TOKEN_POLICY, net.relayPrefix)
            }
            return available(FeatureIds.SPONSOR, AvailabilitySource.CAPABILITY, ctx.tokenNote, net.relayPrefix)
        }
        return when (ctx.tokenSponsorEnabled) {
            true -> available(FeatureIds.SPONSOR, AvailabilitySource.FALLBACK_LEGACY, ctx.tokenNote)
            false -> unavailable(FeatureIds.SPONSOR, FeatureReasonCodes.TOKEN_DISABLED, AvailabilitySource.FALLBACK_LEGACY)
            null -> unavailable(FeatureIds.SPONSOR, FeatureReasonCodes.CAPABILITY_UNKNOWN, AvailabilitySource.FALLBACK_LEGACY)
        }
    }

    // --- swap (always-backend; no token rule) -------------------------------
    private fun resolveSwap(snapshot: CapabilitySnapshot, ctx: FeatureAvailabilityContext): FeatureAvailability {
        val net = authoritativeNetwork(snapshot, ctx.networkId)
        val swap = net?.feature(FeatureIds.SWAP)
        // Only subtract when capability authoritatively MODELS swap for this network;
        // otherwise preserve today's always-available behavior.
        if (swap != null) {
            return if (swap.available) available(FeatureIds.SWAP, AvailabilitySource.CAPABILITY, relayPrefix = net.relayPrefix)
            else unavailable(FeatureIds.SWAP, swap.reasonCode ?: FeatureReasonCodes.SERVICE_DOWN, AvailabilitySource.CAPABILITY, net.relayPrefix)
        }
        return available(FeatureIds.SWAP, AvailabilitySource.FALLBACK_LEGACY)
    }

    // --- helpers -------------------------------------------------------------

    /** The network from the snapshot ONLY when the snapshot is a real (non-empty) response. */
    private fun authoritativeNetwork(snapshot: CapabilitySnapshot, networkId: String): NetworkCapability? {
        if (snapshot.isEmpty) return null
        return snapshot.network(networkId)
    }

    /** A known per-token or per-user deny that vetoes a capability-available feature. */
    private fun tokenOrEligibilityVeto(
        featureId: String,
        ctx: FeatureAvailabilityContext,
        tokenEnabled: Boolean?,
        relayPrefix: String?
    ): FeatureAvailability? {
        if (tokenEnabled == false) {
            return unavailable(featureId, FeatureReasonCodes.TOKEN_DISABLED, AvailabilitySource.TOKEN_POLICY, relayPrefix, ctx.tokenNote)
        }
        if (ctx.eligibilityAllowed == false) {
            return unavailable(
                featureId,
                ctx.eligibilityReasonCode ?: FeatureReasonCodes.ELIGIBILITY_DENIED,
                AvailabilitySource.TOKEN_POLICY,
                relayPrefix,
                ctx.eligibilityReasonFa
            )
        }
        return null
    }

    private fun isRelayableNetworkType(type: NetworkType?): Boolean =
        type == NetworkType.EVM || type == NetworkType.TVM

    private fun available(featureId: String, source: AvailabilitySource, note: String? = null, relayPrefix: String? = null) =
        FeatureAvailability(featureId, available = true, reasonCode = FeatureReasonCodes.OK, source = source, note = note, relayPrefix = relayPrefix)

    private fun unavailable(featureId: String, reasonCode: String, source: AvailabilitySource, relayPrefix: String? = null, note: String? = null) =
        FeatureAvailability(featureId, available = false, reasonCode = reasonCode, source = source, note = note, relayPrefix = relayPrefix)
}
