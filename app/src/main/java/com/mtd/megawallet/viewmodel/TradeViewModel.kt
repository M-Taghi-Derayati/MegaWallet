package com.mtd.megawallet.viewmodel

import com.mtd.data.socket.OrderBookSocketManager
import com.mtd.data.socket.SocketEventOrder
import com.mtd.domain.model.AggregatedOrderDto
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.event.OrderBookRow
import com.mtd.megawallet.event.TradeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class TradeViewModel @Inject constructor(
    private val orderBookSocketManagerFactory: OrderBookSocketManager.Factory,
    errorManager: com.mtd.core.manager.ErrorManager
) : BaseViewModel(errorManager) {

    private val orderBookSocketManager = orderBookSocketManagerFactory.create()


    private val _uiState = MutableStateFlow<TradeUiState>(TradeUiState.Loading)
    val uiState = _uiState.asStateFlow()


    private var currentMarket: String = "ETHUSDT" // یک مقدار پیش‌فرض اولیه
    private var listenerJob: Job? = null


    init {

        connectAndSubscribe()
        listenToUpdates()
    }

    /**
     * --- تابع جدید و کلیدی ---
     * این تابع باید توسط Activity/Fragment پس از ساخت ViewModel فراخوانی شود.
     * بازار مورد نظر را تنظیم کرده و فرآیند دریافت داده را آغاز می‌کند.
     * @param marketSymbol نماد بازار (e.g., "ETH-USDT")
     */
    fun initialize(marketSymbol: String?) {
        // اگر marketSymbol معتبر بود، آن را جایگزین مقدار پیش‌فرض کن
        val marketToLoad = if (!marketSymbol.isNullOrBlank()) marketSymbol else "ETHUSDT"

        // اگر بازار تغییر نکرده، کاری انجام نده
        if (this.currentMarket == marketToLoad && _uiState.value !is TradeUiState.Loading) {
            return
        }

        this.currentMarket = marketToLoad
        _uiState.value = TradeUiState.Loading // نمایش حالت بارگذاری

        connectAndSubscribe()
        listenToUpdates()
    }


    private fun connectAndSubscribe() {
        orderBookSocketManager.connect()
        orderBookSocketManager.unsubscribeFromMarket()
        orderBookSocketManager.subscribeToMarket(currentMarket)
    }

    private fun listenToUpdates() {
        // برای جلوگیری از ایجاد چندین collector، ابتدا job قبلی را لغو می‌کنیم
        listenerJob?.cancel()
        listenerJob = launchSafe {
            orderBookSocketManager.events.collect { event ->
                if (event is SocketEventOrder.OrderBookUpdate && event.market.equals(
                        currentMarket,
                        ignoreCase = true
                    )
                ) {
                    val orderBookData = event.data

                    // --- بخش کامل شده: تبدیل DTO به مدل UI ---
                    val bidsForUi = orderBookData.bids.map { it.toOrderBookRow() }
                    val asksForUi = orderBookData.asks.map { it.toOrderBookRow() }

                    val lastPrice = calculateLastPrice(bidsForUi, asksForUi)
                    // --- پایان بخش کامل شده ---

                    _uiState.update {
                        // اگر state از قبل Success بود، آن را آپدیت می‌کنیم، در غیر این صورت یک state جدید می‌سازیم
                        val currentState = it as? TradeUiState.Success


                        currentState?.copy(
                            bids = bidsForUi,
                            asks = asksForUi,
                            lastPrice = lastPrice
                        ) ?: TradeUiState.Success(
                            marketSymbol = currentMarket,
                            lastPrice = lastPrice,
                            bids = bidsForUi,
                            asks = asksForUi,

                            )
                    }
                }
            }
        }
    }


    // --- توابع مربوط به تعامل کاربر ---

    fun onAmountChange(newAmount: String) {
        _uiState.update {
            if (it is TradeUiState.Success) it.copy(amount = newAmount) else it
        }
    }

    fun onPriceChange(newPrice: String) {
        _uiState.update {
            if (it is TradeUiState.Success) it.copy(price = newPrice) else it
        }
    }

    fun placeMarketOrder() {
        val currentState = _uiState.value as? TradeUiState.Success ?: return
        if (currentState.amount.isBlank()) return

        _uiState.update { currentState.copy(isSubmitting = true) }

        // TODO: در اینجا باید جریان کاری سواپ را که قبلاً در SwapViewModel ساختیم، فراخوانی کنیم.
        // این کار نیازمند بازسازی و انتقال منطق به یک UseCase یا ViewModel مشترک است.
        // launchSafe {
        //     sharedExecutionViewModel.startSwap(
        //         fromAsset = currentState.fromAssetSymbol,
        //         toAsset = currentState.toAssetSymbol,
        //         amount = currentState.amount.toDouble()
        //     )
        // }
    }

    // --- توابع کمکی ---

    private fun AggregatedOrderDto.toOrderBookRow(): OrderBookRow {
        return OrderBookRow(
            price = this.price.toString(),
            quantity = this.quantity.toString(),
            exchangeIds = listOf(this.exchangeId), // فعلاً فقط یک صرافی
            depth = 0.0 // محاسبه عمق نیاز به پردازش کل لیست دارد
        )
    }

    private fun calculateLastPrice(bids: List<OrderBookRow>, asks: List<OrderBookRow>): String {
        val bestBid = bids.firstOrNull()?.price?.toDoubleOrNull() ?: 0.0
        val bestAsk = asks.firstOrNull()?.price?.toDoubleOrNull() ?: 0.0

        if (bestBid > 0 && bestAsk > 0) {
            return ((bestBid + bestAsk) / 2).toString()
        }
        return "..."
    }

    override fun onCleared() {
        super.onCleared()
        orderBookSocketManager.unsubscribeFromMarket()
        // اگر لازم باشد اتصال را کاملاً قطع کنیم: socketManager.disconnect()
    }
}