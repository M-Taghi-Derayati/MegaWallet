package com.mtd.data.network

import com.mtd.data.network.interceptor.IdempotencyInterceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdempotencyInterceptorTest {

    // The fallback is only used when no stable content is available; content-derived keys are the
    // norm, so tests assert the derived behavior rather than this literal.
    private val interceptor = IdempotencyInterceptor("relayer.test") { "fallback-uuid" }

    private fun body(json: String) = json.toRequestBody("application/json".toMediaTypeOrNull())

    private fun post(
        url: String,
        json: String = """{"tx":"0xabc"}""",
        key: String? = null,
        lowerKey: String? = null
    ) = RecordingChain(
        Request.Builder().url(url)
            .apply {
                key?.let { header("X-Idempotency-Key", it) }
                lowerKey?.let { header("x-idempotency-key", it) }
            }
            .post(body(json))
            .build()
    )

    @Test
    fun `adds a content-derived X-Idempotency-Key on relay`() {
        val c = post("http://relayer.test/api/evm/relay")
        interceptor.intercept(c)
        val k = c.proceeded.header("X-Idempotency-Key")
        assertTrue("key present", !k.isNullOrBlank())
        assertNotEquals("derived from content, not the random fallback", "fallback-uuid", k)
    }

    @Test
    fun `key is stable across identical resubmits (dedupe-safe on retry)`() {
        val c1 = post("http://relayer.test/api/mobile/v1/networks/evm/transactions/broadcast")
        val c2 = post("http://relayer.test/api/mobile/v1/networks/evm/transactions/broadcast")
        interceptor.intercept(c1)
        interceptor.intercept(c2)
        assertEquals(
            c1.proceeded.header("X-Idempotency-Key"),
            c2.proceeded.header("X-Idempotency-Key")
        )
    }

    @Test
    fun `key differs for a different payload`() {
        val c1 = post("http://relayer.test/api/evm/sponsor-approve", json = """{"user":"A"}""")
        val c2 = post("http://relayer.test/api/evm/sponsor-approve", json = """{"user":"B"}""")
        interceptor.intercept(c1)
        interceptor.intercept(c2)
        assertNotEquals(
            c1.proceeded.header("X-Idempotency-Key"),
            c2.proceeded.header("X-Idempotency-Key")
        )
    }

    @Test
    fun `adds lowercase x-idempotency-key on swap prepare`() {
        val c = post("http://relayer.test/api/v1/swap/prepare")
        interceptor.intercept(c)
        assertTrue(!c.proceeded.header("x-idempotency-key").isNullOrBlank())
    }

    @Test
    fun `does NOT add on a GET`() {
        val c = RecordingChain(Request.Builder().url("http://relayer.test/api/evm/relay").build())
        interceptor.intercept(c)
        assertNull(c.proceeded.header("X-Idempotency-Key"))
    }

    @Test
    fun `does NOT add on a non-mutating POST`() {
        val c = post("http://relayer.test/api/evm/quote")
        interceptor.intercept(c)
        assertNull(c.proceeded.header("X-Idempotency-Key"))
    }

    @Test
    fun `preserves a caller-supplied key (no duplicate, even cross-case)`() {
        val c = post("http://relayer.test/api/evm/relay", lowerKey = "caller-key")
        interceptor.intercept(c)
        // OkHttp header names are case-insensitive, so headers("X-Idempotency-Key") also matches the
        // caller's lowercase header. Asserting exactly ONE value proves no duplicate was added.
        assertEquals(listOf("caller-key"), c.proceeded.headers("X-Idempotency-Key"))
        assertEquals(listOf("caller-key"), c.proceeded.headers("x-idempotency-key"))
    }

    @Test
    fun `host-scoped - never injects a key for a non-relayer host`() {
        val c = post("http://evil.test/api/evm/relay")
        interceptor.intercept(c)
        assertNull(c.proceeded.header("X-Idempotency-Key"))
    }
}
