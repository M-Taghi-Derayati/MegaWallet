package com.mtd.megawallet.event

import com.mtd.core.assets.AssetConfig
import java.math.BigDecimal
import java.math.BigInteger

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Success(
        val totalBalanceUsd: String,
        val isUpdating: Boolean,
        val assets: List<AssetItem>,
        val recentActivity: List<ActivityItem>
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()


/**
 * مدل داده برای نمایش یک دارایی (Asset) در لیست‌ها.
 * این مدل در صفحه اصلی و صفحه انتخاب دارایی برای ارسال استفاده می‌شود.
 *
 * @param id یک شناسه یکتا برای هر دارایی در هر شبکه (e.g., "ETH-SEPOLIA").
 * @param name نام قابل نمایش دارایی (e.g., "Ethereum").
 * @param symbol نماد دارایی (e.g., "ETH").
 * @param networkName نام شبکه قابل نمایش (e.g., "on Sepolia").
 * @param networkId شناسه شبکه از رجیستری (برای استفاده‌های داخلی).
 * @param iconUrl آدرس URL آیکون دارایی.
 * @param balance مقدار موجودی به صورت رشته فرمت‌شده (e.g., "10.0 ETH").
 * @param balanceUsd ارزش دلاری موجودی به صورت رشته فرمت‌شده (e.g., "$30,000.00").
 * @param priceChange24h درصد تغییرات قیمت در ۲۴ ساعت گذشته.
 */
data class AssetItem(
    // --- شناسه‌ها ---
    val id: String, // شناسه یکتا مثل "ETH-SEPOLIA"
    val networkId: String, // شناسه شبکه از رجیستری

    // --- اطلاعات نمایشی ---
    val name: String, // "Ethereum"
    val symbol: String, // "ETH"
    val networkName: String, // "on Sepolia"
    val iconUrl: String?,

    // --- اطلاعات موجودی (نمایشی) ---
    val balance: String, // "10.0 ETH"
    val balanceUsd: String, // "$30,000.00"

    // --- اطلاعات قیمت (نمایشی) ---
    val priceChange24h: Double=0.0, // e.g., 2.84 for +2.84%

    // --- داده‌های خام برای محاسبات ---
    val balanceRaw: BigDecimal= BigDecimal.ZERO, // مقدار عددی موجودی
    val priceUsdRaw: BigDecimal= BigDecimal.ZERO, // قیمت هر واحد
    val decimals: Int=0, // تعداد ارقام اعشار
    val contractAddress: String?=null, // آدرس قرارداد (برای توکن‌ها)
    val isNativeToken: Boolean=false // مشخص می‌کند که آیا توکن اصلی شبکه است یا خیر
)


/**
 * مدل داده برای نمایش یک آیتم در لیست فعالیت‌های اخیر.
 *
 * @param id هش تراکنش.
 * @param type نوع تراکنش (ارسال، دریافت، ...).
 * @param title عنوان اصلی (e.g., "ارسال بیت‌کوین").
 * @param subtitle توضیحات (e.g., "به: bc1q...xyz").
 * @param amount مقدار تراکنش با نماد ارز (e.g., "-0.01 tBTC").
 * @param amountUsd ارزش دلاری تراکنش (e.g., "-$600.00").
 * @param iconUrl آدرس URL آیکون مربوط به تراکنش.
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
 *
 * @param level سطح کارمزد (e.g., "Normal", "Fast").
 * @param feeAmount مقدار کارمزد با نماد ارز (e.g., "0.000072 ETH").
 * @param feeAmountUsd ارزش دلاری کارمزد (e.g., "$0.15").
 * @param estimatedTime زمان تخمینی تایید تراکنش.
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

}