package com.mtd.domain.model.error

import com.mtd.domain.model.error.ApiErrorMessageMapper.farsiMessage


/**
 * Phase 8 (KAN-20 / KAN-21) — centralized, user-facing error taxonomy.
 *
 * ViewModels must NOT branch on raw server `code`s. They handle the typed [ApiError] (already
 * resolved from the machine `code` in [ApiError.from]) and ask this mapper for a friendly,
 * non-technical message. Messages are provided in both Persian (default, app UI language) and
 * English so the same source can back string resources later.
 *
 * If the server already returned a localized human message ([ApiException.reasonFa]) we still prefer
 * our own curated copy for the cases we recognize, and fall back to the server text only for the
 * truly [ApiError.Unknown] bucket — keeping the UX consistent while never leaking raw codes.
 */
object ApiErrorMessageMapper {

    /** Persian (Farsi) user-facing message for a typed [ApiError]. */
    fun farsiMessage(error: ApiError): String = when (error) {
        ApiError.NetworkNotFound -> "این شبکه در حال حاضر پشتیبانی نمی‌شود."
        ApiError.AssetNotFound -> "این دارایی روی شبکه انتخاب‌شده یافت نشد."
        ApiError.ValidationError -> "اطلاعات ارسالی نامعتبر است. لطفاً ورودی‌ها را بررسی کنید."
        ApiError.InvalidAddress -> "آدرس مقصد معتبر نیست."
        ApiError.InvalidSignedTx -> "تراکنش امضاشده نامعتبر است. لطفاً دوباره تلاش کنید."
        ApiError.UnsupportedOperation -> "این عملیات روی این شبکه پشتیبانی نمی‌شود."
        ApiError.SimulationReverted -> "شبیه‌سازی تراکنش با خطا مواجه شد و احتمال شکست آن وجود دارد."
        ApiError.NetworkFamilyUnsupported -> "این خانواده‌ی شبکه هنوز پشتیبانی نمی‌شود."
        ApiError.UpstreamUnavailable -> "ارتباط با شبکه بلاکچین برقرار نشد. کمی بعد دوباره تلاش کنید."
        ApiError.BroadcastRejected -> "تراکنش توسط شبکه پذیرفته نشد."
        ApiError.RaceConditionLock -> "این تراکنش در حال پردازش است. لطفاً چند لحظه صبر کرده و دوباره تلاش کنید."
        ApiError.InsufficientGasCredit -> "اعتبار کارمزد (گس) شما کافی نیست."
        ApiError.RequoteRequired -> "نرخ تراکنش تغییر کرده است. لطفاً دوباره استعلام بگیرید."
        ApiError.IdempotencyKeyConflict -> "درخواست مشابهی با همین شناسه قبلاً ثبت شده است. لطفاً دوباره تلاش کنید."
        is ApiError.RateLimited -> "تعداد درخواست‌ها زیاد بود. لطفاً کمی بعد دوباره تلاش کنید."
        ApiError.ServiceUnavailable -> "سرویس موقتاً در دسترس نیست. لطفاً بعداً تلاش کنید."
        ApiError.GasCreditUnavailable -> "اعتبار کارمزد رایگان در حال حاضر در دسترس نیست. می‌توانید کارمزد را از کیف پول پرداخت کنید."
        ApiError.MysteryBoxAlreadyOpened -> "این جعبه قبلاً باز شده است."
        ApiError.MysteryBoxNotFound -> "جعبه‌ی موردنظر یافت نشد."
        ApiError.DeviceRequired -> "برای این بخش باید دستگاه شما تأیید شده باشد."
        ApiError.DeviceRewardLimitExceeded -> "به سقف مجاز دریافت جایزه برای این دستگاه رسیده‌اید. کمی بعد دوباره تلاش کنید."
        ApiError.SwapNoRoutes -> "مسیری برای این تبدیل پیدا نشد."
        ApiError.InternalError -> "خطایی در سرور رخ داد. لطفاً دوباره تلاش کنید."
        is ApiError.Unknown -> "خطای ناشناخته‌ای رخ داد. لطفاً دوباره تلاش کنید."
        else -> "خطای ناشناخته‌ای رخ داد. لطفاً دوباره تلاش کنید."
    }

    /** English user-facing message for a typed [ApiError]. */
    fun englishMessage(error: ApiError): String = when (error) {
        ApiError.NetworkNotFound -> "This network is not supported right now."
        ApiError.AssetNotFound -> "This asset was not found on the selected network."
        ApiError.ValidationError -> "The request is invalid. Please check your inputs."
        ApiError.InvalidAddress -> "The destination address is invalid."
        ApiError.InvalidSignedTx -> "The signed transaction is invalid. Please try again."
        ApiError.UnsupportedOperation -> "This operation is not supported on this network."
        ApiError.SimulationReverted -> "The transaction simulation failed and is likely to revert."
        ApiError.NetworkFamilyUnsupported -> "This network family is not supported yet."
        ApiError.UpstreamUnavailable -> "Couldn't reach the blockchain network. Please try again shortly."
        ApiError.BroadcastRejected -> "The network rejected the transaction."
        ApiError.RaceConditionLock -> "This transaction is already being processed. Please wait a moment and retry."
        ApiError.InsufficientGasCredit -> "Your gas credit balance is not enough."
        ApiError.RequoteRequired -> "The transaction rate changed. Please request a new quote."
        ApiError.IdempotencyKeyConflict -> "A request with this same id was already submitted. Please try again."
        is ApiError.RateLimited -> "Too many requests. Please try again shortly."
        ApiError.ServiceUnavailable -> "The service is temporarily unavailable. Please try again later."
        ApiError.GasCreditUnavailable -> "Free gas credit is currently unavailable. You can pay the fee from your wallet instead."
        ApiError.MysteryBoxAlreadyOpened -> "This box has already been opened."
        ApiError.MysteryBoxNotFound -> "The box could not be found."
        ApiError.DeviceRequired -> "Your device must be verified for this feature."
        ApiError.DeviceRewardLimitExceeded -> "You've reached the reward limit for this device. Try again later."
        ApiError.SwapNoRoutes -> "No route was found for this swap."
        ApiError.InternalError -> "A server error occurred. Please try again."
        is ApiError.Unknown -> "An unexpected error occurred. Please try again."
        else -> "An unexpected error occurred. Please try again."
    }

    /**
     * Machine-readable label for the "جزئیات" dialog and the log. Never shown as the primary
     * message — [farsiMessage] owns that — and never carries server-supplied free text.
     */
    fun technicalCode(error: ApiError): String = when (error) {
        is ApiError.Unknown -> error.code?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
        is ApiError.RateLimited -> "RATE_LIMITED"
        else -> error::class.simpleName ?: "UNKNOWN"
    }

    /**
     * Fold an [ApiException] into the app-wide [AppError] taxonomy so it flows through the existing
     * `ErrorManager` snackbar/dialog pipeline. Cross-cutting infra failures map onto the Network
     * bucket (retryable); everything else becomes a business-level message.
     */
    fun toAppError(exception: ApiException): AppError {
        val message = farsiMessage(exception.apiError).let { curated ->
            if (exception.apiError is ApiError.Unknown) exception.reasonFa ?: curated else curated
        }
        return when (exception.apiError) {
            ApiError.UpstreamUnavailable,
            ApiError.ServiceUnavailable,
            is ApiError.RateLimited -> AppError.Network.ServerUnavailable

            ApiError.InvalidAddress -> AppError.Business.InvalidAddress
            ApiError.InsufficientGasCredit -> AppError.Business.InsufficientFunds

            else -> AppError.Business.General(message = message)
        }
    }
}
