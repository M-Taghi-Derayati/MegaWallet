package com.mtd.domain.model.error

/**
 * Phase 1 — machine-readable error taxonomy for the relayer/proxy API.
 *
 * The API contract is explicit (invariant #3): **branch on the machine `code`, never on the
 * human `message`** — most messages are Persian (Farsi). This sealed type is the typed
 * destination for the BM-33 envelope's `error.code`, the gasless/relay `code`, and the swap
 * `error.code`. UI maps a localized message from [ApiException.reasonFa]; control flow keys
 * on the subtype here.
 */
sealed class ApiError {

    // ── Mobile proxy (BM-33) ────────────────────────────────────────────────
    data object NetworkNotFound : ApiError()           // 404 NETWORK_NOT_FOUND
    data object AssetNotFound : ApiError()             // 404 ASSET_NOT_FOUND
    data object ValidationError : ApiError()           // 400 VALIDATION_ERROR
    data object InvalidAddress : ApiError()            // 400 INVALID_ADDRESS
    data object InvalidSignedTx : ApiError()           // 400 INVALID_SIGNED_TX
    data object UnsupportedOperation : ApiError()      // 400 UNSUPPORTED_OPERATION
    data object SimulationReverted : ApiError()        // 422 SIMULATION_REVERTED
    data object NetworkFamilyUnsupported : ApiError()  // 501 NETWORK_FAMILY_UNSUPPORTED
    data object UpstreamUnavailable : ApiError()       // 502 UPSTREAM_UNAVAILABLE
    data object BroadcastRejected : ApiError()         // 502 BROADCAST_REJECTED
    data object InternalError : ApiError()             // 500 INTERNAL_ERROR

    // ── Cross-cutting (relay / swap / growth) ───────────────────────────────
    data object RaceConditionLock : ApiError()         // 409 RACE_CONDITION_LOCK
    data object InsufficientGasCredit : ApiError()     // 400 INSUFFICIENT_GAS_CREDIT / INSUFFICIENT_CREDIT
    data object RequoteRequired : ApiError()           // 409 on relay/quote (treasury/chainId/discount changed)
    data object IdempotencyKeyConflict : ApiError()    // 409 IDEMPOTENCY_KEY_CONFLICT (same key, different payload)
    data class RateLimited(val retryAfterSec: Long?) : ApiError()  // 429
    data object ServiceUnavailable : ApiError()        // 503 (intake paused, readiness fail)
    data object GasCreditUnavailable : ApiError()      // 503 GAS_CREDIT_UNAVAILABLE (gas-credit pool exhausted → fall back to WALLET/SPONSOR)

    // ── Growth / Mystery Box (device-bound) ─────────────────────────────────
    data object MysteryBoxAlreadyOpened : ApiError()   // 409 MYSTERY_BOX_ALREADY_OPENED
    data object MysteryBoxNotFound : ApiError()        // 404 MYSTERY_BOX_NOT_FOUND
    data object DeviceRequired : ApiError()            // 401 DEVICE_REQUIRED (missing deviceId JWT claim)
    data object DeviceRewardLimitExceeded : ApiError() // 429 DEVICE_REWARD_LIMIT_EXCEEDED (>5 opens/device/hour)

    // ── Swap aggregator ─────────────────────────────────────────────────────
    data object SwapNoRoutes : ApiError()              // 422 SWAP_NO_ROUTES

    /** Anything we don't yet have a typed case for — carries the raw code for logging/telemetry. */
    data class Unknown(val code: String?, val rawMessage: String?) : ApiError()

    companion object {
        /**
         * Map a server `code` (+ HTTP status as a fallback discriminator) to a typed [ApiError].
         * Pure & deterministic so it can be unit-tested without any network.
         */
        fun from(code: String?, httpStatus: Int, retryAfterSec: Long? = null): ApiError {
            return when (code?.uppercase()) {
                "NETWORK_NOT_FOUND", "SWAP_NETWORK_NOT_FOUND" -> NetworkNotFound
                "ASSET_NOT_FOUND", "SWAP_ASSET_NOT_FOUND" -> AssetNotFound
                "VALIDATION_ERROR", "SWAP_VALIDATION_ERROR" -> ValidationError
                "INVALID_ADDRESS" -> InvalidAddress
                "INVALID_SIGNED_TX" -> InvalidSignedTx
                "UNSUPPORTED_OPERATION" -> UnsupportedOperation
                "SIMULATION_REVERTED", "SWAP_SIMULATION_FAILED" -> SimulationReverted
                "NETWORK_FAMILY_UNSUPPORTED" -> NetworkFamilyUnsupported
                "UPSTREAM_UNAVAILABLE" -> UpstreamUnavailable
                "BROADCAST_REJECTED" -> BroadcastRejected
                "RACE_CONDITION_LOCK" -> RaceConditionLock
                "INSUFFICIENT_GAS_CREDIT", "INSUFFICIENT_CREDIT" -> InsufficientGasCredit
                "GAS_CREDIT_UNAVAILABLE" -> GasCreditUnavailable
                "QUOTE_EXPIRED",
                "PREPARE_TOKEN_EXPIRED",
                "QUOTE_TOKEN_EXPIRED",
                "PREPARE_TOKEN_INVALID",
                "QUOTE_TOKEN_INVALID" -> RequoteRequired
                "IDEMPOTENCY_KEY_CONFLICT" -> IdempotencyKeyConflict
                "MYSTERY_BOX_ALREADY_OPENED" -> MysteryBoxAlreadyOpened
                "MYSTERY_BOX_NOT_FOUND" -> MysteryBoxNotFound
                "DEVICE_REQUIRED" -> DeviceRequired
                "DEVICE_REWARD_LIMIT_EXCEEDED" -> DeviceRewardLimitExceeded
                "SWAP_NO_ROUTES" -> SwapNoRoutes
                "INTERNAL_ERROR", "SWAP_ENGINE_ERROR" -> InternalError
                else -> when (httpStatus) {
                    409 -> RequoteRequired
                    422 -> SimulationReverted
                    429 -> RateLimited(retryAfterSec)
                    503 -> ServiceUnavailable
                    in 500..599 -> InternalError
                    else -> Unknown(code, null)
                }
            }
        }
    }
}

/**
 * The single exception type carried inside [com.mtd.domain.model.ResultResponse.Error] for all
 * relayer/proxy failures. Downstream code does `(error as? ApiException)?.apiError` and `when`s on
 * the typed [ApiError]; it shows [reasonFa] (already localized by the server) to the user.
 */
class ApiException(
    val apiError: ApiError,
    val httpStatus: Int? = null,
    /** Server-provided human message — frequently Persian. For display only, never for branching. */
    val reasonFa: String? = null,
    val retryAfterSec: Long? = null,
    cause: Throwable? = null
) : Exception("ApiException(code=$apiError, http=$httpStatus, msg=$reasonFa)", cause)
