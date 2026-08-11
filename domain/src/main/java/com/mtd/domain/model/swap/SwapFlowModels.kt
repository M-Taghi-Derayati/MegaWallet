package com.mtd.domain.model.swap

import com.mtd.domain.model.core.NetworkType
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

/**
 * محتوای یک پا، به تفکیکِ خانواده.
 *
 * پارامترهای هزینه عمداً **درونِ** پا نشسته‌اند و نه در [SwapExecutionRequest]: یک بسته می‌تواند
 * پاهای EVM و TVM داشته باشد و `gasPrice` برای TVM اصلاً معنا ندارد (اهرمِ هزینه در ترون
 * `feeLimit` است که خودِ سرور داخلِ تراکنش گذاشته).
 */
sealed interface SwapLegPayload {

    /** `gasLimit` را لایهٔ بالاتر حل می‌کند چون سرور آن را اعلام نمی‌کند. */
    data class Evm(
        val to: String,
        val data: String,
        val value: BigInteger,
        val gasLimit: BigInteger,
        val gasPrice: BigInteger
    ) : SwapLegPayload

    /**
     * تراکنشِ کاملِ ترون، همان‌طور که سرور ساخته. هیچ فیلدی این‌جا بازسازی یا نرمال‌سازی نمی‌شود؛
     * امضا روی [txId] انجام می‌شود و هر سه فیلد عیناً به نود می‌روند.
     */
    data class Tvm(
        val txId: String,
        val rawDataJson: String,
        val rawDataHex: String,
        val visible: Boolean
    ) : SwapLegPayload
}

data class SwapExecutionLeg(
    val kind: SwapLegKind,
    val payload: SwapLegPayload
)

data class SwapExecutionRequest(
    val networkId: String,
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
fun swapTokenRef(contractAddress: String?,tokenId: String): String =
    contractAddress?.takeIf { it.isNotBlank() } ?: tokenId

// ── آدرسِ مقصد ────────────────────────────────────────────────────────────────

/**
 * آیا مبدأ و مقصد دو **قالبِ آدرسِ** متفاوت دارند.
 *
 * تنها همین حالت است که آدرسِ مقصد را اجباری می‌کند: آدرسِ EVM هگزِ `0x…` است و آدرسِ ترون
 * base58ِ `T…`، پس آدرسِ فرستنده روی زنجیرهٔ مقصد اصلاً آدرس نیست و چیزی برای پیش‌فرض‌گرفتن
 * نمی‌ماند. برای دو زنجیرهٔ هم‌خانواده (EVM→EVM) پیش‌فرض‌گرفتنِ آدرسِ فرستنده درست است.
 *
 * نوعِ ناشناخته (`null`) «متفاوت» حساب نمی‌شود: بستنِ فلو بر مبنای ندانستن، جفت‌های سالم را هم
 * می‌بندد و سرور خودش جفتِ نامعتبر را با ۴۰۰ِ نام‌دار رد می‌کند.
 */
fun isCrossAddressFamily(from: NetworkType?, to: NetworkType?): Boolean {
    if (from == null || to == null) return false
    return from != to
}

/**
 * مقایسهٔ دو آدرس برای «همان گیرنده؟».
 *
 * حساسیت به حروف عمداً مشروط است: آدرسِ EVM بی‌تفاوت به حروف است (checksum فقط نمایشی است) ولی
 * base58ِ ترون **حساس** است و کوچک‌کردنش دو آدرسِ متفاوت را یکی نشان می‌دهد.
 */
fun sameSwapAddress(first: String?, second: String?): Boolean {
    val a = first?.trim().orEmpty()
    val b = second?.trim().orEmpty()
    if (a.isEmpty() || b.isEmpty()) return false
    val bothHex = a.startsWith("0x", ignoreCase = true) && b.startsWith("0x", ignoreCase = true)
    return if (bothHex) a.equals(b, ignoreCase = true) else a == b
}
