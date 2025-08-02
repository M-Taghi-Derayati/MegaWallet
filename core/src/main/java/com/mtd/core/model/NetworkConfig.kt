package com.mtd.core.model

data class NetworkConfig(
    val name: String,
    val networkType: String,   // چون Gson enum رو راحت نمی‌خونه، اول رشته بگیریم و بعد تبدیل کنیم
    val chainId: Long,
    val derivationPath: String
)