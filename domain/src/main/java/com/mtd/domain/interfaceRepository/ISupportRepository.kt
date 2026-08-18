package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.support.SupportReportReceipt
import com.mtd.domain.model.support.SupportReportRequest

/**
 * ثبتِ گزارشِ پشتیبانی روی `POST /api/v1/support/reports`.
 *
 * ⚠️ شکست واقعاً شکست برمی‌گرداند — دکمه‌ای که وانمود کند فرستاده شد، از دکمه‌ای که خطا بدهد
 * بدتر است. تنها مسیرِ موفقیت، پاسخِ `2xx` سرور است.
 *
 * قرارداد: `docs/architecture/support-report-contract.md`.
 */
interface ISupportRepository {

    suspend fun submitReport(request: SupportReportRequest): ResultResponse<SupportReportReceipt>
}
