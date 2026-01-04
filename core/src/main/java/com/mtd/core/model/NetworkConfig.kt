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
    val phoenixContractAddress: String?,
    val blockExplorerUrl: String?, // URL برای مشاهده در مرورگر
    val explorers: List<String>, // لیست Base URL های API اکسپلوررها
    val color: String? = null, // رنگ شبکه به صورت هگز
    val faName: String? = null // نام فارسی شبکه
)
