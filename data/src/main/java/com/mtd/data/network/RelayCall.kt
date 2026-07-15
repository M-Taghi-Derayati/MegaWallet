package com.mtd.data.network

import com.google.gson.JsonParser
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import retrofit2.Response

/**
 * Phase 4 — error mapping for the relayer's non-BM-33 JSON routes (Growth, Swap, Notifications),
 * which use the `{ ok:false, error:{ code, message } }` (or flat `{ code, message }`) shape rather
 * than the per-network [com.mtd.data.dto.ProxyEnvelope]. Mirrors the gasless gateway's parser so all
 * three new repositories branch on the machine `error.code` (contract invariant #3) and never on the
 * Persian `message`. HTTP status + `Retry-After` are fallbacks (e.g. 409 → RaceConditionLock/Requote,
 * 429 → RateLimited).
 */
internal fun relayApiError(response: Response<*>, fallbackMessage: String): ApiException {
    val httpStatus = response.code()
    val retryAfter = response.headers()["Retry-After"]?.trim()?.toLongOrNull()
    val (code, message) = parseRelayErrorBody(response.errorBody()?.string())
    return ApiException(
        apiError = ApiError.from(code, httpStatus, retryAfter),
        httpStatus = httpStatus,
        reasonFa = message ?: fallbackMessage,
        retryAfterSec = retryAfter
    )
}

/** Pulls (`code`, `message`) from a relayer error body, tolerating both the enveloped and flat shapes. */
private fun parseRelayErrorBody(raw: String?): Pair<String?, String?> {
    val body = raw?.takeIf { it.isNotBlank() } ?: return null to null
    return try {
        val root = JsonParser.parseString(body).asJsonObject
        val error = root.takeIf { it.has("error") && it.get("error").isJsonObject }
            ?.getAsJsonObject("error")
        val code = (error?.get("code") ?: root.get("code"))?.takeUnless { it.isJsonNull }?.asString
        val message = (error?.get("message") ?: root.get("message") ?: root.get("reasonFa"))
            ?.takeUnless { it.isJsonNull }?.asString
        code to message
    } catch (e: Exception) {
        null to null
    }
}
