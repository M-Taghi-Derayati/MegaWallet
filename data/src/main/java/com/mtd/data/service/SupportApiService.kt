package com.mtd.data.service

import com.mtd.data.dto.SupportReportRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * تنها جایی که مسیرِ ثبتِ گزارش نوشته می‌شود.
 *
 * بالای فایل و `const` است تا هم در انوتیشنِ Retrofit قابلِ استفاده باشد و هم عوض‌کردنش یک
 * ویرایش باشد، نه جست‌وجو در چند فایل.
 */
const val SUPPORT_REPORT_PATH = "api/v1/support/reports"

/**
 * ثبتِ گزارشِ پشتیبانی روی رله.
 *
 * ⚠️ دربارهٔ احرازِ هویتِ این نقطه هیچ فرضی این‌جا گذاشته نشده. اگر سرور توکن بخواهد، همان
 * `AuthInterceptor`ِ host-scoped که به بقیهٔ مسیرهای رله وصل است خودش هدر را می‌گذارد؛ اگر
 * نخواهد، چیزی خراب نمی‌شود. تا وقتی قرارداد نیامده، هیچ هدرِ دستی‌ای این‌جا اضافه نمی‌شود.
 */
interface SupportApiService {

    @POST(SUPPORT_REPORT_PATH)
    suspend fun submitReport(
        @Body body: SupportReportRequestDto
    ): Response<Unit>
}
