package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.support.SupportReportRequest

/**
 * ثبتِ گزارشِ پشتیبانی.
 *
 * ⚠️ قراردادِ سمتِ سرور هنوز نهایی نشده. به همین دلیل خروجی `ResultResponse<Unit>` است و نه یک
 * مدلِ پاسخ: تا وقتی شکلِ پاسخ معلوم نیست، ساختنِ مدلی برایش یعنی جا انداختنِ حدسی که بعداً
 * قطعی به نظر می‌رسد. شکست هم واقعاً شکست برمی‌گرداند — دکمه‌ای که وانمود کند فرستاده شد، از
 * دکمه‌ای که خطا بدهد بدتر است.
 */
interface ISupportRepository {

    suspend fun submitReport(request: SupportReportRequest): ResultResponse<Unit>
}
