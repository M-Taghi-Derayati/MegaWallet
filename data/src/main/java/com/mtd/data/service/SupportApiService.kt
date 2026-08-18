package com.mtd.data.service

import com.mtd.data.dto.SupportReportRequestDto
import com.mtd.data.dto.SupportReportResponseDto
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
 * ⚠️ احراز هویتِ این نقطه **اختیاری** است و سمتِ سرور هم همین‌طور پیاده شده: فرم از حالتِ
 * خارج‌شده هم در دسترس است. هدرِ `Authorization` را این‌جا دستی نمی‌گذاریم؛ اگر نشستِ معتبری
 * باشد، همان `AuthInterceptor`ِ host-scoped خودش آن را می‌گذارد و سرور `userId` را به تیکت
 * می‌چسباند. اگر نباشد، درخواست بدونِ هدر می‌رود و پذیرفته می‌شود.
 */
interface SupportApiService {

    @POST(SUPPORT_REPORT_PATH)
    suspend fun submitReport(
        @Body body: SupportReportRequestDto
    ): Response<SupportReportResponseDto>
}
