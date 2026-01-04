package com.mtd.domain.model

import com.mtd.core.assets.AssetConfig
import com.mtd.core.registry.AssetRegistry

// این کلاس حالا به AssetRegistry وابسته است تا بتواند AssetConfig های کامل را بگیرد
class SwapConfig(private val assetRegistry: AssetRegistry) {

    // ما مسیرها را با ID های رشته‌ای تعریف می‌کنیم
     val supportedRoutesRaw = listOf(
        "USDT-SEPOLIA" to "ETH-SEPOLIA",
        "USDT-BSC_TESTNET" to "ETH-SEPOLIA",
        "ETH-SEPOLIA" to "USDT-SEPOLIA",
        "ETH-SEPOLIA" to "USDT-BSC_TESTNET",
        "USDT-SEPOLIA" to "BNB-BSC_TESTNET",
        "USDT-BSC_TESTNET" to "BNB-BSC_TESTNET",
        "BTC-BITCOIN_TESTNET" to "USDT-SEPOLIA",
        "USDT-SEPOLIA" to "BTC-BITCOIN_TESTNET",
        "USDT-BSC_TESTNET" to "BTC-BITCOIN_TESTNET",
    )

    /**
     * لیستی از تمام دارایی‌های مبدا ممکن را به صورت AssetConfig کامل برمی‌گرداند.
     * این تابع برای پر کردن لیست اولیه انتخاب ارز مبدا استفاده می‌شود.
     */
    fun getAvailableFromAssets(): List<AssetConfig> {
        return supportedRoutesRaw
            .map { it.first } // گرفتن تمام ID های مبدا
            .distinct()      // حذف موارد تکراری
            .mapNotNull { assetRegistry.getAssetById(it) } // تبدیل ID به AssetConfig کامل
    }

    /**
     * با گرفتن AssetConfig دارایی مبدا، لیستی از تمام دارایی‌های مقصد ممکن را
     * به صورت AssetConfig کامل برمی‌گرداند.
     * این تابع برای فیلتر کردن لیست ارز مقصد استفاده می‌شود.
     */
    fun getPossibleToAssets(fromAsset: AssetConfig): List<AssetConfig> {
        return supportedRoutesRaw
            .filter { it.first == fromAsset.id } // پیدا کردن تمام مسیرهای ممکن
            .map { it.second }                   // گرفتن ID های مقصد
            .distinct()
            .mapNotNull { assetRegistry.getAssetById(it) } // تبدیل ID به AssetConfig کامل
    }
}