package com.mtd.data.utils

import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import com.mtd.domain.model.error.AppError
import com.mtd.domain.model.error.ErrorMapper
import retrofit2.HttpException
import timber.log.Timber

/**
 * Wrapper برای همه فراخوانی‌های API که exception را به صورت خودکار handle می‌کند
 */
suspend fun <T> safeApiCall(
    apiCall: suspend () -> T
): ResultResponse<T> {
    return try {
        val result = apiCall()
        ResultResponse.Success(result)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: ApiException) {
        // Already a typed taxonomy error (e.g. from the gasless gateway / proxy) — preserve the
        // ApiError code end-to-end so callers branch on it instead of a flattened AppError.
        if (e.apiError == ApiError.UnsupportedOperation) {
            // Capability negotiation, not a failure: this is how a transport says "that op isn't
            // mine" so the caller can take its own path (DIRECT falls back to per-network history).
            // At ERROR with a stack trace, a working fallback read like a crash.
            Timber.i("API call not supported by this transport: %s", e.message)
        } else {
            Timber.e(e, "API call failed (typed)")
        }
        ResultResponse.Error(e)
    } catch (e: Exception) {
        Timber.e(e, "API call failed")
        val appError = mapDataException(e)
        ResultResponse.Error(appError)
    }
}

private fun mapDataException(exception: Exception): AppError {
    return if (exception is HttpException) {
        when (exception.code()) {
            in 500..599 -> AppError.Network.ServerUnavailable
            else -> AppError.Network.Unknown(exception)
        }
    } else {
        ErrorMapper.map(exception)
    }
}

/**
 * برای استفاده در جایی که ResultResponse را باز می‌کنیم
 */
inline fun <T> ResultResponse<T>.onSuccess(action: (T) -> Unit): ResultResponse<T> {
    if (this is ResultResponse.Success) action(data)
    return this
}

inline fun <T> ResultResponse<T>.onError(action: (Exception) -> Unit): ResultResponse<T> {
    if (this is ResultResponse.Error) action(exception as Exception)
    return this
}
