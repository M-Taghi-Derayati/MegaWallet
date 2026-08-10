package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.assets.DiscoveredToken

/**
 * کشفِ توکن از بک‌اند (§5 قراردادِ ANDROID_SERVER_INTEGRATION).
 *
 * فقط **خواندنی** است: چیزی را به فهرستِ کاربر اضافه نمی‌کند. افزودن کارِ
 * [IUserTokenRepository] است.
 *
 * **ترتیبِ آرایهٔ سرور حفظ می‌شود.** سرور verified-first و بعد رتبهٔ ارزشِ بازار مرتب کرده؛
 * مرتب‌سازیِ دوبارهٔ محلی همان چیزی را برمی‌گرداند که این اندپوینت‌ها برای حذفش ساخته شدند —
 * روی ترون سه جعلِ USDT (`US DT`، `U S D T`، `UDST`) با قیمت‌های ساختگی جلوتر از تترِ واقعی بودند.
 */
interface ITokenDiscoveryRepository {

    /**
     * **فهرستِ پیش‌فرض** یک شبکه با یک فراخوانی — اجتماعِ کاتالوگِ امضاشدهٔ ما + رتبهٔ ارزشِ بازار
     * + توکن‌هایی که این آدرس واقعاً داشته، یکتاشده و مرتب‌شده.
     *
     * [address] همان چیزی است که نمی‌گذارد توکنِ خودِ کاربر فقط به‌خاطرِ بیرون‌بودن از ۱۰۰تای اول
     * ناپدید شود؛ از تاریخچهٔ از قبل ایندکس‌شده خوانده می‌شود، پس هزینهٔ فراخوانیِ اضافه ندارد.
     * `null` یعنی فقط کاتالوگ + رتبه‌بندی.
     */
    suspend fun getDefaultTokens(
        networkId: String,
        address: String?,
        limit: Int = DEFAULT_LIST_LIMIT
    ): ResultResponse<List<DiscoveredToken>>

    /**
     * توکن‌هایی که این آدرس قبلاً با آن‌ها تراکنش داشته، از تاریخچهٔ ایندکس‌شدهٔ سرور.
     * بدونِ تماسِ اضافه با زنجیره.
     *
     * از وقتی [getDefaultTokens] همین‌ها را در خودش دارد، این فقط برای وقتی می‌ماند که
     * «دارایی‌های شما» را جدا لازم داشته باشیم.
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

    /**
     * همان جست‌وجو روی همهٔ زنجیره‌ها. هر نتیجه `networkId` خودش را دارد؛ یک نماد مثل `USDT`
     * طبیعتاً چند ردیف برمی‌گرداند.
     *
     * نتایجی که روی شبکه‌ای ناشناخته برای این نسخهٔ اپ هستند حذف می‌شوند — توکنی که نمی‌توانیم
     * موجودی‌اش را بخوانیم نباید قابلِ افزودن باشد.
     */
    suspend fun searchTokensAllNetworks(
        query: String,
        limit: Int = DEFAULT_SEARCH_LIMIT
    ): ResultResponse<List<DiscoveredToken>>

    /**
     * یافتنِ یک آدرسِ قرارداد روی هر زنجیره‌ای که سرور می‌شناسد — مسیرِ «آدرس را paste کن».
     *
     * لیستِ خالی یعنی «هیچ‌جا پیدا نشد» و باید به ردِ import منجر شود، نه به حدسِ متادیتا؛ بدونِ
     * `decimals`ِ معتبر هر مبلغی ۱۰^n برابر غلط می‌شود. بیش از یک نتیجه هم عادی است و انتخاب
     * با کاربر است.
     */
    suspend fun resolveTokenByAddress(address: String): ResultResponse<List<DiscoveredToken>>

    /**
     * وقتی شبکه از قبل معلوم است: catalog → فهرستِ آینه‌ای → `eth_call` روی خودِ قرارداد. یعنی هر
     * ERC-20ِ واقعی قابلِ import است، نه فقط آن‌هایی که فهرستی منتشرشان کرده.
     *
     * `decimals` همیشه از یک منبعِ واقعی می‌آید و **هرگز پیش‌فرض نمی‌شود**: آدرسی که `decimals()`
     * آن خوانده نشود سمتِ سرور «توکن نیست» گزارش می‌شود (۴۰۴) — که همان چیزی است که جلوی خطای
     * ۱۰^۱۲ در نمایشِ مبلغ را می‌گیرد. این‌جا هم هیچ پیش‌فرضی اضافه نشود.
     *
     * [walletAddress] در همان فراخوانی آدرس را برای مانیتورینگ ثبت می‌کند.
     */
    suspend fun resolveTokenOnNetwork(
        networkId: String,
        contractAddress: String,
        walletAddress: String?
    ): ResultResponse<DiscoveredToken?>

    companion object {
        const val DEFAULT_SEARCH_LIMIT = 30

        /** پیش‌فرضِ سرور هم ۱۰۰ است؛ سقفش ۲۰۰. */
        const val DEFAULT_LIST_LIMIT = 100
    }
}
