package com.mtd.domain.model

import com.mtd.domain.model.error.AppError


class GaslessApiException(
    val statusCode: Int,
    val responseBody: String?,
    override val message: String
) : Exception(message)

enum class GaslessErrorCategory {
    VALIDATION,
    CONFLICT,
    AUTH,
    INFRA,
    UNKNOWN
}

data class GaslessErrorInfo(
    val category: GaslessErrorCategory,
    val httpCode: Int? = null,
    val reason: String? = null
)

object GaslessErrorClassifier {
    fun classify(throwable: Throwable): GaslessErrorInfo {
        return when (throwable) {
            is GaslessApiException -> classifyByHttpCode(throwable.statusCode, throwable.responseBody)
            is AppError.Network -> GaslessErrorInfo(
                category = GaslessErrorCategory.INFRA,
                reason = throwable.message
            )
            else -> GaslessErrorInfo(
                category = GaslessErrorCategory.UNKNOWN,
                reason = throwable.message
            )
        }
    }

    private fun classifyByHttpCode(httpCode: Int, reason: String?): GaslessErrorInfo {
        val category = when (httpCode) {
            400 -> GaslessErrorCategory.VALIDATION
            401, 403 -> GaslessErrorCategory.AUTH
            409 -> GaslessErrorCategory.CONFLICT
            else -> GaslessErrorCategory.UNKNOWN
        }
        return GaslessErrorInfo(category = category, httpCode = httpCode, reason = reason)
    }
}
