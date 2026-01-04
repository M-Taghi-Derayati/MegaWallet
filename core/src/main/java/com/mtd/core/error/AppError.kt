package com.mtd.core.error


sealed class AppError : Exception() {
    // خطاهای شبکه
    sealed class Network : AppError() {
        data object NoInternet : Network()
        data object Timeout : Network()
        data object ServerUnavailable : Network()
        data class Unknown(val originalException: Throwable) : Network()
    }

    // خطاهای بیزنس (منطقی)
    sealed class Business : AppError() {
        data object InsufficientFunds : Business()
        data object InvalidAddress : Business()
        data class General(val messageResId: Int? = null, override val message: String? = null) : Business()
    }

    // خطای ناشناخته
    data class Unexpected(val originalException: Throwable) : AppError()
}
