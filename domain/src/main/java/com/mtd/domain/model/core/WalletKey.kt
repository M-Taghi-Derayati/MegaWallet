package com.mtd.domain.model.core

data class WalletKey(
    /**
     * TASK-53 — شناسهٔ کانونیِ شبکه (مثل `"sepolia"` یا `"base_mainnet"`).
     * هویت از این‌جا می‌آید، نه از [networkName]. شبکه‌ای که سرور در باندلِ امضاشده اضافه می‌کند
     * ممکن است اصلاً معادلی در enum نداشته باشد.
     */
    val networkId: String,
    val networkType: NetworkType,
    val chainId: Long?,             // برای EVM ها
    val derivationPath: String?,     // مسیر BIP (مثلاً m/44'/60'/0'/0/0)
    val address: String,            // آدرس نهایی
    val publicKeyHex: String,
    var symbol: String? = null,     // نماد ارز
    /**
     * نامِ قدیمیِ شبکه؛ صرفاً یک alias نمایشی/سازگاری است و برای شبکه‌های فقط-در-باندل `null` است.
     * هیچ‌وقت برای تطبیق هویت استفاده نکنید — [networkId] را مقایسه کنید.
     */
    val networkName: NetworkName? = null
)
