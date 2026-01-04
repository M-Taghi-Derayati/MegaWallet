package com.mtd.megawallet.event

import com.mtd.core.assets.AssetConfig
import java.math.BigDecimal
import java.math.BigInteger

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Success(
        val totalBalanceUsdt: String,
        val totalBalanceIrr: String = "...",
        val tetherPriceIrr: String = "...",
        val isUpdating: Boolean,
        val assets: List<AssetItem>,
        val recentActivity: List<ActivityItem>,
        val displayCurrency: DisplayCurrency = DisplayCurrency.USDT
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()

    enum class DisplayCurrency {
         IRR, USDT
    }
}

/**
 * مدل داده برای نمایش یک دارایی (Asset) در لیست‌ها.
 * این مدل در صفحه اصلی و صفحه انتخاب دارایی برای ارسال استفاده می‌شود.
 */
data class AssetItem(
    // --- شناسه‌ها ---
    val id: String, // شناسه یکتا مثل "ETH-SEPOLIA"
    val networkId: String, // شناسه شبکه از رجیستری

    // --- اطلاعات نمایشی ---
    val name: String, // "Ethereum"
    val faName: String? = null, // نام فارسی ارز
    val symbol: String, // "ETH"
    val networkName: String, // "on Sepolia"
    val networkFaName: String? = null, // نام فارسی شبکه
    val iconUrl: String?,

    // --- اطلاعات موجودی (نمایشی) ---
    val balance: String, // "10.0 ETH"
    val balanceUsdt: String, // "$30,000.00"
    val balanceIrr: String = "...",
    val formattedDisplayBalance: String = balanceUsdt, // فیلد جدید برای نمایش در UI

    // --- اطلاعات قیمت (نمایشی) ---
    val priceChange24h: Double=0.0, // e.g., 2.84 for +2.84%

    // --- داده‌های خام برای محاسبات ---
    val balanceRaw: BigDecimal= BigDecimal.ZERO, // مقدار عددی موجودی
    val priceUsdRaw: BigDecimal= BigDecimal.ZERO, // قیمت هر واحد
    val decimals: Int=0, // تعداد ارقام اعشار
    val contractAddress: String?=null, // آدرس قرارداد (برای توکن‌ها)
    val isNativeToken: Boolean=false, // مشخص می‌کند که آیا توکن اصلی شبکه است یا خیر

    // --- فیلدهای مربوط به گروه‌بندی (Aggregation) ---
    val isGroupHeader: Boolean = false, // آیا این آیتم نماینده یک گروه است؟
    val groupName: String? = null, // نام گروه (مثلاً "Tether")
    val groupAssets: List<AssetItem> = emptyList(), // لیست زیرمجموعه‌های گروه
    val isExpanded: Boolean = false, // وضعیت باز/بسته بودن گروه (فقط برای نمایش در UI)
    
    // --- توزیع شبکه‌ها (برای نمایش Circle Chart) ---
    val networkDistribution: List<NetworkShare> = emptyList() // نسبت موجودی در هر شبکه
)

/**
 * اطلاعات سهم هر شبکه در یک گروه (برای نمایش Circle Chart)
 */
data class NetworkShare(
    val networkId: String,
    val networkName: String,
    val colorHex: String, // رنگ شبکه به صورت Hex (مثل "#1E88E5")
    val percentage: Float // درصد از کل (0-100)
)


/**
 * مدل داده برای نمایش یک آیتم در لیست فعالیت‌های اخیر.
 */
data class ActivityItem(
    val id: String,
    val type: ActivityType,
    val title: String,
    val subtitle: String,
    val amount: String,
    val amountUsd: String,
    val iconUrl: String?
)

data class AssetWithBalance(val config: AssetConfig, val balance: BigInteger)

enum class ActivityType { SEND, RECEIVE, SWAP, MINT, UNKNOWN }

/**
 * مدل داده برای نمایش گزینه‌های کارمزد در صفحه ارسال.
 */
data class FeeOption(
    val level: String,
    val feeAmountDisplay: String, // "0.000072 ETH"
    val feeAmountUsdDisplay: String, // "$0.15"
    val estimatedTime: String,
    // فیلدهای داده خام برای ارسال تراکنش
    val feeInSmallestUnit: BigInteger,
    val gasPrice: BigInteger? = null,
    val gasLimit: BigInteger? = null,
    val feeRateInSatsPerByte: Long? = null
)