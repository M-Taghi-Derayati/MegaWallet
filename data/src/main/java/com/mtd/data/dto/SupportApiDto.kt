package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

/**
 * بدنهٔ ثبتِ گزارشِ پشتیبانی.
 *
 * ⚠️ قراردادِ سرور هنوز نیامده؛ این شکل یک **پیشنهاد** است نه چیزی که تأیید شده باشد. وقتی
 * قرارداد رسید، همین یک فایل و [com.mtd.data.service.SUPPORT_REPORT_PATH] تنها جاهایی‌اند که
 * باید عوض شوند.
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
    @SerializedName("walletAddress") val walletAddress: String? = null
)
