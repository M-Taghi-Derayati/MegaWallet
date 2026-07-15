package com.mtd.data.utils

import kotlin.math.pow

/**
 * Sprint 3 — single source of truth for capped exponential backoff across ALL relayer polling/retry
 * paths (gasless tx-status polling, sponsor polling, proxy tx-status polling). Mirrors the schedule the
 * notification socket already uses (delay doubles per attempt, capped at a ceiling) but as a small,
 * reusable unit so every path backs off identically instead of hammering the backend with a fixed
 * interval.
 *
 * Stateful and NOT thread-safe — create one instance per poll/retry loop and [reset] it when a fresh
 * sequence begins. The delay math is pure (no clock / IO), so it is trivially unit-testable via the
 * companion [delayForAttempt].
 */
class ExponentialBackoff(
    private val baseDelayMs: Long,
    private val maxDelayMs: Long,
    private val factor: Double = 2.0
) {
    init {
        require(baseDelayMs > 0) { "baseDelayMs must be > 0" }
        require(maxDelayMs >= baseDelayMs) { "maxDelayMs must be >= baseDelayMs" }
        require(factor >= 1.0) { "factor must be >= 1.0" }
    }

    private var attempt = 0

    /** Reset the sequence back to the first (base) delay. */
    fun reset() {
        attempt = 0
    }

    /** The delay for the current attempt WITHOUT advancing the sequence. */
    fun peekDelayMs(): Long = delayForAttempt(attempt, baseDelayMs, maxDelayMs, factor)

    /** The delay to wait before the next poll/retry, then advance the sequence. */
    fun nextDelayMs(): Long = delayForAttempt(attempt++, baseDelayMs, maxDelayMs, factor)

    companion object {
        /**
         * Pure, deterministic delay for a 0-based [attempt]: `baseDelayMs * factor^attempt`, clamped to
         * `[baseDelayMs, maxDelayMs]`. Overflow-safe — large attempts saturate at [maxDelayMs] rather
         * than wrapping.
         */
        fun delayForAttempt(
            attempt: Int,
            baseDelayMs: Long,
            maxDelayMs: Long,
            factor: Double = 2.0
        ): Long {
            if (attempt <= 0) return baseDelayMs.coerceIn(baseDelayMs, maxDelayMs)
            val scaled = baseDelayMs.toDouble() * factor.pow(attempt)
            if (scaled.isInfinite() || scaled >= maxDelayMs.toDouble()) return maxDelayMs
            return scaled.toLong().coerceIn(baseDelayMs, maxDelayMs)
        }
    }
}
