package com.mtd.data.repository.support

import com.mtd.data.dto.SupportReportRequestDto
import com.mtd.data.network.relayApiError
import com.mtd.data.service.SupportApiService
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.interfaceRepository.ISupportRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.support.SupportReportReceipt
import com.mtd.domain.model.support.SupportReportRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ثبتِ گزارش روی رله.
 *
 * ⚠️ پاسخِ ناموفق واقعاً به‌عنوان خطا برمی‌گردد و بلعیده نمی‌شود: `Response` را خودمان بررسی
 * می‌کنیم چون Retrofit با `Response<T>` برای کدِ ۵۰۰ هم استثنا پرتاب نمی‌کند، و بدونِ این بررسی
 * یک گزارشِ ردشده از سمتِ سرور در برنامه «ارسال شد» دیده می‌شد.
 *
 * ⚠️ خطا از [relayApiError] می‌گذرد، نه `HttpException`ِ خام. سرور پوششِ
 * `{ ok:false, error:{ code, message, detail } }` را برمی‌گرداند و پیامِ فارسیِ داخلش دقیق است
 * («متن گزارش بیش از حد مجاز است»، «تعداد گزارش‌های ارسالی شما زیاد است»). با `HttpException`
 * همهٔ این‌ها به «ارتباط با شبکه برقرار نشد» تبدیل می‌شدند و کاربر هرگز نمی‌فهمید چه شد.
 * `Retry-After` هم همان‌جا برداشته می‌شود، پس `429` قابلِ توضیح است.
 */
@Singleton
class SupportRepositoryImpl @Inject constructor(
    private val supportApiService: SupportApiService
) : ISupportRepository {

    override suspend fun submitReport(
        request: SupportReportRequest
    ): ResultResponse<SupportReportReceipt> = safeApiCall {
        val response = supportApiService.submitReport(request.toDto())
        if (!response.isSuccessful) {
            throw relayApiError(response, fallbackMessage = "گزارش ثبت نشد. لطفاً دوباره تلاش کنید.")
        }

        val body = response.body()
        SupportReportReceipt(
            ticketId = body?.ticketId?.takeIf { it.isNotBlank() },
            duplicate = body?.duplicate == true
        )
    }
}

/**
 * نگاشتِ دامنه به سیم.
 *
 * `name` خالی به `null` تبدیل می‌شود نه رشتهٔ تهی — «نامی نداد» و «نامی خالی داد» برای سمتِ
 * دیگر یک چیزند و فرستادنِ `""` فقط نویز است. `walletAddress` اما هیچ دستکاری‌ای نمی‌شود.
 *
 * فیلدهای دستگاه هم اگر خالی باشند `null` می‌شوند تا سرور رشتهٔ تهی ذخیره نکند.
 */
private fun SupportReportRequest.toDto(): SupportReportRequestDto = SupportReportRequestDto(
    category = category.wireValue,
    subject = subject.trim(),
    description = description.trim(),
    areas = areas.map { it.wireValue },
    name = name?.trim()?.takeIf { it.isNotEmpty() },
    email = email.trim(),
    walletAddress = walletAddress,
    appVersion = client.appVersion.blankToNull(),
    appBuild = client.appBuild,
    platform = client.platform.blankToNull(),
    osVersion = client.osVersion.blankToNull(),
    deviceModel = client.deviceModel.blankToNull(),
    locale = client.locale.blankToNull(),
    connectionMode = client.connectionMode.blankToNull()
)

private fun String.blankToNull(): String? = trim().takeIf { it.isNotEmpty() }
