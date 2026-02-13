package com.mtd.core.model

data class WalletKey(
    val networkName: NetworkName,
    val networkType: NetworkType,
    val chainId: Long?,             // برای EVM ها
    val derivationPath: String?,     // مسیر BIP (مثلاً m/44'/60'/0'/0/0)
    val address: String,            // آدرس نهایی
    val publicKeyHex: String,
    var symbol: String? = null      // نماد ارز
)