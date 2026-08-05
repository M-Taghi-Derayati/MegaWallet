package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.assets.DiscoveredToken

/**
 * کشفِ توکن از بک‌اند (§5 قراردادِ ANDROID_SERVER_INTEGRATION).
 *
 * فقط **خواندنی** است: چیزی را به فهرستِ کاربر اضافه نمی‌کند. افزودن کارِ
 * [IUserTokenRepository] است.
 */
interface ITokenDiscoveryRepository {

    /**
     * توکن‌هایی که این آدرس قبلاً با آن‌ها تراکنش داشته، از تاریخچهٔ ایندکس‌شدهٔ سرور.
     * بدونِ تماسِ اضافه با زنجیره.
     */
    suspend fun getHeldTokens(networkId: String, address: String): ResultResponse<List<DiscoveredToken>>

    /**
     * جست‌وجو در کلِ جهانِ توکن‌ها (شاملِ long-tailِ غیر-featured) با نماد، نام، یا آدرسِ دقیقِ قرارداد.
     */
    suspend fun searchTokens(
        networkId: String,
        query: String,
        limit: Int = DEFAULT_SEARCH_LIMIT
    ): ResultResponse<List<DiscoveredToken>>

    companion object {
        const val DEFAULT_SEARCH_LIMIT = 30
    }
}
