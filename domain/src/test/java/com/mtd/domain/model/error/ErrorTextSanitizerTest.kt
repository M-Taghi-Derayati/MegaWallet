package com.mtd.domain.model.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK-57 / TASK-25 — a non-custodial wallet must never print an address, key, hash or signed
 * payload into an error string. These cases are drawn from the shapes the app actually handles.
 */
class ErrorTextSanitizerTest {

    private fun assertRedacted(secret: String, raw: String = "upstream said: $secret") {
        val out = ErrorTextSanitizer.sanitize(raw)
        assertFalse("leaked <$secret> from <$raw>", out.contains(secret))
        assertTrue("no redaction marker in <$out>", out.contains(ErrorTextSanitizer.REDACTED))
    }

    @Test
    fun `redacts EVM addresses, hashes and signed payloads`() {
        assertRedacted("0x000000000022D473030F116dDEE9F6B43aC78BA3")
        assertRedacted("0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
        assertRedacted("0X1234ABCD5678EF90")
    }

    @Test
    fun `redacts bare hex blobs such as raw signatures and private keys`() {
        assertRedacted("4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318")
        assertRedacted("f86c808504a817c800825208940123456789abcdef0123456789abcdef01234567")
    }

    @Test
    fun `redacts Tron base58 addresses`() {
        assertRedacted("TN3W4H6rK2ce4vX9YnFQHwKENnHjoxb3m9")
    }

    @Test
    fun `redacts Bitcoin and Dogecoin base58 addresses`() {
        assertRedacted("1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2")
        assertRedacted("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy")
        assertRedacted("DH5yaieqoZN36fDVciNyRueRGvGLR3mr7L")
    }

    @Test
    fun `redacts bech32 addresses`() {
        assertRedacted("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq")
    }

    @Test
    fun `redacts a BIP-39 mnemonic`() {
        val mnemonic =
            "legal winner thank year wave sausage worth useful legal winner thank yellow"
        assertRedacted(mnemonic, "derivation failed for $mnemonic")
    }

    @Test
    fun `redacts bearer tokens`() {
        assertRedacted("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dBjftJeZ4CVPmB92K27uhbUJU1p1r")
    }

    @Test
    fun `keeps ordinary Persian and diagnostic text intact`() {
        val raw = "ارتباط با سرور برقرار نشد (HTTP 502)"
        assertEquals(raw, ErrorTextSanitizer.sanitize(raw))
    }

    @Test
    fun `keeps short numbers and status codes intact`() {
        val raw = "status 429, retry after 30s, chainId 56"
        assertEquals(raw, ErrorTextSanitizer.sanitize(raw))
    }

    @Test
    fun `blank input yields an empty string`() {
        assertEquals("", ErrorTextSanitizer.sanitize(null))
        assertEquals("", ErrorTextSanitizer.sanitize(""))
        assertEquals("", ErrorTextSanitizer.sanitize("   "))
    }

    @Test
    fun `clamps a runaway message to dialog size`() {
        val out = ErrorTextSanitizer.sanitize("x".repeat(5_000))
        assertTrue("not clamped: ${out.length}", out.length <= 401)
        assertTrue(out.endsWith("…"))
    }

    @Test
    fun `redacts every secret when a message carries several`() {
        val out = ErrorTextSanitizer.sanitize(
            "transfer 0x1234567890abcdef -> TN3W4H6rK2ce4vX9YnFQHwKENnHjoxb3m9 failed"
        )
        assertFalse(out.contains("0x1234567890abcdef"))
        assertFalse(out.contains("TN3W4H6rK2ce4vX9YnFQHwKENnHjoxb3m9"))
        // REDACTED contains regex metacharacters, so count by splitting rather than matching.
        assertEquals(2, out.split(ErrorTextSanitizer.REDACTED).size - 1)
    }
}
