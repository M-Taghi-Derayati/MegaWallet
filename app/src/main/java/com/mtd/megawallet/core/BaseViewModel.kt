package com.mtd.megawallet.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtd.core.error.AppError
import com.mtd.core.error.ErrorMapper
import com.mtd.core.manager.ErrorAction
import com.mtd.core.manager.ErrorContext
import com.mtd.core.manager.ErrorManager
import com.mtd.core.manager.ErrorSeverity
import com.mtd.core.ui.UiEvent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel(
    protected val errorManager: ErrorManager
) : ViewModel() {

    // استفاده از Channel برای one-time events (بهتر از SharedFlow برای UI events)
    private val _uiEvents = Channel<UiEvent>(Channel.UNLIMITED)
    val uiEvents = _uiEvents.receiveAsFlow()

    // CoroutineExceptionHandler برای گرفتن خطاهای handle نشده
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        handleException(throwable)
    }

    init {
        // Observe error events from ErrorManager
        observeErrorEvents()
    }

    /**
     * مشاهده error events از ErrorManager
     */
    private fun observeErrorEvents() {
        viewModelScope.launch {
            errorManager.errorEvents.collect { errorEvent ->
                handleErrorAction(errorEvent.action, errorEvent.error)
            }
        }
    }

    /**
     * هندل کردن action های خطا
     */
    private suspend fun handleErrorAction(action: ErrorAction, error: AppError) {
        when (action) {
            is ErrorAction.ShowSnackbar -> {
                val message = ErrorMapper.getUserMessage(error)
                _uiEvents.send(UiEvent.ShowErrorSnackbar(message))
            }
            is ErrorAction.ShowRetryDialog -> {
                val message = ErrorMapper.getUserMessage(error)
                _uiEvents.send(
                    UiEvent.ShowDialog(
                        title = "خطا",
                        message = "$message\nآیا می‌خواهید دوباره تلاش کنید؟",
                        positiveButton = "تلاش مجدد",
                        negativeButton = "لغو",
                        onPositive = {
                            // Retry logic should be implemented in child ViewModels
                        }
                    )
                )
            }
            is ErrorAction.ForceLogout -> {
                // Force logout logic - should be handled in MainViewModel or Application
                _uiEvents.send(UiEvent.ShowErrorSnackbar("خطای بحرانی. لطفاً دوباره وارد شوید."))
            }
            is ErrorAction.NavigateTo -> {
                _uiEvents.send(UiEvent.Navigate(action.destination))
            }
            is ErrorAction.ShowDialog -> {
                _uiEvents.send(
                    UiEvent.ShowDialog(
                        title = action.title,
                        message = action.message,
                        positiveButton = action.positiveButton,
                        negativeButton = action.negativeButton
                    )
                )
            }
        }
    }

    /**
     * متد امن برای اجرای کوروتین‌ها
     * از این به جای viewModelScope.launch استفاده کنید
     */
    protected fun launchSafe(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(exceptionHandler) {
            block()
        }
    }

    /**
     * هندل کردن خطاهای catch نشده
     */
    private fun handleException(throwable: Throwable) {
        viewModelScope.launch {
            errorManager.handleError(
                throwable = throwable,
                context = ErrorContext(
                    component = this@BaseViewModel::class.simpleName ?: "Unknown",
                    userAction = "Uncaught exception"
                ),
                severity = ErrorSeverity.HIGH
            )
        }
    }

    /**
     * هندل کردن خطا به صورت دستی
     */
    protected suspend fun handleError(
        throwable: Throwable,
        userAction: String? = null,
        severity: ErrorSeverity = ErrorSeverity.MEDIUM
    ) {
        errorManager.handleError(
            throwable = throwable,
            context = ErrorContext(
                component = this::class.simpleName ?: "Unknown",
                userAction = userAction
            ),
            severity = severity
        )
    }

    /**
     * ارسال manual event از ViewModel
     */
    protected suspend fun sendEvent(event: UiEvent) {
        _uiEvents.send(event)
    }

    /**
     * نمایش Error Snackbar کاستوم با پیام کوتاه و شرح کامل
     */
    protected suspend fun showErrorSnackbar(
        shortMessage: String,
        detailedMessage: String="",
        errorTitle: String = "خطا"
    ) {
        _uiEvents.send(
            UiEvent.ShowErrorSnackbar(
                shortMessage = shortMessage,
                detailedMessage = detailedMessage,
                errorTitle = errorTitle
            )
        )
    }


    override fun onCleared() {
        super.onCleared()
        // بستن Channel برای جلوگیری از memory leak
        _uiEvents.close()
    }
}