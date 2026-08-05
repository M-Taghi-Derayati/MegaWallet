package com.mtd.domain.model.assets

data class AssetConfig(
    val id: String,
    val name: String,
    var symbol: String,
    val decimals: Int,
    val networkId: String,
    val contractAddress: String?,
    val iconUrl: String?,
    val faName: String? = null,
    /**
     * توکنی که خودِ کاربر اضافه کرده و در باندلِ امضاشده نیست.
     *
     * فقط منبعِ ادغام‌شده ([com.mtd.domain.interfaceRepository.IAssetCatalog]) این را `true` می‌گذارد؛
     * هرچه از assets.json یا باندل می‌آید `false` می‌ماند. مصرفِ اصلی‌اش گارد است، نه نمایش: مسیرِ
     * کارمزدِ gasless فهرستِ جداگانه و curated دارد و سرور این توکن‌ها را رد می‌کند، پس نباید
     * پیشنهاد شود.
     */
    val isUserAdded: Boolean = false
)