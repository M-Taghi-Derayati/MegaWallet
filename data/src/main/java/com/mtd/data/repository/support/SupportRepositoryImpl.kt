package com.mtd.data.repository.support

import com.mtd.data.dto.SupportReportRequestDto
import com.mtd.data.service.SupportApiService
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.interfaceRepository.ISupportRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.support.SupportReportRequest
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ثبتِ گزارش روی رله.
 *
 * ⚠️ پاسخِ ناموفق واقعاً به‌عنوان خطا برمی‌گردد و بلعیده نمی‌شود: `Response` را خودمان بررسی
 * می‌کنیم چون Retrofit با `Response<T>` برای کدِ ۵۰۰ هم استثنا پرتاب نمی‌کند، و بدونِ این بررسی
 * یک گزارشِ ردشده از سمتِ سرور در برنامه «ارسال شد» دیده می‌شد.
 */
@Singleton
class SupportRepositoryImpl @Inject constructor(
    private val supportApiService: SupportApiService
) : ISupportRepository {

    override suspend fun submitReport(request: SupportReportRequest): ResultResponse<Unit> =
        safeApiCall {
            val response = supportApiService.submitReport(request.toDto())
            if (!response.isSuccessful) throw HttpException(response)
            Unit
        }
}

/**
 * نگاشتِ دامنه به سیم.
 *
 * `name` خالی به `null` تبدیل می‌شود نه رشتهٔ تهی — «نامی نداد» و «نامی خالی داد» برای سمتِ
 * دیگر یک چیزند و فرستادنِ `""` فقط نویز است. `walletAddress` اما هیچ دستکاری‌ای نمی‌شود.
 */
private fun SupportReportRequest.toDto(): SupportReportRequestDto = SupportReportRequestDto(
    category = category.wireValue,
    subject = subject.trim(),
    description = description.trim(),
    areas = areas.map { it.wireValue },
    name = name?.trim()?.takeIf { it.isNotEmpty() },
    email = email.trim(),
    walletAddress = walletAddress
)
