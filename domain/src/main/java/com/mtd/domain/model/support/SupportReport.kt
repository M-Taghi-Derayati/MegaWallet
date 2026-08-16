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
    val walletAddress: String?
)
