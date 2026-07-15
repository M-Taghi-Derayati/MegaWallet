package com.mtd.data.network.interceptor

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.security.MessageDigest
import java.util.UUID

/**
 * Injects an idempotency key on mutating relayer POSTs that don't already carry one
 * (contract invariant #4). Covers:
 *   - `…/relay`           → `X-Idempotency-Key`
 *   - `…/transactions/broadcast` → `X-Idempotency-Key`
 *   - `…/sponsor-approve` → `X-Idempotency-Key` (gas-funding POST — must not double-fund on retry)
 *   - `…/swap/prepare`    → `x-idempotency-key`
 *
 * **TASK-07 / TD-31 — the key is derived from the request CONTENT**, not a fresh random UUID per
 * physical request. The server contract is "same key + same payload = same result": a client
 * resubmit after an ambiguous failure (timeout where the request may already have been processed)
 * carries a **byte-identical** payload → the same key → the server dedupes it instead of
 * double-broadcasting / double-funding. Distinct operations differ in their signed payload (each
 * carries a unique on-chain nonce), so they naturally get distinct keys. Previously a new
 * `UUID.randomUUID()` per attempt defeated this exact protection.
 *
 * A random UUID is used only as a **fallback** when no stable content is available (no body, a
 * one-shot body that can't be re-read, or a hashing failure).
 *
 * Host-scoped to [relayerHost]. Header lookup is case-insensitive (OkHttp), so the gasless
 * coordinator's per-session `x-idempotency-key` on `/relay` is preserved (not overwritten).
 * [fallbackKeyGenerator] is injectable for deterministic tests.
 */
class IdempotencyInterceptor(
    private val relayerHost: String,
    private val fallbackKeyGenerator: () -> String = { UUID.randomUUID().toString() }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        val isMutating = request.method == "POST" &&
            request.url.host.equals(relayerHost, ignoreCase = true) &&
            (path.endsWith("/relay") || path.endsWith("/swap/prepare") ||
                path.endsWith("/broadcast") || path.endsWith("/sponsor-approve"))

        if (!isMutating) return chain.proceed(request)
        if (request.header(HEADER_CAMEL) != null || request.header(HEADER_LOWER) != null) {
            return chain.proceed(request)
        }

        val key = contentKey(request) ?: fallbackKeyGenerator()
        val headerName = if (path.endsWith("/swap/prepare")) HEADER_LOWER else HEADER_CAMEL
        return chain.proceed(
            request.newBuilder().header(headerName, key).build()
        )
    }

    /**
     * SHA-256 over `method + encodedPath + body bytes`. Stable across retries of the same logical
     * operation; unique across operations (the signed body embeds a unique nonce). Returns null when
     * a stable key can't be derived (no body / one-shot / failure) so the caller falls back to UUID.
     */
    private fun contentKey(request: Request): String? {
        val body = request.body ?: return null
        if (body.isOneShot()) return null
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            val digest = MessageDigest.getInstance("SHA-256").apply {
                update(request.method.toByteArray(Charsets.UTF_8))
                update(request.url.encodedPath.toByteArray(Charsets.UTF_8))
                update(buffer.readByteArray())
            }.digest()
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val HEADER_CAMEL = "X-Idempotency-Key"
        const val HEADER_LOWER = "x-idempotency-key"
    }
}
