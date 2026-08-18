package com.mtd.domain.model.support

/**
 * دسته‌ای که کاربر در گامِ اولِ پشتیبانی انتخاب می‌کند.
 *
 * فقط یک برچسب نیست: لحن و رنگِ همهٔ گام‌های بعدی از همین می‌آید، و سمتِ سرور هم صف‌بندیِ گزارش
 * به همین وابسته است. پس مقدارِ سیمیِ هر عضو باید پایدار بماند، حتی اگر متنِ فارسی‌اش عوض شود.
 */
enum class SupportCategory(val wireValue: String) {
    BUG("bug"),
    FEEDBACK("feedback"),
    OTHER("other")
}

/**
 * بخش‌هایی از برنامه که یک گزارش می‌تواند به آن‌ها مربوط باشد.
 *
 * ⚠️ این فهرست فقط چیزهایی را دارد که برنامه واقعاً دارد. NFT عمداً این‌جا نیست — هنوز در دست
 * ساخت است و آوردنِ نامش در فهرستِ «کدام بخش؟» یعنی وعدهٔ قابلیتی که وجود ندارد.
 *
 * افزودنِ عضوِ تازه نیازی به انتشارِ سرور ندارد: سرور مقدارِ ناشناس را دور نمی‌اندازد، خودش را
 * دست‌نخورده نگه می‌دارد و برای فیلتر کردن به `other` تا می‌کند.
 */
enum class SupportArea(val wireValue: String) {
    SEND("send"),
    RECEIVE("receive"),
    SWAP("swap"),
    HISTORY("history"),
    TOKENS("tokens"),
    SECURITY("security"),
    OTHER("other")
}

/**
 * حدودی که سرور اعمال می‌کند.
 *
 * ⚠️ این‌ها **آینهٔ** `LIMITS` در `routes/supportRoutes.js` هستند، نه سلیقهٔ ما. این‌جا تکرار
 * شده‌اند تا ورودی همان‌جا که تایپ می‌شود متوقف شود؛ کاربری که چهار گام را پر کند و بعد
 * `VALIDATION_ERROR` بگیرد، بدترین حالتِ ممکن است. اگر سمتِ سرور عوض شد، این‌جا هم عوض شود.
 *
 * [AREAS] هرگز به مرز نمی‌رسد چون `SupportArea` خودش هفت عضو دارد و بیشتر از آن انتخاب‌شدنی
 * نیست؛ همان‌جا می‌ماند تا اگر عضوِ هشتمی اضافه شد، این محدودیت از یاد نرود.
 */
object SupportLimits {
    const val SUBJECT = 120
    const val DESCRIPTION = 4_000
    const val NAME = 80
    const val EMAIL = 254
    const val AREAS = 7
}

/**
 * اطلاعاتِ نسخه و دستگاه، همراهِ گزارش.
 *
 * سرور هیچ‌کدام را اجباری نکرده و نبودنشان گزارش را رد نمی‌کند، ولی بدونِ [appVersion] بیشترِ
 * گزارش‌های اشکال غیرقابلِ پیگیری‌اند.
 *
 * ⚠️ عمداً هیچ شناسهٔ پایداری این‌جا نیست — نه `deviceId`، نه شناسهٔ تبلیغاتی. این بسته برای
 * بازتولیدِ یک اشکال است، نه برای شناختنِ یک نفر.
 */
data class SupportClientInfo(
    val appVersion: String,
    val appBuild: Int?,
    val platform: String,
    val osVersion: String,
    val deviceModel: String,
    val locale: String,
    val connectionMode: String
)

/**
 * یک گزارشِ آمادهٔ ارسال.
 *
 * ⚠️ [walletAddress] آدرسِ EVMِ کیف‌پولِ انتخاب‌شده است و **دست‌نخورده** حمل می‌شود؛ نه کوچک
 * می‌شود نه نرمال. اگر کیف‌پول اصلاً کلیدِ EVM نداشته باشد `null` می‌ماند و گزارش بدونِ آدرس
 * می‌رود — نبودنِ یک فیلدِ کمکی نباید جلوی ثبتِ گزارش را بگیرد.
 */
data class SupportReportRequest(
    val category: SupportCategory,
    val subject: String,
    val description: String,
    val areas: List<SupportArea>,
    val name: String?,
    val email: String,
    val walletAddress: String?,
    val client: SupportClientInfo
)

/**
 * رسیدِ ثبتِ گزارش.
 *
 * [ticketId] شمارهٔ پیگیری است (`SUP-<سال>-<شماره>`) و به کاربر نشان داده می‌شود.
 *
 * [duplicate] یعنی سرور همین گزارش را در پنجرهٔ دِدوپِ خودش دیده و به‌جای تیکتِ تازه، **همان
 * تیکتِ اول** را برگردانده. برای کاربر شکست نیست — گزارشش ثبت شده — ولی جمله‌ای که می‌بیند باید
 * فرق کند، وگرنه فکر می‌کند دو بار ثبت شده است.
 */
data class SupportReportReceipt(
    val ticketId: String?,
    val duplicate: Boolean
)
