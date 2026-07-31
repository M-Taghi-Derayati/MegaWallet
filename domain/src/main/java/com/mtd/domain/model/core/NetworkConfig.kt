package com.mtd.domain.model.core

data class NetworkConfig(
    val id: String,
    val name: String,
    val networkType: String,
    val chainId: Long?,
    val derivationPath: String,
    val rpcUrlsEvm: List<String>,
    val rpcUrls: List<String>,
    val currencySymbol: String,
    val webSocketUrl: String?,
    val decimals: Int,
    val regex: String? = null,
    val iconUrl: String,
    val explorers: List<String>, // لیست Base URL های API اکسپلوررها
    // TASK-51 — قالب آدرس صفحه‌ی وب اکسپلورر برای یک تراکنش، با جایگزین {hash}.
    // این با `explorers` فرق دارد: آن‌ها Base URL های API هستند (برای دیتاسورس‌ها)، نه صفحه‌ای که
    // کاربر باز می‌کند. اگر null باشد، از روی API base حدس زده می‌شود که برای همه‌ی شبکه‌ها جواب نمی‌دهد.
    val explorerTxUrl: String? = null,
    val color: String? = null, // رنگ شبکه به صورت هگز
    val faName: String? = null, // نام فارسی شبکه
    val isTestnet: Boolean = false, // مشخص کننده شبکه تست

    // TASK-53 — رفتارهای مخصوصِ هر زنجیره که قبلاً `when (network.name)` بودند و حالا داده‌اند.
    // هدف: زنجیرهٔ EVM جدیدی که سرور اضافه می‌کند بدون تغییر کد کار کند.

    /**
     * گویشِ API اکسپلورر: `"etherscan"` یا `"bscscan"`.
     * اگر null باشد [DEFAULT_EXPLORER_API] فرض می‌شود، چون گویش Etherscan عملاً استاندارد
     * مشترک اکثر زنجیره‌های EVM است. (قبلاً شاخهٔ `else` هیچ تاریخچه‌ای برنمی‌گرداند.)
     */
    val explorerApi: String? = null,

    /**
     * آیا این زنجیره L2 مبتنی بر OP-Stack است و علاوه بر گس L2، هزینهٔ دادهٔ L1 هم دارد؟
     * فقط برای [NetworkType.EVM] معنا دارد.
     */
    val hasL1DataFee: Boolean = false
) {
    companion object {
        const val EXPLORER_API_ETHERSCAN = "etherscan"
        const val EXPLORER_API_BSCSCAN = "bscscan"

        /** گویشِ پیش‌فرض برای شبکه‌ای که `explorerApi` را اعلام نکرده است. */
        const val DEFAULT_EXPLORER_API = EXPLORER_API_ETHERSCAN
    }
}
