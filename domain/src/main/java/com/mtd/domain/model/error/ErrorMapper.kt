package com.mtd.domain.model.error

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * TASK-57 — the single place a `Throwable` becomes something a user may see.
 *
 * Contract: [getUserMessage] always returns curated Persian copy, never a raw exception or DTO
 * string. Technical text is produced separately by [getTechnicalDetail], PII-scrubbed by
 * [ErrorTextSanitizer], and only ever rendered inside the expanded snackbar. [present] bundles both
 * with an [ErrorSurface] so a call site declares *how loud* the failure is in one place.
 */
object ErrorMapper {

    /** Fallback copy for anything we cannot name. Deliberately actionable, never technical. */
    const val GENERIC_MESSAGE = "خطای ناشناخته رخ داد. لطفاً دوباره تلاش کنید."

    const val DEFAULT_TITLE = "خطا"

    fun map(throwable: Throwable): AppError {
        return when (throwable) {
            is AppError -> throwable // اگر خودش از قبل AppError بود
            // Phase 8 (KAN-20/21): typed relayer/proxy errors → curated user-facing taxonomy.
            is ApiException -> ApiErrorMessageMapper.toAppError(throwable)
            is UnknownHostException -> AppError.Network.NoInternet
            is SocketTimeoutException -> AppError.Network.Timeout
            is IOException -> AppError.Network.NoInternet // سایر خطاهای IO معمولاً قطعی نت هستند
            else -> AppError.Unexpected(throwable)
        }
    }

    // متدی برای گرفتن پیام کاربرپسند (می‌تواند بعداً به String Resource تبدیل شود)
    fun getUserMessage(error: AppError): String {
        return when (error) {
            is AppError.Network.NoInternet -> "اتصال اینترنت برقرار نیست."
            is AppError.Network.Timeout -> "پاسخی از سرور دریافت نشد. لطفاً دوباره تلاش کنید."
            is AppError.Network.ServerUnavailable -> "سرور موقتاً در دسترس نیست."
            is AppError.Network.Unknown -> "ارتباط با شبکه برقرار نشد. لطفاً دوباره تلاش کنید."
            is AppError.Business.InsufficientFunds -> "موجودی کافی نیست."
            is AppError.Business.InvalidAddress -> "آدرس وارد شده معتبر نیست."
            // Curated copy already resolved upstream (see ApiErrorMessageMapper.toAppError) or a
            // hand-written Persian message from a call site. Sanitized defensively: the Unknown
            // bucket falls back to the server's own text, which we do not control.
            is AppError.Business.General ->
                ErrorTextSanitizer.sanitize(error.message).ifBlank { GENERIC_MESSAGE }
            is AppError.Unexpected -> GENERIC_MESSAGE
        }
    }

    /** Convenience overload — maps then resolves in one step. */
    fun getUserMessage(throwable: Throwable): String = getUserMessage(map(throwable))

    /**
     * The one rule for turning a failure into display copy: prefer what the taxonomy knows, and
     * only fall back to the call site's own Persian description of the action when it doesn't.
     * Use this anywhere a message is written into UI state instead of the snackbar pipeline.
     */
    fun userMessage(throwable: Throwable, fallbackMessage: String? = null): String {
        val mapped = getUserMessage(map(throwable))
        return if (mapped == GENERIC_MESSAGE && !fallbackMessage.isNullOrBlank()) {
            fallbackMessage
        } else {
            mapped
        }
    }

    /**
     * Technical text for the expanded snackbar and the log. Carries the machine code / exception type
     * so a support conversation is possible, with every address, key, hash and payload redacted.
     */
    fun getTechnicalDetail(throwable: Throwable): String {
        val cause = unwrap(throwable)

        // شاخهٔ Business خطای واقعی زیرِ خودش ندارد؛ پیامش از قبل همان جملهٔ کاربرپسند است.
        // برگرداندنِ «نوع: General» به‌عنوانِ جزئیاتِ فنی فقط همان جمله را تکرار می‌کرد — و حالا
        // که وجودِ جزئیات تعیین می‌کند اسنک‌بار باز بشود یا نه، قرص را بی‌دلیل باز شدنی می‌کند.
        // خطاهای تایپ‌دارِ سرور از این شاخه رد نمی‌شوند: آن‌ها `ApiException` می‌مانند.
        if (cause is AppError.Business) return ""

        val parts = mutableListOf<String>()

        if (cause is ApiException) {
            parts += "کد: ${ApiErrorMessageMapper.technicalCode(cause.apiError)}"
            cause.httpStatus?.let { parts += "وضعیت: $it" }
            cause.retryAfterSec?.let { parts += "تلاش مجدد پس از $it ثانیه" }
            ErrorTextSanitizer.sanitize(cause.reasonFa)
                .takeIf { it.isNotBlank() }
                ?.let { parts += it }
        } else {
            cause::class.simpleName
                ?.takeIf { it.isNotBlank() }
                ?.let { parts += "نوع: $it" }
            ErrorTextSanitizer.sanitize(cause.message)
                .takeIf { it.isNotBlank() }
                ?.let { parts += it }
        }

        return parts.joinToString("\n")
    }

    /**
     * Bundles the curated message, the scrubbed technical detail and the declared [surface].
     * This is what every error branch should hand to the UI layer.
     *
     * @param fallbackMessage optional Persian copy describing *what the user was doing*
     *   ("خطا در حذف کیف پول"). Used only when the taxonomy has nothing more specific to say, so a
     *   typed [ApiError] keeps its curated wording and an unrecognized exception still reads as
     *   something concrete instead of [GENERIC_MESSAGE].
     */
    fun present(
        throwable: Throwable,
        surface: ErrorSurface = ErrorSurface.SNACKBAR,
        title: String = DEFAULT_TITLE,
        fallbackMessage: String? = null
    ): ErrorPresentation = ErrorPresentation(
        title = title,
        shortMessage = userMessage(throwable, fallbackMessage),
        technicalDetail = getTechnicalDetail(throwable),
        surface = surface,
        reasons = ErrorReason.of(map(throwable))
    )

    /** Digs the original failure out of the [AppError] wrappers so the detail stays informative. */
    private fun unwrap(throwable: Throwable): Throwable = when (throwable) {
        is AppError.Unexpected -> throwable.originalException
        is AppError.Network.Unknown -> throwable.originalException
        else -> throwable
    }
}
