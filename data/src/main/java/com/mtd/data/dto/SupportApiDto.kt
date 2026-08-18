package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

/**
 * بدنهٔ `POST /api/v1/support/reports`.
 *
 * قرارداد: `docs/architecture/support-report-contract.md` — و سمتِ سرور
 * `routes/supportRoutes.js` که همان را پیاده کرده است.
 *
 * ⚠️ فیلدهای اطلاعاتِ دستگاه **تخت** روی سیم می‌نشینند، نه داخلِ یک شیءِ `client`. سرور خودش
 * آن‌ها را زیرِ `client` ذخیره می‌کند؛ شکلِ ذخیره‌سازیِ او قرارداد نیست.
 *
 * `walletAddress` می‌تواند `null` باشد و همان‌طور که آمده فرستاده می‌شود — هیچ `lowercase`ای
 * روی آدرس انجام نمی‌شود.
 */
data class SupportReportRequestDto(
    @SerializedName("category") val category: String,
    @SerializedName("subject") val subject: String,
    @SerializedName("description") val description: String,
    @SerializedName("areas") val areas: List<String>,
    @SerializedName("name") val name: String? = null,
    @SerializedName("email") val email: String,
    @SerializedName("walletAddress") val walletAddress: String? = null,

    // ── اطلاعاتِ دستگاه ─────────────────────────────────────────────────────────
    // سرور هیچ‌کدام را اجباری نکرده و نبودنشان گزارش را رد نمی‌کند.
    @SerializedName("appVersion") val appVersion: String? = null,
    @SerializedName("appBuild") val appBuild: Int? = null,
    @SerializedName("platform") val platform: String? = null,
    @SerializedName("osVersion") val osVersion: String? = null,
    @SerializedName("deviceModel") val deviceModel: String? = null,
    @SerializedName("locale") val locale: String? = null,
    @SerializedName("connectionMode") val connectionMode: String? = null
)

/**
 * پاسخِ موفق.
 *
 * `201` برای گزارشِ تازه و `200` وقتی سرور همین گزارش را در پنجرهٔ دِدوپِ خودش دیده و
 * [duplicate] را `true` کرده؛ در آن حالت [ticketId] شمارهٔ **همان تیکتِ اول** است.
 *
 * ⚠️ همه‌چیز خالی‌پذیر است. یک پاسخِ موفق با بدنهٔ ناقص نباید تبدیل به کرش شود؛ گزارش ثبت شده و
 * نبودنِ شمارهٔ پیگیری فقط یعنی جملهٔ کوتاه‌تری به کاربر نشان می‌دهیم.
 */
data class SupportReportResponseDto(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("ticketId") val ticketId: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("duplicate") val duplicate: Boolean = false
)
