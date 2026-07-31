package com.mtd.domain.model.core

enum class NetworkType {
    EVM,
    BITCOIN,
    TVM,
    SOLANA,
    XRP,
    UTXO,
    TON,
    OTHER
}

/**
 * TASK-53 — **این enum یک alias قدیمیِ نمایشی است، نه هویتِ شبکه.**
 *
 * هویتِ کانونیِ شبکه `networkId: String` است (مثل `"base_mainnet"`). شبکه‌ای که سرور در باندلِ
 * امضاشده اضافه می‌کند لازم نیست این‌جا ثابتی داشته باشد، و نبودش نباید باعث حذف یا کرش شود.
 * برای همین همه‌جا از [fromConfigName] استفاده می‌شود که `null` برمی‌گرداند، نه `valueOf` که
 * `IllegalArgumentException` پرتاب می‌کند.
 *
 * ثابت جدید به این enum اضافه **نکنید** مگر برای کدی که واقعاً رفتارِ مخصوصِ آن زنجیره را دارد
 * (مثل پارامترهای bitcoinj برای زنجیره‌های UTXO).
 */
enum class NetworkName {
    ETHEREUM,
    SEPOLIA,
    BSCTESTNET,
    BSC,
    BITCOINTESTNET,
    BITCOIN,
    POLTESTNET,
    TRON,
    BASE,
    BASESEPOLIA,
    ARBITRUM,
    ARBSEPOLIA,
    SHASTA,
    SOLANA,
    SOLDEVNET,
    RIPPLE,
    XRPTESTNET,
    DOGE,
    DOGETESTNET,
    LITECOIN,
    LTCTESTNET,
    TON,
    TONTEST;

    companion object {
        /**
         * تبدیلِ امنِ `config.name` به alias — بدون پرتابِ استثنا.
         * جایگزینِ `NetworkName.valueOf(...)`؛ نامِ ناشناخته یعنی «این شبکه alias ندارد»،
         * نه «این شبکه نامعتبر است».
         */
        fun fromConfigName(name: String?): NetworkName? {
            val normalized = name?.trim()?.uppercase().orEmpty()
            if (normalized.isEmpty()) return null
            return entries.find { it.name == normalized }
        }
    }
}
