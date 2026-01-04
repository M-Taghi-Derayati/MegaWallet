package com.mtd.data.utils

import com.mtd.core.error.ErrorMapper
import com.mtd.domain.model.ResultResponse
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
    } catch (e: Exception) {
        Timber.e(e, "API call failed")
        val appError = ErrorMapper.map(e)
        ResultResponse.Error(appError)
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
