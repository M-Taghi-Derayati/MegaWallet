package com.mtd.domain.model.assets

/**
 * توکنی که کاربر خودش به فهرستش اضافه کرده و در باندلِ امضاشده نیست.
 *
 * همهٔ فیلدهای لازم برای ساختِ یک [AssetConfig] این‌جا ذخیره می‌شوند، چون هیچ کاتالوگی این توکن را
 * نمی‌شناسد: بعد از افزودن، دستگاه باید بدونِ شبکه هم بتواند ردیفِ آن را کامل بسازد.
 *
 * `decimals` این‌جا **منبعِ حقیقت** است، نه یک کپیِ کش‌شده — مصرف‌کننده‌ها آن را از منبعِ ادغام‌شده
 * می‌خوانند، نه از جای دیگری. مقدارِ اشتباه یعنی خطای ۱۰^n در پول.
 */
data class UserToken(
    val networkId: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val contractAddress: String,
    val iconUrl: String?,
    /** شناسهٔ کاتالوگِ سرور اگر داشت؛ فقط اطلاعاتی است و در ساختِ [assetId] نقشی ندارد. */
    val catalogId: String? = null
) {
    /**
     * شناسهٔ دارایی برای این توکن.
     *
     * پیشوندِ [USER_ASSET_ID_PREFIX] تضمین می‌کند با شناسه‌های باندل (مثل `USDT-SEPOLIA`) برخورد
     * نکند، و lowercase کردنِ قرارداد یعنی یک توکن با هر شکلِ checksum فقط یک ردیف می‌سازد.
     */
    val assetId: String get() = "$USER_ASSET_ID_PREFIX$networkId:${contractAddress.lowercase()}"

    val dedupeKey: String get() = "$networkId:${contractAddress.lowercase()}"

    companion object {
        const val USER_ASSET_ID_PREFIX = "user:"
    }
}

/**
 * انتخابِ کاربر برای یک کیف پول: چه اضافه شده و چه پنهان شده.
 *
 * حذف همیشه «پنهان‌سازی» است نه پاک‌کردن، چون دارایی‌های باندل واقعاً حذف‌شدنی نیستند؛ فهرستِ
 * [hiddenAssetIds] روی هر دو نوع (باندل و کاربر) کار می‌کند.
 */
data class UserTokenSelection(
    val added: List<UserToken> = emptyList(),
    val hiddenAssetIds: Set<String> = emptySet()
) {
    val isEmpty: Boolean get() = added.isEmpty() && hiddenAssetIds.isEmpty()

    companion object {
        val EMPTY = UserTokenSelection()
    }
}
