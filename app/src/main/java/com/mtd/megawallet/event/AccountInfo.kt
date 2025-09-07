package com.mtd.megawallet.event

data class AccountInfo(
    val id: String, // یک شناسه یکتا، مثلا "BITCOINTESTNET"
    val networkName: String, // نام قابل نمایش، مثلا "Bitcoin Testnet"
    val address: String, // آدرس کوتاه شده، مثلا "tb1q...cl2z"
    val balance: String, // موجودی فرمت شده، مثلا "0.0019 tBTC"
    val balanceUsd: String, // ارزش دلاری، مثلا "$50.12 USD"
    val iconUrl: String? = null, // URL آیکون شبکه
    val derivationPath: String, // مسیر استخراج برای استفاده در آینده
    val isSelected: Boolean = true
)