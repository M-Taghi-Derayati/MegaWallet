package com.mtd.megawallet.event// در پکیج com.mtd.megawallet.event

import com.mtd.domain.model.Quote
import java.math.BigDecimal

// کلاس کمکی برای نگهداری اطلاعات دارایی در UI
data class SwapAsset(
    val assetId: String,
    val symbol: String,
    val iconUrl: String?,
    val networkName: String,
    val balance: BigDecimal // موجودی به صورت عددی برای محاسبات
)

sealed class SwapUiState {
    // حالت اولیه، در حال دریافت اطلاعات اولیه مثل جفت‌ارزها
    data object InitialLoading : SwapUiState()

    // حالت اصلی و داینامیک
    data class Ready(
        // لیست‌های پویا برای انتخاب‌گرها
        val fromAssets: List<SwapAsset>,
        val toAssets: List<SwapAsset>,
        // انتخاب‌های فعلی کاربر
        val selectedFromAsset: SwapAsset?,
        val selectedToAsset: SwapAsset?,
        // فیلدهای مقدار و قیمت
        val amountIn: String = "",
        val amountOut: String = "",
        val quote: Quote? = null,
        val isQuoteLoading: Boolean = false,
        // کنترل UI
        val isToAssetSelectorEnabled: Boolean = false,
        val isAmountInputEnabled: Boolean = false,
        val isButtonEnabled: Boolean = false,
        val error: String? = null
    ) : SwapUiState()

    // حالت نمایش جزئیات نهایی برای تایید کاربر
    data class Confirmation(
        val fromDisplay: String, // e.g., "100.00 USDT"
        val toDisplay: String,   // e.g., "98.50 MATIC"
        val feeDisplay: String,  // e.g., "Fee: 1.50 USDT"
        val fromNetworkIcon: String?,
        val toNetworkIcon: String?,
        // quote را برای استفاده در execute نگه می‌داریم
        internal val quote: Quote 
    ) : SwapUiState()

    // حالت‌های میانی در حین اجرای معامله
    data class InProgress(val message: String) : SwapUiState()

    // حالت‌های نهایی
    data class Success(val tradeId: String, val message: String) : SwapUiState()
    data class Failure(val errorMessage: String) : SwapUiState()

    // حالت جدید برای سواپ‌های UTXO (مثل بیت‌کوین)
    data class WaitingForDeposit(
        val quote: Quote, // تمام اطلاعات قیمت را نگه می‌داریم
        val amountToDeposit: String, // e.g., "0.015 BTC"
        val depositAddress: String
    ) : SwapUiState()
}

sealed class SwapNavigationEvent {
    data class NavigateToSendScreen(
        val assetId: String,
        val recipientAddress: String,
        val amount: String,
        val isReadOnly: Boolean = true // برای اینکه کاربر نتواند آدرس و مقدار را تغییر دهد
    ) : SwapNavigationEvent()
}