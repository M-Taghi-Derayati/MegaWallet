package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.assets.AssetConfig

/**
 * منبعِ **واحدِ** دارایی‌ها برای کلِ برنامه: باندلِ امضاشده + توکن‌های افزودهٔ کاربر − پنهان‌شده‌ها.
 *
 * هیچ مصرف‌کننده‌ای نباید ادغام را خودش انجام دهد یا مستقیم سراغِ رجیستریِ باندل برود؛ اگر جایی
 * فهرستِ دارایی لازم دارد — چه برای نمایش، چه برای تصمیمِ اینکه موجودیِ کدام قرارداد خوانده شود —
 * از این‌جا می‌خوانَد. `AssetConfig.isUserAdded` می‌گوید یک ردیف از کدام سمت آمده.
 */
interface IAssetCatalog {
    fun getAllAssetConfigs(): List<AssetConfig>
    fun getAssetConfigById(id: String): AssetConfig?
    fun getAssetConfigsForNetwork(networkId: String): List<AssetConfig>
}
