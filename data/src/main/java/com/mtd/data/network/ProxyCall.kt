package com.mtd.data.network

import com.google.gson.Gson
import com.mtd.data.dto.ProxyEnvelope
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import timber.log.Timber

/**
 * Executes a mobile-proxy call returning a BM-33 [ProxyEnvelope] and unwraps it into a typed
 * [ResultResponse]. Success → `map(data)`. Any failure (non-2xx, `ok:false`, null data, transport
 * error) → `ResultResponse.Error(ApiException)` whose [ApiException.apiError] is the typed,
 * machine-code-based error. Never leaks a raw [retrofit2.HttpException]; never branches on `message`.
 */
internal suspend fun <T, R> proxyCall(
    call: suspend () -> Response<ProxyEnvelope<T>>,
    map: (T) -> R
): ResultResponse<R> {
    return try {
        val response = call()
        val body = response.body()
        if (response.isSuccessful && body != null && body.ok && body.data != null) {
            ResultResponse.Success(map(body.data))
        } else {
            ResultResponse.Error(ProxyErrorParser.parse(response, body))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        ResultResponse.Error(e)
    } catch (e: Exception) {
        Timber.e(e, "proxyCall transport failure")
        ResultResponse.Error(ApiException(ApiError.Unknown(null, e.message), cause = e))
    }
}

/** Builds an [ApiException] from a (possibly error) proxy [Response] + parsed envelope. */
internal object ProxyErrorParser {

    private val gson = Gson()

    fun parse(response: Response<*>, body: ProxyEnvelope<*>?): ApiException {
        val http = response.code()
        val retryAfter = response.headers()["Retry-After"]?.trim()?.toLongOrNull()
        // Prefer the parsed envelope (2xx + ok:false); else parse the raw errorBody (non-2xx).
        val errorDto = body?.error ?: parseErrorBody(response)
        val code = errorDto?.code
        val reasonFa = errorDto?.message
        return ApiException(
            apiError = ApiError.from(code, http, retryAfter),
            httpStatus = http,
            reasonFa = reasonFa,
            retryAfterSec = retryAfter
        )
    }

    private fun parseErrorBody(response: Response<*>): com.mtd.data.dto.ProxyErrorDto? {
        return try {
            val raw = response.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
            gson.fromJson(raw, ProxyEnvelope::class.java)?.error
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse proxy errorBody")
            null
        }
    }
}
