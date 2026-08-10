package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.assets.TokenPriceQuery
import com.mtd.domain.model.assets.TokenPriceResult

/**
 * قیمت بر اساسِ **آدرسِ قرارداد** (`POST /api/mobile/v1/tokens/prices`).
 *
 * چرا جدا از [IMarketDataRepository]: آن یکی با **نماد** کار می‌کند و فقط به چند ارزِ اصلی پاسخ
 * می‌دهد، پس هر توکنی که کاربر خودش اضافه کرده از آن مسیر بی‌قیمت می‌ماند. این مسیر قراردادهای
 * دلخواه — از جمله ترون که تا امروز هیچ منبعِ قیمتی نداشت — را پوشش می‌دهد.
 */
interface ITokenPriceRepository {

    /**
     * قیمتِ USD برای چند قرارداد. فراخوانی خودش chunk می‌کند
     * ([TokenPriceResult.MAX_TOKENS_PER_REQUEST]) و نتیجه‌ها را ادغام می‌کند.
     *
     * پاسخِ ناقص **خطا نیست**: قرارداد‌های بی‌قیمت در `missing` می‌آیند و باید «—» نمایش داده شوند،
     * نه `$0`.
     */
    suspend fun getPricesByContract(queries: List<TokenPriceQuery>): ResultResponse<TokenPriceResult>
}
