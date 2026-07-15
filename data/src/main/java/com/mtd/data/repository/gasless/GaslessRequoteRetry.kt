package com.mtd.data.repository.gasless

import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import timber.log.Timber

/**
 * True when [this] carries the typed 409 RequoteRequired error (treasury / chainId / discount drifted
 * between prepare and quote/relay). Branches on the machine [ApiError] subtype only — never on the
 * Persian message (contract invariant #3).
 */
internal fun ResultResponse<*>.isRequoteRequired(): Boolean =
    this is ResultResponse.Error &&
        (exception as? ApiException)?.apiError == ApiError.RequoteRequired

/**
 * Sprint 3 — bounded requote driver shared by [EvmGaslessCoordinator] and [TronGaslessCoordinator].
 *
 * On a 409 RequoteRequired the prepareToken/nonce/treasury are stale, so we MUST re-run BOTH prepare
 * AND quote — not just quote. [reprepare] rebuilds the session from a fresh `/prepare` (new prepareToken,
 * nonce and a new idempotency key so the subsequent relay payload can't collide); [attempt] then
 * re-quotes, re-signs and relays with that refreshed session.
 *
 * Capped at [maxRequotes] (default ONE) so a backend stuck returning 409 can never spin us into an
 * infinite prepare/quote loop. We retry ONLY on RequoteRequired — every other typed error (notably
 * [ApiError.InsufficientGasCredit], [ApiError.GasCreditUnavailable], [ApiError.RaceConditionLock]) is
 * surfaced verbatim to the caller. There is NO silent feeFundingSource=WALLET fallback: [reprepare]
 * re-prepares the SAME request, preserving the user's funding choice.
 */
internal suspend fun <S, T> withBoundedRequote(
    initialSession: S,
    maxRequotes: Int = 1,
    label: String = "gasless",
    reprepare: suspend (S) -> ResultResponse<S>,
    attempt: suspend (S) -> ResultResponse<T>
): ResultResponse<T> {
    var current = initialSession
    var requotes = 0
    while (true) {
        val result = attempt(current)
        if (!result.isRequoteRequired() || requotes >= maxRequotes) {
            return result
        }
        requotes++
        Timber.w("[$label] RequoteRequired (409) — re-running prepare+quote ($requotes/$maxRequotes)")
        when (val reprepared = reprepare(current)) {
            is ResultResponse.Success -> current = reprepared.data
            is ResultResponse.Error -> {
                Timber.e(reprepared.exception, "[$label] re-prepare failed during bounded requote")
                return reprepared
            }
        }
    }
}
