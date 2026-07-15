package com.mtd.core.notification

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 5 — cross-transport event de-duplication.
 *
 * The same backend event (`tx.status.changed`, `balance.updated`, `growth.fee_share.accrued`) can
 * arrive over BOTH the realtime WebSocket and an FCM push within a short window. The API contract is
 * explicit: **dedupe on the event `id`/`eventId`**. This is a lightweight, in-memory, thread-safe
 * cache that remembers recently seen ids for a strict TTL and lets the first arrival through.
 *
 * Both the WebSocket listener and the FCM handler call [shouldProcess] before propagating an event
 * to the domain/notification layer. Whichever transport wins the race processes the event; the loser
 * is silently dropped while the id is still within [ttlMillis].
 *
 * Memory is bounded: every call opportunistically prunes expired entries, so the map only ever holds
 * ids seen within the last [ttlMillis].
 */
@Singleton
class EventDeduplicationCache @Inject constructor() {

    /** eventId → epoch-millis when first seen. */
    private val seen = ConcurrentHashMap<String, Long>()

    /** Strict 5-second window — matches the WS-vs-FCM delivery skew. */
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS

    /** Pluggable clock so the TTL behavior is unit-testable without real time. */
    private val clock: () -> Long = { System.currentTimeMillis() }

    /**
     * Returns `true` exactly once per [eventId] within the TTL window, recording it as the winner.
     * Subsequent calls with the same id inside the window return `false` (duplicate → drop).
     *
     * A null/blank id cannot be de-duplicated, so it always returns `true` (process it).
     */
    fun shouldProcess(eventId: String?): Boolean {
        val now = clock()
        pruneExpired(now)

        val id = eventId?.takeIf { it.isNotBlank() } ?: return true

        // putIfAbsent is atomic: only the first caller for this id gets null back.
        val previous = seen.putIfAbsent(id, now)
        if (previous == null) return true

        // An entry exists but may be stale (pruneExpired races with concurrent inserts).
        return if (now - previous >= ttlMillis) {
            seen[id] = now
            true
        } else {
            false
        }
    }

    /** Drop everything (e.g. on logout) so a re-login starts with a clean window. */
    fun clear() {
        seen.clear()
    }

    private fun pruneExpired(now: Long) {
        if (seen.isEmpty()) return
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value >= ttlMillis) {
                iterator.remove()
            }
        }
    }

    private companion object {
        const val DEFAULT_TTL_MILLIS = 5_000L
    }
}
