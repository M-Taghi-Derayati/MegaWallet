package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.assets.AssetConfig

/**
 * نمای **صفحهٔ مدیریتِ توکن** به کاتالوگ: برخلاف [IAssetCatalog]، دارایی‌های پنهان‌شده را هم
 * برمی‌گرداند.
 *
 * جدا از [IAssetCatalog] است چون کارِ متفاوتی می‌کند: [IAssetCatalog] می‌گوید «چه چیزی نمایش داده
 * شود» و هیچ مصرف‌کنندهٔ نمایشی نباید دارایی پنهان‌شده ببیند؛ این‌جا کاربر دارد خودِ انتخاب را
 * ویرایش می‌کند، پس باید هر دو طرف را ببیند تا بتواند چیزی را که پنهان کرده برگرداند.
 *
 * هر دو را همان یک کلاسِ ادغام‌کننده پیاده می‌کند، تا دانشِ «باندل + کاربر − پنهان» در یک جا بماند.
 */
interface IManageableAssetCatalog {
    fun getManageableAssets(networkId: String): List<ManageableAsset>
}

data class ManageableAsset(
    val config: AssetConfig,
    val isHidden: Boolean
) {
    /**
     * کلیدِ یکتاسازی بینِ کاتالوگ و نتایجِ سرور. کوینِ اصلیِ شبکه قرارداد ندارد، پس با شناسه‌اش
     * کلید می‌گیرد و هرگز با یک توکن اشتباه گرفته نمی‌شود.
     */
    val dedupeKey: String
        get() = config.contractAddress
            ?.let { "${config.networkId}:${it.lowercase()}" }
            ?: "native:${config.id}"
}
