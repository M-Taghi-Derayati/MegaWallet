package com.mtd.megawallet.event


// مدل داده برای یک ردیف در Order Book UI
data class OrderBookRow(
    val price: String,
    val quantity: String,
    val exchangeIds: List<String>, // لیستی از ID صرافی‌ها (ممکن است چند صرافی قیمت یکسان داشته باشند)
    val depth: Double // برای رسم نمودار عمق
)

sealed interface TradeUiState {
    data object Loading : TradeUiState
    data class Error(val message: String) : TradeUiState

    data class Success(
        val marketSymbol: String,
        val lastPrice: String,
        val bids: List<OrderBookRow>,
        val asks: List<OrderBookRow>,

        // وضعیت فرم سفارش
        val orderType: String = "Market", // "Market" or "Limit"
        val amount: String = "",
        val price: String = "",
        val isSubmitting: Boolean = false // برای نمایش ProgressBar روی دکمه
    ) : TradeUiState
}