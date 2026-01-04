package com.mtd.core.manager

import com.mtd.core.error.AppError
import com.mtd.core.error.ErrorMapper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدیریت متمرکز خطاها در کل اپلیکیشن
 * همه خطاها باید از طریق این Manager handle شوند
 */
@Singleton
class ErrorManager @Inject constructor(
    private val errorMapper: ErrorMapper
) {
    private val _errorEvents = MutableSharedFlow<ErrorEvent>(replay = 0)
    val errorEvents = _errorEvents.asSharedFlow()

    /**
     * هندل کردن خطا و تصمیم‌گیری در مورد action مناسب
     */
    suspend fun handleError(
        throwable: Throwable,
        context: ErrorContext,
        severity: ErrorSeverity = ErrorSeverity.MEDIUM
    ): ErrorHandlingResult {
        // 1. Map error
        val appError = errorMapper.map(throwable)

        // 2. Log error based on severity
        logError(appError, context, severity, throwable)

        // 3. Decide action
        val action = decideAction(appError, context, severity)

        // 4. Emit event
        _errorEvents.emit(ErrorEvent(appError, action, context, severity))

        return ErrorHandlingResult(appError, action)
    }

    /**
     * Log کردن خطا بر اساس severity
     */
    private fun logError(
        error: AppError,
        context: ErrorContext,
        severity: ErrorSeverity,
        originalThrowable: Throwable
    ) {
        when (severity) {
            ErrorSeverity.CRITICAL -> {
                Timber.e(originalThrowable, "CRITICAL Error in ${context.component}: ${context.userAction}")
                // در آینده می‌توان crash reporter اضافه کرد
            }
            ErrorSeverity.HIGH -> {
                Timber.e(originalThrowable, "HIGH Error in ${context.component}: ${context.userAction}")
            }
            ErrorSeverity.MEDIUM -> {
                Timber.w(originalThrowable, "MEDIUM Error in ${context.component}: ${context.userAction}")
            }
            ErrorSeverity.LOW -> {
                Timber.d(originalThrowable, "LOW Error in ${context.component}: ${context.userAction}")
            }
        }
    }

    /**
     * تصمیم‌گیری در مورد action مناسب بر اساس نوع خطا
     */
    private fun decideAction(
        error: AppError,
        context: ErrorContext,
        severity: ErrorSeverity
    ): ErrorAction {
        return when {
            // خطاهای بحرانی - نیاز به action فوری
            severity == ErrorSeverity.CRITICAL -> ErrorAction.ForceLogout
            
            // خطاهای شبکه - نیاز به retry
            error is AppError.Network.NoInternet -> ErrorAction.ShowRetryDialog
            error is AppError.Network.Timeout -> ErrorAction.ShowRetryDialog
            error is AppError.Network.ServerUnavailable -> ErrorAction.ShowRetryDialog
            
            // خطاهای بیزنس - فقط نمایش پیام
            error is AppError.Business.InsufficientFunds -> ErrorAction.ShowSnackbar
            error is AppError.Business.InvalidAddress -> ErrorAction.ShowSnackbar
            error is AppError.Business.General -> ErrorAction.ShowSnackbar
            
            // سایر خطاها
            else -> ErrorAction.ShowSnackbar
        }
    }

    /**
     * نمایش snackbar ساده
     */
    suspend fun showSnackbar(message: String) {
        _errorEvents.emit(
            ErrorEvent(
                error = AppError.Business.General(message = message),
                action = ErrorAction.ShowSnackbar,
                context = ErrorContext(component = "ErrorManager"),
                severity = ErrorSeverity.LOW
            )
        )
    }
}

/**
 * نتیجه هندل کردن خطا
 */
data class ErrorHandlingResult(
    val error: AppError,
    val action: ErrorAction
)

/**
 * رویداد خطا که به UI ارسال می‌شود
 */
data class ErrorEvent(
    val error: AppError,
    val action: ErrorAction,
    val context: ErrorContext,
    val severity: ErrorSeverity
)

/**
 * Action هایی که باید در UI انجام شود
 */
sealed class ErrorAction {
    data object ShowSnackbar : ErrorAction()
    data object ShowRetryDialog : ErrorAction()
    data object ForceLogout : ErrorAction()
    data class NavigateTo(val destination: String) : ErrorAction()
    data class ShowDialog(
        val title: String,
        val message: String,
        val positiveButton: String = "تایید",
        val negativeButton: String? = null
    ) : ErrorAction()
}

/**
 * Context خطا - اطلاعاتی در مورد جایی که خطا رخ داده
 */
data class ErrorContext(
    val component: String,
    val userAction: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * سطح اهمیت خطا
 */
enum class ErrorSeverity {
    LOW,      // خطاهای جزئی که نیاز به action ندارند
    MEDIUM,   // خطاهای معمولی
    HIGH,     // خطاهای مهم که نیاز به توجه دارند
    CRITICAL  // خطاهای بحرانی که نیاز به action فوری دارند
}

