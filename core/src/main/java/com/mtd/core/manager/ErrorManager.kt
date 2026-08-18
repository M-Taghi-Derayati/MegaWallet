package com.mtd.core.manager

import com.mtd.domain.model.error.AppError
import com.mtd.domain.model.error.ErrorMapper
import com.mtd.domain.model.error.ErrorPresentation
import com.mtd.domain.model.error.ErrorSurface
import com.mtd.domain.model.ui.UiEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدیریت متمرکز خطاها در کل اپلیکیشن
 * همه خطاها باید از طریق این Manager handle شوند
 *
 * TASK-57 — this is also the single **app-wide UI message bus**. ViewModels never render; they
 * report here, and exactly one host per Activity (`AppMessageHost`) collects [uiMessages] and draws
 * the top snackbar / detail dialog / blocking dialog. That is why the flow lives on a `@Singleton`
 * rather than per-ViewModel: previously every `BaseViewModel` mirrored the stream into its own
 * channel, and only two onboarding screens ever collected one — so every error raised anywhere in
 * the main app was silently dropped.
 *
 * Severity policy lives in [ErrorSurface]; each call site declares its own.
 */
@Singleton
class ErrorManager @Inject constructor(
    private val errorMapper: ErrorMapper,
    private val networkManager: NetworkManager
) {
    // extraBufferCapacity so non-suspending emitters (tryEmit) never drop a message while the
    // host is recomposing; replay = 0 so a message is not re-shown on Activity recreation.
    private val _uiMessages = MutableSharedFlow<UiEvent>(replay = 0, extraBufferCapacity = 16)

    /** Everything the user should see. Collected by `AppMessageHost` at the Activity root. */
    val uiMessages = _uiMessages.asSharedFlow()

    /**
     * بررسی وضعیت آنلاین بودن
     */
    fun isOnline(): Boolean = networkManager.isOnline()

    /**
     * بررسی اتصال به اینترنت و نمایش Snackbar در صورت قطع بودن
     * @return true اگر وصل باشد، false اگر قطع باشد (و اسنک‌بار نمایش داده شود)
     */
    suspend fun ensureOnline(surface: ErrorSurface = ErrorSurface.SNACKBAR): Boolean {
        if (!isOnline()) {
            report(
                throwable = AppError.Network.NoInternet,
                context = ErrorContext(component = "ErrorManager", userAction = "Connectivity Check"),
                surface = surface,
                severity = ErrorSeverity.LOW
            )
            return false
        }
        return true
    }

    /**
     * هندل کردن خطا و تصمیم‌گیری در مورد action مناسب
     *
     * Maps [throwable] to curated Persian copy, logs the original at [severity], and surfaces it
     * according to [surface]. Returns the mapped [AppError] so a caller can branch on the taxonomy
     * (never on the message text).
     */
    suspend fun report(
        throwable: Throwable,
        context: ErrorContext,
        surface: ErrorSurface = ErrorSurface.SNACKBAR,
        severity: ErrorSeverity = ErrorSeverity.MEDIUM,
        title: String = ErrorMapper.DEFAULT_TITLE,
        fallbackMessage: String? = null
    ): AppError {
        val presentation = errorMapper.present(throwable, surface, title, fallbackMessage)
        logError(presentation, context, severity, throwable)
        emit(presentation)
        return errorMapper.map(throwable)
    }

    /** Legacy entry point kept for existing call sites; snackbar unless the failure is critical. */
    suspend fun handleError(
        throwable: Throwable,
        context: ErrorContext,
        severity: ErrorSeverity = ErrorSeverity.MEDIUM
    ): AppError = report(
        throwable = throwable,
        context = context,
        surface = if (severity == ErrorSeverity.CRITICAL) ErrorSurface.BLOCKING else ErrorSurface.SNACKBAR,
        severity = severity
    )

    /**
     * نمایش snackbar ساده
     *
     * [message] must already be user-facing Persian copy — never an exception or DTO string.
     */
    suspend fun showSnackbar(message: String, detail: String = "") {
        _uiMessages.emit(
            UiEvent.ShowErrorSnackbar(
                shortMessage = message,
                detailedMessage = detail
            )
        )
    }

    /** TASK-57 — success-styled confirmation. Auto-dismisses; carries no technical detail. */
    suspend fun showSuccess(message: String) {
        _uiMessages.emit(UiEvent.ShowSuccessSnackbar(message))
    }

    /** Non-suspending variant for callbacks that cannot suspend (e.g. clipboard taps). */
    fun showSuccessNow(message: String) {
        _uiMessages.tryEmit(UiEvent.ShowSuccessSnackbar(message))
    }

    /** Blocking modal the user must acknowledge. */
    suspend fun showBlockingDialog(
        title: String,
        message: String,
        positiveButton: String = "تایید",
        negativeButton: String? = null
    ) {
        _uiMessages.emit(
            UiEvent.ShowDialog(
                title = title,
                message = message,
                positiveButton = positiveButton,
                negativeButton = negativeButton
            )
        )
    }

    private suspend fun emit(presentation: ErrorPresentation) {
        when (presentation.surface) {
            // Logged above and nothing else — a failed background refresh must not nag the user.
            ErrorSurface.SILENT -> Unit

            ErrorSurface.SNACKBAR -> _uiMessages.emit(
                UiEvent.ShowErrorSnackbar(
                    shortMessage = presentation.shortMessage,
                    detailedMessage = presentation.technicalDetail,
                    errorTitle = presentation.title,
                    reasons = presentation.reasons
                )
            )

            // همان قرصِ همیشگی، فقط بی‌تایمر.
            //
            // ⚠️ پیش‌تر این شاخه یک `ShowDialog` می‌ساخت و یک مودالِ متریال وسطِ صفحه می‌آمد — که
            // نه با بقیهٔ برنامه هم‌شکل بود و نه دلایل را نشان می‌داد. اهمیتِ این سطح در «باید
            // دیده شود» است، نه در «باید مودال باشد»، و [UiEvent.ShowErrorSnackbar.sticky] همان
            // را تأمین می‌کند: تا کاربر نبندد نمی‌رود.
            //
            // `ShowDialog` هنوز وجود دارد ولی فقط برای [showBlockingDialog] — جایی که واقعاً
            // انتخابی با دکمه‌های بله/خیر پیشِ رویِ کاربر گذاشته می‌شود، نه صرفاً خبرِ یک شکست.
            ErrorSurface.BLOCKING -> _uiMessages.emit(
                UiEvent.ShowErrorSnackbar(
                    shortMessage = presentation.shortMessage,
                    detailedMessage = presentation.technicalDetail,
                    errorTitle = presentation.title,
                    reasons = presentation.reasons,
                    sticky = true
                )
            )
        }
    }

    /**
     * Log کردن خطا بر اساس severity
     *
     * The original throwable goes to Timber (stack traces stay in logcat); the message we print is
     * the already-scrubbed technical detail so nothing sensitive lands in a persisted log sink.
     */
    private fun logError(
        presentation: ErrorPresentation,
        context: ErrorContext,
        severity: ErrorSeverity,
        originalThrowable: Throwable
    ) {
        val line = "${context.component}: ${context.userAction} — ${presentation.technicalDetail}"
        when (severity) {
            ErrorSeverity.CRITICAL -> {
                Timber.e(originalThrowable, "CRITICAL Error in $line")
                // در آینده می‌توان crash reporter اضافه کرد
            }
            ErrorSeverity.HIGH -> Timber.e(originalThrowable, "HIGH Error in $line")
            ErrorSeverity.MEDIUM -> Timber.w(originalThrowable, "MEDIUM Error in $line")
            ErrorSeverity.LOW -> Timber.d(originalThrowable, "LOW Error in $line")
        }
    }
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
 * سطح اهمیت خطا — درجه‌ی لاگ. برای اینکه *چطور* به کاربر نشان داده شود به
 * [com.mtd.domain.model.error.ErrorSurface] مراجعه کنید.
 */
enum class ErrorSeverity {
    LOW,      // خطاهای جزئی که نیاز به action ندارند
    MEDIUM,   // خطاهای معمولی
    HIGH,     // خطاهای مهم که نیاز به توجه دارند
    CRITICAL  // خطاهای بحرانی که نیاز به action فوری دارند
}
