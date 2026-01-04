package com.mtd.megawallet.event// در پکیج com.mtd.megawallet.event

import com.mtd.core.model.QuoteResponse
import com.mtd.core.model.QuoteResponse.ReceivingOptionDto
import com.mtd.megawallet.event.SwapUiState.AssetSelectItem
import java.math.BigDecimal

// کلاس کمکی برای نگهداری اطلاعات دارایی در UI
data class SwapAsset(
    val assetId: String,
    val symbol: String,
    val iconUrl: String?,
    val networkName: String,
    val networkId: String,
    val balance: BigDecimal // موجودی به صورت عددی برای محاسبات
)

// یک آیتم در لیست انتخاب دارایی می‌تواند یا یک هدر باشد یا خود دارایی
sealed interface AssetSelectionListItem {
    data class Header(val networkName: String) : AssetSelectionListItem
    data class Asset(val item: AssetSelectItem) : AssetSelectionListItem
}


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
        val fromNetworkId: String?,
        // فیلدهای مقدار و قیمت
        val amountIn: String = "",
        val amountOut: String = "",
        val selectedOption:ReceivingOptionDto?=null,
        val receivingOptionsForUI: List<ReceivingOptionUI> = emptyList(),
        val quote: QuoteResponse? = null,
        val isQuoteLoading: Boolean = false,
        val isBottomSheetVisible: Boolean = false,
        val assetsForSelection: List<AssetSelectionListItem> = emptyList(),
        val bottomSheetTitle: String = "",
        val searchQuery: String = "",
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
        internal val fromNetworkId: String,
        // quote را برای استفاده در execute نگه می‌داریم
        internal val quote: QuoteResponse,
        internal val selectedOption: ReceivingOptionDto?,
        val selectedFromAsset: SwapAsset?
    ) : SwapUiState()

    // حالت‌های میانی در حین اجرای معامله
    data class InProgress(val message: String) : SwapUiState()

    // حالت‌های نهایی
    data class Success(val tradeId: String, val message: String) : SwapUiState()
    data class Failure(val errorMessage: String) : SwapUiState()

    // حالت جدید برای سواپ‌های UTXO (مثل بیت‌کوین)
    data class WaitingForDeposit(
        val quote: QuoteResponse , // تمام اطلاعات قیمت را نگه می‌داریم
        val amountToDeposit: String,
        val assetToDeposit: AssetItem,// e.g., "0.015 BTC"
        val depositAddress: String
    ) : SwapUiState()

    data class AssetSelectItem(
        val assetId: String,
        val name: String,
        val symbol: String,
        val iconUrl: String?,
        val networkName: String,
        val balance: String
    )

    data class ReceivingOptionUI(
        val option: ReceivingOptionDto,
        val isSelected: Boolean
    )
}

sealed class SwapNavigationEvent {
    data class NavigateToSendScreen(
        val assetId: String,
        val recipientAddress: String,
        val amount: String,
        val isReadOnly: Boolean = true // برای اینکه کاربر نتواند آدرس و مقدار را تغییر دهد
    ) : SwapNavigationEvent()
}