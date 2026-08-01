package com.mtd.domain.model.swap

import java.math.BigInteger

/**
 * مدل‌های اجرای سوآپ: چیزی که `SwapModels.kt` (قرارداد سرور) ندارد و کلاینت برای اجرای
 * ترتیبیِ بستهٔ تراکنش‌ها به آن نیاز دارد.
 *
 * `prepare` یک لیست **مرتب** برمی‌گرداند (اول approve اگر لازم باشد، بعد swap). هر پا باید جداگانه
 * امضا و ارسال شود و پای approve باید **قبل از** ارسال پای swap ماین شده باشد؛ در غیر این صورت
 * swap با allowance صفر revert می‌شود.
 */

/** نقشِ هر پا در بسته. آخرین پا همیشه [SWAP] است. */
enum class SwapLegKind { APPROVE, SWAP }

enum class SwapLegStatus {
    PENDING,
    SUBMITTING,
    /** ارسال شد؛ منتظر تأیید شبکه. */
    CONFIRMING,
    CONFIRMED,
    /** ارسال شد ولی در بازهٔ poll تأیید نشد — نه موفق، نه شکست‌خورده. */
    UNCONFIRMED,
    FAILED
}

data class SwapLegProgress(
    val kind: SwapLegKind,
    val status: SwapLegStatus = SwapLegStatus.PENDING,
    val txId: String? = null
)

/**
 * نتیجهٔ نهاییِ بسته.
 *
 * [Stalled] از [Failed] جداست چون معنای متفاوتی برای کاربر دارد: تراکنش روی زنجیره هست و ممکن است
 * بعداً تأیید شود، پس نباید «ناموفق» نمایش داده شود و نباید دوباره ارسال شود.
 */
sealed interface SwapExecutionOutcome {
    data class Completed(val swapTxId: String) : SwapExecutionOutcome
    data class Stalled(val legIndex: Int, val txId: String) : SwapExecutionOutcome
    data class Failed(val legIndex: Int, val cause: Throwable) : SwapExecutionOutcome
}

data class SwapExecutionProgress(
    val legs: List<SwapLegProgress>,
    val activeIndex: Int? = null,
    val outcome: SwapExecutionOutcome? = null
) {
    val isFinished: Boolean get() = outcome != null
}

/** یک پای آمادهٔ ارسال؛ `gasLimit` توسط لایهٔ بالاتر حل شده چون سرور آن را اعلام نمی‌کند. */
data class SwapExecutionLeg(
    val kind: SwapLegKind,
    val to: String,
    val data: String,
    val value: BigInteger,
    val gasLimit: BigInteger
)

data class SwapExecutionRequest(
    val networkId: String,
    val gasPrice: BigInteger,
    /** به همان ترتیبی که `prepare` برگردانده است. */
    val legs: List<SwapExecutionLeg>
)

/**
 * `SwapTx.value` یک hex-quantity است (مثل `"0x0"`)، نه مقدار خامِ base-unit.
 *
 * برگرداندنِ `null` عمدی است: مقدارِ غیرقابل‌تجزیه نباید به صفر تبدیل شود، چون یک سوآپِ ارز بومی
 * را بی‌سروصدا به تراکنشی با ارزش صفر تبدیل می‌کند که روی زنجیره revert می‌شود.
 */
fun parseHexQuantityOrNull(raw: String?): BigInteger? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return BigInteger.ZERO
    val negative = text.startsWith("-")
    val body = text.removePrefix("-")
    val parsed = runCatching {
        if (body.startsWith("0x", ignoreCase = true)) {
            val digits = body.substring(2)
            if (digits.isEmpty()) BigInteger.ZERO else BigInteger(digits, 16)
        } else {
            BigInteger(body)
        }
    }.getOrNull() ?: return null
    return if (negative) null else parsed
}

/**
 * شناسهٔ توکن در `/api/v1/swap` برای ارزِ بومیِ شبکه.
 *
 * ⚠️ قرارداد سرور این را مشخص نکرده است. این مقدار، sentinel رایج بین aggregatorها
 * (1inch/0x/LiFi) است. تنها جایی است که این ثابت نوشته شده؛ اگر سرور شکل دیگری بخواهد
 * (`"native"`، رشتهٔ خالی، آدرس صفر) فقط همین‌جا عوض می‌شود.
 */
const val SWAP_NATIVE_TOKEN_SENTINEL = "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE"

/** آدرس قرارداد برای توکن‌ها، و [SWAP_NATIVE_TOKEN_SENTINEL] برای ارز بومی. */
fun swapTokenRef(contractAddress: String?): String =
    contractAddress?.takeIf { it.isNotBlank() } ?: SWAP_NATIVE_TOKEN_SENTINEL
