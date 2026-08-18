package com.mtd.megawallet.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtd.core.manager.ErrorContext
import com.mtd.core.manager.ErrorManager
import com.mtd.core.manager.ErrorSeverity
import com.mtd.domain.model.error.AppError
import com.mtd.domain.model.error.ErrorMapper
import com.mtd.domain.model.error.ErrorSurface
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * TASK-57 — error reporting contract for every ViewModel.
 *
 * Never build a user-facing string from an exception. Call [reportError] with the [ErrorSurface]
 * that matches the situation (see the policy table on [ErrorSurface]); it maps the failure to
 * curated Persian copy, tucks the scrubbed technical text inside the expanded snackbar, and logs.
 * For a message you have already written yourself, use [showErrorSnackbar] / [showSuccess].
 *
 * Messages are emitted on the app-wide [ErrorManager.uiMessages] bus, not on a per-ViewModel
 * channel — a single `AppMessageHost` per Activity renders them.
 */
abstract class BaseViewModel(
    protected val errorManager: ErrorManager
) : ViewModel() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        handleException(throwable)
    }

    /**
     * Reports a failure through the shared pipeline.
     *
     * @param surface how loudly to surface it. SILENT still logs — use it for background work.
     * @param fallbackMessage Persian copy describing what the user was doing; shown only when the
     *   error taxonomy has nothing more specific (see [ErrorMapper.present]).
     */
    protected suspend fun reportError(
        throwable: Throwable,
        userAction: String? = null,
        surface: ErrorSurface = ErrorSurface.SNACKBAR,
        severity: ErrorSeverity = ErrorSeverity.MEDIUM,
        title: String = ErrorMapper.DEFAULT_TITLE,
        fallbackMessage: String? = null
    ): AppError = errorManager.report(
        throwable = throwable,
        context = ErrorContext(
            component = this::class.simpleName ?: "Unknown",
            userAction = userAction
        ),
        surface = surface,
        severity = severity,
        title = title,
        fallbackMessage = fallbackMessage
    )

    /**
     * Curated Persian copy for a failure that is written into **UI state** (an error field on a
     * state object) rather than pushed through the snackbar pipeline. Never returns raw exception
     * text. Usually paired with a [reportError] call so the failure is also logged/surfaced.
     */
    protected fun userMessageFor(throwable: Throwable, fallbackMessage: String? = null): String =
        ErrorMapper.userMessage(throwable, fallbackMessage)

    /** Fire-and-forget variant of [reportError] for non-suspending call sites. */
    protected fun reportErrorAsync(
        throwable: Throwable,
        userAction: String? = null,
        surface: ErrorSurface = ErrorSurface.SNACKBAR,
        severity: ErrorSeverity = ErrorSeverity.MEDIUM,
        title: String = ErrorMapper.DEFAULT_TITLE,
        fallbackMessage: String? = null
    ) {
        launchLocal { reportError(throwable, userAction, surface, severity, title, fallbackMessage) }
    }

    /**
     * @param connectivitySurface how the "no internet" gate reports itself. Background work should
     *   pass [ErrorSurface.SILENT] so a dropped connection does not produce a snackbar per tick.
     */
    protected fun launchSafe(
        checkNetwork: Boolean = true,
        connectivitySurface: ErrorSurface = ErrorSurface.SNACKBAR,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return viewModelScope.launch(exceptionHandler) {
            if (checkNetwork && !errorManager.ensureOnline(connectivitySurface)) {
                return@launch
            }
            block()
        }
    }

    protected fun launchLocal(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(exceptionHandler) {
            block()
        }
    }

    /** Anything that escapes a coroutine is by definition unexpected — always surfaced. */
    private fun handleException(throwable: Throwable) {
        viewModelScope.launch {
            reportError(
                throwable = throwable,
                userAction = "Uncaught exception",
                surface = ErrorSurface.SNACKBAR,
                severity = ErrorSeverity.HIGH
            )
        }
    }

    /**
     * Shows an error snackbar from copy **you wrote**. [shortMessage] must be user-facing Persian;
     * put anything technical in [detailedMessage], which is rendered only once the user opens the
     * snackbar.
     */
    protected suspend fun showErrorSnackbar(
        shortMessage: String,
        detailedMessage: String = "",
        errorTitle: String = ErrorMapper.DEFAULT_TITLE
    ) {
        errorManager.showSnackbar(shortMessage, detailedMessage)
    }

    protected fun showSnackbarMessage(message: String) {
        launchLocal { showErrorSnackbar(message) }
    }

    /** TASK-57 — success-styled confirmation snackbar (green, auto-dismissing). */
    protected suspend fun showSuccess(message: String) {
        errorManager.showSuccess(message)
    }

    protected fun showSuccessMessage(message: String) {
        launchLocal { showSuccess(message) }
    }
}
