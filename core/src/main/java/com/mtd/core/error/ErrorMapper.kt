package com.mtd.core.error

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorMapper {
    fun map(throwable: Throwable): AppError {
        return when (throwable) {
            is AppError -> throwable // اگر خودش از قبل AppError بود
            is UnknownHostException -> AppError.Network.NoInternet
            is SocketTimeoutException -> AppError.Network.Timeout
            is IOException -> AppError.Network.NoInternet // سایر خطاهای IO معمولاً قطعی نت هستند
            is HttpException -> {
                when (throwable.code()) {
                    in 500..599 -> AppError.Network.ServerUnavailable
                    else -> AppError.Network.Unknown(throwable)
                }
            }
            else -> AppError.Unexpected(throwable)
        }
    }
    
    // متدی برای گرفتن پیام کاربرپسند (می‌تواند بعداً به String Resource تبدیل شود)
    fun getUserMessage(error: AppError): String {
        return when (error) {
            is AppError.Network.NoInternet -> "اتصال اینترنت برقرار نیست."
            is AppError.Network.Timeout -> "پاسخی از سرور دریافت نشد. لطفاً دوباره تلاش کنید."
            is AppError.Network.ServerUnavailable -> "سرور موقتاً در دسترس نیست."
            is AppError.Business.InsufficientFunds -> "موجودی کافی نیست."
            is AppError.Business.InvalidAddress -> "آدرس وارد شده معتبر نیست."
            else -> "خطای ناشناخته رخ داد."
        }
    }
}
