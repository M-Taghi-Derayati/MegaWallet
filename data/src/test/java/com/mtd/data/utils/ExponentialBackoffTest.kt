package com.mtd.data.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExponentialBackoffTest {

    @Test
    fun `delays double per attempt then saturate at the cap`() {
        val backoff = ExponentialBackoff(baseDelayMs = 1_000L, maxDelayMs = 8_000L)

        assertEquals(1_000L, backoff.nextDelayMs())  // attempt 0
        assertEquals(2_000L, backoff.nextDelayMs())  // attempt 1
        assertEquals(4_000L, backoff.nextDelayMs())  // attempt 2
        assertEquals(8_000L, backoff.nextDelayMs())  // attempt 3 (cap)
        assertEquals(8_000L, backoff.nextDelayMs())  // attempt 4 (still capped)
    }

    @Test
    fun `reset returns the sequence to the base delay`() {
        val backoff = ExponentialBackoff(baseDelayMs = 500L, maxDelayMs = 10_000L)
        backoff.nextDelayMs()
        backoff.nextDelayMs()
        backoff.reset()
        assertEquals(500L, backoff.peekDelayMs())
        assertEquals(500L, backoff.nextDelayMs())
    }

    @Test
    fun `delayForAttempt is pure and overflow-safe for large attempts`() {
        // A huge attempt index must saturate at the cap, never wrap or go negative.
        val delay = ExponentialBackoff.delayForAttempt(attempt = 1_000, baseDelayMs = 1_000L, maxDelayMs = 30_000L)
        assertEquals(30_000L, delay)
        assertTrue(delay > 0)
    }

    @Test
    fun `delayForAttempt clamps negative attempt to base`() {
        assertEquals(1_000L, ExponentialBackoff.delayForAttempt(attempt = -5, baseDelayMs = 1_000L, maxDelayMs = 30_000L))
    }
}
