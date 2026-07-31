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
    val isTestnet: Boolean = false // مشخص کننده شبکه تست
)
