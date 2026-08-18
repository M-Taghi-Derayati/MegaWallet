package com.mtd.domain.model.error

/**
 * دلیلِ محتملِ یک خطا — «چرا ممکن است این اتفاق افتاده باشد».
 *
 * پیامِ کوتاهِ [ErrorMapper.getUserMessage] فقط می‌گوید *چه* شد. اسنک‌بار در حالتِ بسته همان یک
 * جمله است و با یک ضربه باز می‌شود و این فهرست را نشان می‌دهد؛ هر دلیل یک عنوان، یک توضیح و
 * (در لایهٔ UI) یک آیکون دارد.
 *
 * ⚠️ متن اینجا زندگی می‌کند و نه در لایهٔ Compose، چون هم‌خانوادهٔ [ErrorMapper] است: هر جملهٔ
 * کاربرپسندِ خطا در `domain` نوشته می‌شود تا یک‌جا قابلِ بازبینی باشد. آیکون اما به این لایه راه
 * ندارد و در `AppSnackbar` به هر مقدار نگاشته می‌شود.
 */
enum class ErrorReason(val title: String, val body: String) {
    CONNECTION(
        title = "اینترنت ضعیف",
        body = "اتصالِ ضعیف یا قطع‌ووصل شدنِ اینترنت باعث می‌شود اطلاعات دریافت یا به‌روز نشوند."
    ),
    SLOW_RESPONSE(
        title = "پاسخِ دیرهنگام",
        body = "درخواست فرستاده شد اما پاسخی در زمانِ مجاز نرسید. چند لحظه بعد دوباره تلاش کنید."
    ),
    SERVER(
        title = "اختلالِ سرور",
        body = "گاهی سرورها کندتر از همیشه پاسخ می‌دهند. این دست مشکل‌ها معمولاً زود برطرف می‌شوند."
    ),
    BALANCE(
        title = "موجودیِ ناکافی",
        body = "موجودیِ این شبکه برای مبلغِ تراکنش و کارمزدِ آن کافی نیست."
    ),
    ADDRESS(
        title = "آدرسِ نامعتبر",
        body = "آدرسِ مقصد با قالبِ این شبکه هم‌خوانی ندارد. آن را دوباره بررسی کنید."
    ),
    OTHER(
        title = "مشکلِ دیگر",
        body = "مشکل‌های کم‌تر رایج نیاز به بررسیِ ما دارند. اگر تکرار شد با پشتیبانی تماس بگیرید."
    );

    companion object {
        /**
         * دلایلِ محتملِ یک خطا، به ترتیبِ احتمال.
         *
         * فهرستِ خالی یعنی «حرفِ بیشتری نداریم»؛ اسنک‌بار در آن حالت اصلاً باز نمی‌شود — بهتر از
         * بازشدن روی یک کارتِ خالی. [AppError.Business.General] عمداً خالی است: متنش از قبل
         * دقیق و دست‌نویس است و حدس زدن برایش چیزی اضافه نمی‌کند.
         */
        fun of(error: AppError): List<ErrorReason> = when (error) {
            is AppError.Network.NoInternet -> listOf(CONNECTION, SERVER, OTHER)
            is AppError.Network.Timeout -> listOf(SLOW_RESPONSE, CONNECTION, SERVER)
            is AppError.Network.ServerUnavailable -> listOf(SERVER, CONNECTION, OTHER)
            is AppError.Network.Unknown -> listOf(CONNECTION, SERVER, OTHER)
            is AppError.Business.InsufficientFunds -> listOf(BALANCE)
            is AppError.Business.InvalidAddress -> listOf(ADDRESS)
            is AppError.Business.General -> emptyList()
            is AppError.Unexpected -> listOf(CONNECTION, SERVER, OTHER)
        }
    }
}
