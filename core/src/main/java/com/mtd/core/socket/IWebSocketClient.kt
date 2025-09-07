// در data/socket/RawWebSocketClient.kt

package com.mtd.core.socket

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import javax.inject.Inject

// این اینترفیس برای تست‌پذیری بهتر است
interface IWebSocketClient {
    fun connect(url: String, listener: WebSocketListener)
    fun disconnect()
    fun send(message: String): Boolean
}

class RawWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) : IWebSocketClient {

    private var webSocket: WebSocket? = null
    private val TAG = "RawWebSocketClient"
    private val NORMAL_CLOSURE_STATUS = 1000

    override fun connect(url: String, listener: WebSocketListener) {
        if (webSocket != null) {
            Timber.tag(TAG).w("Already connected or connecting. Please disconnect first.")
            return
        }

        Timber.tag(TAG).d("Connecting to $url...")
        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    override fun disconnect() {
        webSocket?.close(NORMAL_CLOSURE_STATUS, "Disconnected by client")
        webSocket = null
        Timber.tag(TAG).d("WebSocket disconnected.")
    }

    override fun send(message: String): Boolean {
        return webSocket?.send(message) == true
    }
}