package com.mtd.core.model

data class WalletKey(
    val networkName: String,
    val networkType: NetworkType,
    val chainId: Long?,             // برای EVM ها
    val derivationPath: String,     // مسیر BIP (مثلاً m/44'/60'/0'/0/0)
    val address: String,            // آدرس نهایی
    val privateKeyHex: String,      // کلید خصوصی به هگز
    val publicKeyHex: String        // کلید عمومی به هگز (در صورت نیاز)
)