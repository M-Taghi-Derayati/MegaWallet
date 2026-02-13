package com.mtd.core.model

data class NetworkConfig(
    val id: String,
    val name: String,
    val networkType: String,
    val chainId: Long?,
    val derivationPath: String,
    val rpcUrls: List<String>,
    val currencySymbol: String,
    val webSocketUrl: String?,
    val decimals: Int,
    val iconUrl: String,
    val explorers: List<String>, // لیست Base URL های API اکسپلوررها
    val color: String? = null, // رنگ شبکه به صورت هگز
    val faName: String? = null, // نام فارسی شبکه
    val isTestnet: Boolean = false // مشخص کننده شبکه تست
)
