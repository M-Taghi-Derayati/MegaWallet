// In: app/src/main/java/com/mtd/app/ui/send/SendUiState.kt
package com.mtd.megawallet.event



sealed class SendUiState {
    // مرحله ۱: وارد کردن آدرس
    data class EnteringAddress(
        val address: String = "",
        val isValid: Boolean = false,
        val error: String? = null
    ) : SendUiState()

    // وضعیت برای نمایش ProgressBar بین مراحل
    data class Loading(val message: String) : SendUiState()

    // مرحله ۲: انتخاب دارایی
    data class SelectingAsset(
        val recipientAddress: String,
        val compatibleAssets: List<AssetItem> = emptyList()
    ) : SendUiState()

    // مرحله ۳: وارد کردن مقدار و کارمزد
    data class EnteringDetails(
        val recipientAddress: String,
        val selectedAsset: AssetItem?=null,
        val amount: String = "",
        val amountUsd: String = "$0.00",
        val feeOptions: List<FeeOption> = emptyList(),
        val selectedFee: FeeOption? = null,
        val validationError: String? = null
    ) : SendUiState()

    // مرحله ۴: پیش‌نمایش و تایید نهایی
    data class Confirmation(
        val asset: AssetItem,
        val amount: String, // "0.01"
        val amountDisplay: String, // "0.01 ETH"
        val amountUsd: String, // "$30.00"
        val recipientAddress: String,
        val fromAddress: String,
        val fee: FeeOption,
        val totalDebit: String, // "-$30.15"
        val totalDebitAsset: String // "-0.0100076 ETH"
    ) : SendUiState()
    
    // مرحله ۵: در حال ارسال
    object Sending : SendUiState()

    // مرحله ۶: موفقیت‌آمیز
    data class Success(val txHash: String) : SendUiState()
    
    // حالت کلی خطا
    data class Error(val message: String) : SendUiState()
}
