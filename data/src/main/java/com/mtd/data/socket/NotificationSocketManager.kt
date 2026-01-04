package com.mtd.data.socket

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.mtd.core.notification.NotificationService
import com.mtd.data.di.ForWebSocket
import com.mtd.data.di.NetworkModule.serverIp
import dagger.hilt.android.qualifiers.ApplicationContext
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class NotificationSocketManager @Inject constructor(
    @ForWebSocket private val okHttpClient: OkHttpClient, // استفاده از OkHttpClient مخصوص WebSocket
    private val notificationService: NotificationService,
    private val gson: Gson,
    @ApplicationContext private val context: Context
) {
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var shouldBeConnected = false
    private var reconnectAttempts = 0

    private val _events = MutableSharedFlow<SocketEvent>(replay = 1) // با replay cache برای از دست نرفتن رویدادها
    val events = _events.asSharedFlow()

    private val serverUrl = "ws://${serverIp}:3000"

    /**
     * اتصال را آغاز کرده و پرچم اتصال را برای تلاش مجدد خودکار فعال می‌کند.
     */
    fun connect() {
        if (shouldBeConnected) return
        Timber.i("[NotificationSocket] connect() called. Setting shouldBeConnected to true.")
        shouldBeConnected = true
        attemptConnection()
    }

    /**
     * اتصال را به صورت دستی قطع کرده و تلاش مجدد خودکار را غیرفعال می‌کند.
     */
    fun disconnect() {
        shouldBeConnected = false
        reconnectAttempts = 0
        Timber.i("[NotificationSocket] disconnect() called. Closing connection.")
        webSocket?.close(1000, "Client session ended.")
        webSocket = null
    }

    /**
     * تابع داخلی برای تلاش جهت برقراری اتصال.
     */
    private fun attemptConnection() {
        if (webSocket != null) {
            Timber.d("[NotificationSocket] Connection already exists or is in progress.")
            return
        }
        if (!shouldBeConnected) {
            Timber.d("[NotificationSocket] shouldBeConnected is false, aborting connection attempt.")
            return
        }

        Timber.d("[NotificationSocket] Attempting to connect... (Attempt #${reconnectAttempts + 1})")
        val request = Request.Builder().url(serverUrl).build()
        webSocket = okHttpClient.newWebSocket(request, SocketListener())
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Timber.i("[NotificationSocket] ✅ WebSocket connection opened successfully.")
            // پس از اتصال موفق، شمارنده تلاش مجدد را ریست می‌کنیم.
            reconnectAttempts = 0
        }

        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        override fun onMessage(webSocket: WebSocket, text: String) {
            Timber.d("[NotificationSocket] Received message: $text")
            val event = parseSocketMessage(text)

            if (event !is SocketEvent.Unknown) {
                scope.launch { _events.emit(event) }
            }

            handleNotification(event)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Timber.w("[NotificationSocket] 🔌 Connection closing: Code=$code, Reason=$reason")
            this@NotificationSocketManager.webSocket = null
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.e(t, "[NotificationSocket] ❌ Connection failed.")
            this@NotificationSocketManager.webSocket = null

            // --- منطق تلاش مجدد خودکار (Exponential Backoff) ---
            if (shouldBeConnected) {
                reconnectAttempts++
                // تأخیر: 2s, 4s, 8s, 16s, ... تا حداکثر 60 ثانیه
                val delayMillis = (2000 * 2.0.pow(reconnectAttempts - 1)).toLong()
                val finalDelay = delayMillis.coerceAtMost(60_000L) // حداکثر 1 دقیقه

                Timber.w("[NotificationSocket] Will attempt to reconnect in ${finalDelay / 1000} seconds.")

                scope.launch {
                    delay(finalDelay)
                    attemptConnection()
                }
            }
        }
    }

    private fun parseSocketMessage(text: String): SocketEvent {
        return try {
            val type = JSONObject(text).optString("type")
            when (type) {
                "WELCOME" -> SocketEvent.Welcome
                "DEPOSIT_CONFIRMED" -> gson.fromJson(text, SocketEvent.DepositConfirmed::class.java)
                "TRADE_COMPLETED" -> gson.fromJson(text, SocketEvent.TradeCompleted::class.java)
                "TRADE_FAILED" -> gson.fromJson(text, SocketEvent.TradeFailed::class.java)
                else -> SocketEvent.Unknown
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse socket message.")
            SocketEvent.Unknown
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun handleNotification(event: SocketEvent) {
        // برای نوتیفیکیشن به اجازه نیاز داریم
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        when (event) {
            is SocketEvent.DepositConfirmed -> notificationService.showTradeNotification(
                "واریز تأیید شد",
                "مقدار ${event.amount} ${event.asset} دریافت شد."
            )
            is SocketEvent.TradeCompleted -> notificationService.showTradeNotification(
                "معامله تکمیل شد",
                "دارایی شما با موفقیت ارسال شد."
            )
            is SocketEvent.TradeFailed -> notificationService.showTradeNotification(
                "معامله ناموفق",
                "خطا: ${event.reason}"
            )
            else -> { /* برای رویدادهای دیگر نوتیفیکیشن نمی‌خواهیم */ }
        }
    }
}

// مدل‌های داده SocketEvent را هم در همین فایل یا فایل جداگانه تعریف کنید
sealed interface SocketEvent {
    data object Welcome : SocketEvent
    data class TradeCompleted(val tradeId: String, val finalTxHash: String) : SocketEvent
    data class TradeFailed(val tradeId: String, val reason: String) : SocketEvent
    data class DepositConfirmed(val quoteId: String, val txHash: String, val amount: Double, val asset: String) : SocketEvent
    data object Unknown : SocketEvent
}