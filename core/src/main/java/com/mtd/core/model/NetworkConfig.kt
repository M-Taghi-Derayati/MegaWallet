package com.mtd.core.model

data class NetworkConfig(
    val id: String,
    val name: String,
    val networkType: String,
    val chainId: Long?,
    val derivationPath: String,
    val rpcUrls: List<String>,
    val currencySymbol: String,
    val blockExplorerUrl: String?, // URL برای مشاهده در مرورگر
    val explorers: List<String> // لیست Base URL های API اکسپلوررها
)
