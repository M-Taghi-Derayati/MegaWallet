package com.mtd.data.socket

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mtd.data.di.ForWebSocket
import com.mtd.data.di.NetworkModule.serverIp
import com.mtd.domain.model.AggregatedOrderBookDto
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import timber.log.Timber
import kotlin.math.pow

/**
 * مدیریت اتصال WebSocket برای یک بازار خاص جهت دریافت آپدیت‌های Order Book.
 * این کلاس Singleton نیست و چرخه حیات آن به ViewModel مربوطه گره خورده است.
 */
class OrderBookSocketManager @AssistedInject constructor(
    @ForWebSocket private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    @AssistedFactory
    interface Factory {
        fun create(): OrderBookSocketManager
    }

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentMarket: String? = null
    private var shouldBeConnected = false // پرچم برای کنترل وضعیت اتصال
    private var reconnectAttempts = 0

    private val _events = MutableSharedFlow<SocketEventOrder.OrderBookUpdate>()
    val events = _events.asSharedFlow()

    private val serverUrl = "ws://${serverIp}:3000"

    /**
     * اتصال را آغاز کرده و پرچم اتصال را فعال می‌کند.
     */
    fun connect() {
        if (shouldBeConnected) return // اگر از قبل در حال تلاش برای اتصال هستیم، کاری نکن
        shouldBeConnected = true
        attemptConnection()
    }

    /**
     * اتصال را قطع کرده و پرچم اتصال را غیرفعال می‌کند تا از تلاش مجدد جلوگیری شود.
     */
    fun disconnect() {
        shouldBeConnected = false
        reconnectAttempts = 0 // ریست کردن شمارنده
        Timber.d("[OrderBook] Disconnecting from market: $currentMarket")
        webSocket?.close(1000, "Client disconnected gracefully")
        webSocket = null
    }

    /**
     * تلاش می‌کند یک اتصال جدید برقرار کند. این تابع به صورت داخلی استفاده می‌شود.
     */
    private fun attemptConnection() {
        if (webSocket != null) return // اگر از قبل متصل است، کاری نکن
        if (!shouldBeConnected) return // اگر قرار نیست متصل باشیم، تلاش نکن

        Timber.d("[OrderBook] Attempting to connect... (Attempt #${reconnectAttempts + 1})")
        val request = Request.Builder().url(serverUrl).build()
        webSocket = okHttpClient.newWebSocket(request, OrderBookSocketListener())
    }

    fun subscribeToMarket(marketSymbol: String) {
        this.currentMarket = marketSymbol
        val message = JSONObject().apply {
            put("action", "subscribe")
            put("market", marketSymbol)
        }.toString()

        if (webSocket?.send(message) == true) {
            Timber.i("[OrderBook] Sent subscribe request for market: $marketSymbol")
        } else {
            Timber.w("[OrderBook] Failed to send subscribe request. WebSocket not connected or ready.")
            // اگر اتصال برقرار نبود، پس از اتصال موفق در onOpen، اشتراک به صورت خودکار ارسال می‌شود.
        }
    }

    fun unsubscribeFromMarket() {
        currentMarket?.let { market ->
            val message = JSONObject().apply {
                put("action", "unsubscribe")
                put("market", market)
            }.toString()
            webSocket?.send(message)
            Timber.i("[OrderBook] Sent unsubscribe request for market: $market")
        }
    }

    private inner class OrderBookSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Timber.i("[OrderBook] ✅ WebSocket connection opened.")
            // پس از اتصال موفق، شمارنده تلاش مجدد را ریست می‌کنیم
            reconnectAttempts = 0
            // اگر بازاری برای اشتراک مشخص شده بود، درخواست را ارسال می‌کنیم
            currentMarket?.let { subscribeToMarket(it) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = JSONObject(text)
                if (json.optString("type") == "ORDER_BOOK_UPDATE") {
                    val event = gson.fromJson(text, SocketEventOrder.OrderBookUpdate::class.java)
                    if (event.market.equals(currentMarket, ignoreCase = true)) {
                        scope.launch { _events.emit(event) }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[OrderBook] Failed to parse OrderBookUpdate message.")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Timber.w("[OrderBook] 🔌 Connection closing: $reason")
            this@OrderBookSocketManager.webSocket = null
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.e(t, "[OrderBook] ❌ Connection failed.")
            this@OrderBookSocketManager.webSocket = null

            // --- منطق تلاش مجدد خودکار ---
            if (shouldBeConnected) {
                reconnectAttempts++
                // تأخیر نمایی: 2s, 4s, 8s, 16s, ... تا حداکثر 60 ثانیه
                val delayMillis = (2000 * 2.0.pow((reconnectAttempts - 1).toDouble())).toLong()
                val finalDelay = delayMillis.coerceAtMost(60000L)

                Timber.w("[OrderBook] Will attempt to reconnect in ${finalDelay / 1000} seconds.")

                scope.launch {
                    delay(finalDelay)
                    attemptConnection()
                }
            }
            // --- پایان منطق تلاش مجدد ---
        }
    }
}

/**
 * یک رابط مهر و موم شده (sealed interface) که تمام رویدادهای ممکنی که
 * می‌توان از طریق WebSocket دریافت کرد را مدل‌سازی می‌کند.
 */
sealed interface SocketEventOrder {

    /**
     * رویداد خوش‌آمدگویی که بلافاصله پس از اتصال موفق ارسال می‌شود.
     * برای تست و تأیید اتصال کاربرد دارد.
     */
    data object Welcome : SocketEventOrder

    /**
     * رویداد آپدیت دفتر سفارشات (Order Book).
     * این رویداد توسط OrderBookSocketManager دریافت می‌شود.
     * @param market نماد بازاری که این آپدیت به آن تعلق دارد (e.g., "ETH-USDT").
     * @param data خود داده‌های Order Book تجمیع شده.
     */
    data class OrderBookUpdate(
        @SerializedName("market") val market: String,
        @SerializedName("data") val data: AggregatedOrderBookDto
    ) : SocketEventOrder

    /**
     * رویداد تأیید واریز برای سواپ‌های UTXO (مانند بیت‌کوین).
     * این رویداد توسط NotificationSocketManager دریافت می‌شود.
     * @param quoteId شناسه پیش‌فاکتوری که این واریز به آن مرتبط است.
     * @param txHash هش تراکنش واریزی کاربر.
     * @param amount مقدار واریز شده به واحد اصلی (e.g., BTC).
     * @param asset نماد دارایی واریز شده (e.g., "BTC").
     */
    data class DepositConfirmed(
        @SerializedName("quoteId") val quoteId: String,
        @SerializedName("txHash") val txHash: String,
        @SerializedName("amount") val amount: Double,
        @SerializedName("asset") val asset: String
    ) : SocketEventOrder

    /**
     * رویداد تکمیل موفقیت‌آمیز یک معامله.
     * این رویداد توسط NotificationSocketManager دریافت می‌شود.
     * @param tradeId شناسه معامله در سیستم ما.
     * @param finalTxHash هش تراکنش پرداخت نهایی به کاربر.
     */
    data class TradeCompleted(
        @SerializedName("tradeId") val tradeId: String,
        @SerializedName("finalTxHash") val finalTxHash: String
    ) : SocketEventOrder

    /**
     * رویداد شکست یک معامله.
     * این رویداد توسط NotificationSocketManager دریافت می‌شود.
     * @param tradeId شناسه معامله ناموفق.
     * @param reason دلیل شکست (یک پیام قابل نمایش برای کاربر یا دیباگ).
     */
    data class TradeFailed(
        @SerializedName("tradeId") val tradeId: String,
        @SerializedName("reason") val reason: String
    ) : SocketEventOrder

    /**
     * یک رویداد داخلی برای نمایش پیام داخل برنامه‌ای (Snackbar/Toast).
     * این رویداد از سرور نمی‌آید، بلکه توسط خود SocketManager تولید می‌شود.
     */
    data class ShowInAppMessage(
        val title: String,
        val message: String
    ) : SocketEventOrder

    /**
     * برای پیام‌های ناشناخته یا نامعتبر از سرور.
     */
    data object Unknown : SocketEventOrder
}