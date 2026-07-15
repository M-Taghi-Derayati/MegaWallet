package com.mtd.domain.model.capability

import com.mtd.domain.model.core.NetworkType

/**
 * Capability Platform (Android Migration, Phase A) — PURE domain types for the
 * Feature Availability seam. This is the single place that will eventually decide
 * "should the UI offer gasless / sponsor / swap here?", by combining the
 * backend-authoritative capability snapshot with the existing per-token / per-user
 * eligibility signals.
 *
 * Framework-free. This layer makes a DECISION only — it never sends, prepares, or
 * relays transactions, and it does not gate the wallet core.
 */

/** Stable feature identifiers (mirror the backend featureIds). */
object FeatureIds {
    const val GASLESS = "gasless"
    const val SPONSOR = "sponsor"
    const val SWAP = "swap"
}

/** Where the final decision came from (useful for diagnostics + UI copy). */
enum class AvailabilitySource {
    /** Backend capability snapshot was authoritative for this network. */
    CAPABILITY,
    /** A per-token / per-user policy signal decided it. */
    TOKEN_POLICY,
    /** A client-side universal rule (e.g. gasless needs a contract token). */
    CLIENT_RULE,
    /** Capability was unknown/offline → preserved today's legacy behavior. */
    FALLBACK_LEGACY
}

/**
 * Machine-branchable reason codes. The first block mirrors the backend taxonomy
 * (services/capability/reasonCodes.js); the rest are client-specific. Branch on the
 * CODE, never on [FeatureAvailability.note] (which is human/Persian copy).
 */
object FeatureReasonCodes {
    const val OK = "OK"
    const val NETWORK_NOT_SUPPORTED = "NETWORK_NOT_SUPPORTED"
    const val TOKEN_DISABLED = "TOKEN_DISABLED"
    const val MAINTENANCE = "MAINTENANCE"
    const val SERVICE_DOWN = "SERVICE_DOWN"
    const val DEPENDENCY_DOWN = "DEPENDENCY_DOWN"
    const val ROLLOUT_CLOSED = "ROLLOUT_CLOSED"

    // Client-side reasons:
    const val NATIVE_TOKEN_NOT_SUPPORTED = "NATIVE_TOKEN_NOT_SUPPORTED"
    const val NETWORK_TYPE_UNSUPPORTED = "NETWORK_TYPE_UNSUPPORTED"
    const val ELIGIBILITY_DENIED = "ELIGIBILITY_DENIED"
    /** Capability snapshot unknown AND no local signal to confirm availability. */
    const val CAPABILITY_UNKNOWN = "CAPABILITY_UNKNOWN"
}

/**
 * Per-call inputs. The caller (a future SendViewModel wiring) supplies the signals it
 * ALREADY fetches today — so this resolver neither calls `/tokens` nor the transfer
 * coordinator. `null` means "not known / not fetched".
 */
data class FeatureAvailabilityContext(
    val networkId: String,
    /** From the local registry; drives the legacy fallback rule (gasless ⊂ EVM/TVM). */
    val networkType: NetworkType? = null,
    /** Token contract address; `null` = native coin. */
    val tokenId: String? = null,
    val isContractToken: Boolean = !tokenId.isNullOrBlank(),
    // Existing per-token signals (from GET /api/{chain}/tokens):
    val tokenGaslessEnabled: Boolean? = null,
    val tokenSponsorEnabled: Boolean? = null,
    val tokenNote: String? = null,
    // Existing per-user eligibility signals (from POST /api/{chain}/eligibility):
    val eligibilityAllowed: Boolean? = null,
    val eligibilityReasonCode: String? = null,
    val eligibilityReasonFa: String? = null
)

/** The final UI decision for one feature on one (network, token, user) tuple. */
data class FeatureAvailability(
    val featureId: String,
    val available: Boolean,
    val reasonCode: String,
    val source: AvailabilitySource,
    /** Optional human/Persian copy for the UI (never branch on this). */
    val note: String? = null,
    /** Endpoint-discovery hint from capability (canonical lowercase relay prefix), if known. */
    val relayPrefix: String? = null
)
