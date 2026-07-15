package com.mtd.data.socket

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.mtd.core.notification.EventDeduplicationCache
import com.mtd.core.notification.NotificationService
import com.mtd.data.BuildConfig
import com.mtd.data.di.ForWebSocket
import com.mtd.domain.interfaceRepository.IAppEventBus
import com.mtd.domain.interfaceRepository.ITokenStore
import com.mtd.domain.model.AppEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Phase 5 (KAN-NEW-03) — realtime push transport. Replaces the 4-second polling loop with a single
 * authenticated WebSocket to the relayer's `/ws`:
 *
 *  - **JWT at the HTTP upgrade handshake** — the bearer token is sent three ways for server
 *    compatibility (priority order matches the contract): `Authorization: Bearer …` header,
 *    `Sec-WebSocket-Protocol: bearer,<token>` subprotocol, and `?token=` query param. An anonymous
 *    handshake is rejected with 401, so we never attempt a connection without a valid session.
 *  - **30s heartbeat** — the OkHttp client also pings at the protocol level, but we additionally send
 *    app-level `{"type":"ping"}` frames and run a watchdog that force-reconnects a socket that has
 *    gone silent (no inbound frame within 2× the interval = dead peer).
 *  - **Exponential backoff** — reconnect delay doubles (2s → 4s → … capped at 60s) and resets on a
 *    clean open.
 *  - **De-duplication** — every inbound event is gated through [EventDeduplicationCache] on its `id`
 *    so an event that also arrives via FCM is not surfaced twice.
 */
@Singleton
class NotificationSocketManager @Inject constructor(
    @ForWebSocket private val okHttpClient: OkHttpClient,
    private val notificationService: NotificationService,
    private val tokenStore: ITokenStore,
    private val dedupeCache: EventDeduplicationCache,
    private val appEventBus: IAppEventBus,
    @ApplicationContext private val context: Context
) {
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var shouldBeConnected = false
    @Volatile private var reconnectAttempts = 0
    @Volatile private var lastInboundAtMs = 0L
    // Distinguishes the first successful connect (cold start already loads data) from every later
    // (re)connect, which must re-sync any state missed while the socket was down. See onOpen().
    @Volatile private var hasConnectedBefore = false

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    // replay=1 hands the latest event to a late subscriber; extraBufferCapacity + DROP_OLDEST keeps a
    // burst of frames from suspending the emitter (or the whole read loop) when a collector is slow —
    // stale realtime events are worth dropping, a wedged socket isn't. Paired with tryEmit below.
    private val _events = MutableSharedFlow<SocketEvent>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow()

    /**
     * Enable the connection and turn on automatic reconnection. Safe to call repeatedly.
     *
     * Always re-arms the attempt (rather than short-circuiting on `shouldBeConnected`): an earlier
     * call may have deferred because no session token existed yet, and the auth flow calls `connect()`
     * again once the JWT is minted — that second call must actually open the socket. `attemptConnection()`
     * is itself a no-op when a socket already exists, so repeat calls can't produce duplicate sockets.
     */
    fun connect() {
        Timber.i("[NotificationSocket] connect() called.")
        shouldBeConnected = true
        attemptConnection()
    }

    /** Manually close and disable automatic reconnection (e.g. on logout). */
    fun disconnect() {
        shouldBeConnected = false
        reconnectAttempts = 0
        reconnectJob?.cancel()
        stopHeartbeat()
        Timber.i("[NotificationSocket] disconnect() called.")
        webSocket?.close(NORMAL_CLOSURE, "Client session ended.")
        webSocket = null
    }

    private fun attemptConnection() {
        if (webSocket != null) {
            Timber.d("[NotificationSocket] Connection already exists or is in progress.")
            return
        }
        if (!shouldBeConnected) return

        val token = tokenStore.getTokenDevice()?.takeIf { it.isNotBlank() }
        if (token == null) {
            // Anonymous handshakes are rejected (401). Wait for a session; connect() is called again
            // after sign-in. We do NOT busy-loop reconnects while unauthenticated.
            Timber.w("[NotificationSocket] No session token; deferring connection until authenticated.")
            return
        }

        Timber.d("[NotificationSocket] Connecting… (attempt #${reconnectAttempts + 1})")
        val encodedToken = URLEncoder.encode(token, Charsets.UTF_8.name())
        // Derive the WS endpoint from the single source of truth (the REST base URL) so the host can
        // never drift between HTTP and WS config. http→ws, https→wss; the relayer's WS path is /ws.
        val wsBase = BuildConfig.RELAYER_BASE_URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/')
        val url = "$wsBase/ws?token=$encodedToken"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Sec-WebSocket-Protocol", "bearer,$token")
            .build()
        webSocket = okHttpClient.newWebSocket(request, SocketListener())
    }

    private fun scheduleReconnect() {
        if (!shouldBeConnected) return
        reconnectAttempts++
        // 2s, 4s, 8s, 16s, … capped at 60s.
        val delayMillis = (RECONNECT_BASE_MS * 2.0.pow(reconnectAttempts - 1)).toLong()
            .coerceAtMost(RECONNECT_MAX_MS)
        Timber.w("[NotificationSocket] Reconnecting in ${delayMillis / 1000}s.")
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMillis)
            attemptConnection()
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        lastInboundAtMs = System.currentTimeMillis()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val socket = webSocket ?: break
                val silentForMs = System.currentTimeMillis() - lastInboundAtMs
                if (silentForMs > HEARTBEAT_INTERVAL_MS * 2) {
                    Timber.w("[NotificationSocket] Heartbeat timeout (${silentForMs}ms silent). Forcing reconnect.")
                    socket.cancel() // hard close → onFailure → scheduleReconnect()
                    break
                }
                // App-level ping; server replies with {"name":"pong"} which refreshes lastInboundAtMs.
                socket.send("{\"type\":\"ping\"}")
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Timber.i("[NotificationSocket] ✅ Connected.")
            reconnectAttempts = 0
            startHeartbeat()
            // Re-sync on every (re)connect except the very first: while the socket was down (dropped,
            // backgrounded, or offline) we may have missed balance/tx/history pushes. The cold-start
            // path already loads on the first connect, so only fan out a refresh on later ones.
            if (hasConnectedBefore) {
                Timber.d("[NotificationSocket] Reconnected → requesting state re-sync.")
                scope.launch { appEventBus.postEvent(AppEvent.WalletNeedsRefresh) }
            }
            hasConnectedBefore = true
        }

        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        override fun onMessage(webSocket: WebSocket, text: String) {
            lastInboundAtMs = System.currentTimeMillis()
            Timber.d("[NotificationSocket] ⬇ $text")

            val parsed = parseEnvelope(text) ?: return
            if (parsed.isHeartbeat) return // pong — liveness only, nothing to surface.

            // Dedup across transports (WS + FCM) on the event id.
            if (!dedupeCache.shouldProcess(parsed.event.id)) {
                Timber.d("[NotificationSocket] Duplicate event '${parsed.event.id}' dropped.")
                return
            }

            if (parsed.event !is SocketEvent.Unknown) {
                // tryEmit (never suspends) + the buffered/DROP_OLDEST flow above: a burst of frames can
                // never block this listener callback or the OkHttp read thread.
                _events.tryEmit(parsed.event)
                dispatchRefreshFor(parsed.event)
            }
            handleNotification(parsed.event)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Timber.w("[NotificationSocket] 🔌 Closing: code=$code reason=$reason")
            stopHeartbeat()
            this@NotificationSocketManager.webSocket = null
            if (shouldBeConnected && code != NORMAL_CLOSURE) scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            this@NotificationSocketManager.webSocket = null
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.e(t, "[NotificationSocket] ❌ Failure (http=${response?.code}).")
            stopHeartbeat()
            this@NotificationSocketManager.webSocket = null
            // A 401 means the token is missing/expired — backoff still applies; once refreshed, the
            // next attempt carries the new bearer.
            if (shouldBeConnected) scheduleReconnect()
        }
    }

    /**
     * Fan a live event out to the app-wide refresh bus so the foreground UI reflects it immediately.
     * Previously live frames refreshed *nothing* (they only produced a system notification); a wallet
     * would only re-read on the next reconnect or a manual pull. Meaningful events now trigger a refresh.
     *
     * NOTE: this is deliberately **coarse** (a full wallet / current-history refresh). Surgically
     * targeting only the affected asset — `AppEvent.WalletAssetNeedsRefresh(networkId, assetId, …)` —
     * needs the socket's identifier formats confirmed first: the server's `chain` is a "relayPrefix"-style
     * key (not the app's `networkId`, so it needs a reverse map) and `token` is ambiguous
     * (symbol vs contract address). That surgical pass is tracked separately; coarse-but-correct here is a
     * strict improvement and can never silently miss an update by guessing the mapping wrong.
     */
    private fun dispatchRefreshFor(event: SocketEvent) {
        val refreshEvents: List<AppEvent> = when (event) {
            is SocketEvent.BalanceUpdated -> listOf(AppEvent.WalletNeedsRefresh)
            is SocketEvent.TxStatusChanged -> listOf(
                AppEvent.WalletNeedsRefresh,             // a status change can move the balance
                AppEvent.TransactionHistoryNeedsRefresh() // …and the history list
            )
            is SocketEvent.GrowthFeeShareAccrued -> listOf(AppEvent.WalletNeedsRefresh)
            is SocketEvent.ConnectionReady, SocketEvent.Unknown -> emptyList()
        }
        if (refreshEvents.isEmpty()) return
        scope.launch { refreshEvents.forEach { appEventBus.postEvent(it) } }
    }

    private data class ParsedFrame(val event: SocketEvent, val isHeartbeat: Boolean)

    /**
     * Parses the contract WS envelope `{ id, name, ts, payload }`. Returns `null` only when the frame
     * is unparseable JSON.
     */
    private fun parseEnvelope(text: String): ParsedFrame? {
        return try {
            val root = JSONObject(text)
            // Heartbeat replies arrive as { "name": "pong" } (no payload).
            val name = root.optString("name").ifBlank { root.optString("type") }
            if (name.equals("pong", ignoreCase = true)) {
                return ParsedFrame(SocketEvent.Unknown, isHeartbeat = true)
            }

            val id = root.optString("id").takeIf { it.isNotBlank() }
            val payload = root.optJSONObject("payload") ?: JSONObject()

            val event = when (name) {
                "connection.ready" -> SocketEvent.ConnectionReady(id)
                "tx.status.changed" -> SocketEvent.TxStatusChanged(
                    id = id,
                    txId = payload.optStringOrNull("id"),
                    txHash = payload.optStringOrNull("txHash"),
                    chain = payload.optStringOrNull("chain"),
                    status = payload.optStringOrNull("status"),
                    token = payload.optStringOrNull("token"),
                    amount = payload.optStringOrNull("amount")
                )
                "balance.updated" -> SocketEvent.BalanceUpdated(
                    id = id,
                    chain = payload.optStringOrNull("chain"),
                    address = payload.optStringOrNull("address"),
                    token = payload.optStringOrNull("token"),
                    deltaRaw = payload.optStringOrNull("deltaRaw")
                )
                "growth.fee_share.accrued" -> SocketEvent.GrowthFeeShareAccrued(
                    id = id,
                    rewardAmountRaw = payload.optStringOrNull("rewardAmountRaw"),
                    token = payload.optStringOrNull("token")
                )
                else -> SocketEvent.Unknown
            }
            ParsedFrame(event, isHeartbeat = false)
        } catch (e: Exception) {
            Timber.e(e, "[NotificationSocket] Failed to parse frame.")
            null
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun handleNotification(event: SocketEvent) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        when (event) {
            is SocketEvent.TxStatusChanged -> {
                val status = event.status?.uppercase()
                when (status) {
                    "SUCCESS" -> notificationService.showTradeNotification(
                        "تراکنش موفق",
                        "تراکنش شما با موفقیت ثبت شد."
                    )
                    "FAILED", "TIMEOUT" -> notificationService.showTradeNotification(
                        "تراکنش ناموفق",
                        "تراکنش شما تکمیل نشد."
                    )
                    else -> { /* QUEUED/PENDING — no user-facing notification */ }
                }
            }
            is SocketEvent.GrowthFeeShareAccrued -> notificationService.showTradeNotification(
                "پاداش جدید",
                "سهم کارمزد دعوت به حساب شما اضافه شد."
            )
            else -> { /* connection.ready / balance.updated / unknown → silent */ }
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val HEARTBEAT_INTERVAL_MS = 30_000L
        const val RECONNECT_BASE_MS = 2_000.0
        const val RECONNECT_MAX_MS = 60_000L
    }
}

/**
 * Realtime frames the client surfaces, aligned to the relayer's targeted WS events. All money fields
 * stay raw `String` (BigInt-as-String invariant) — never parsed to Long/Double here.
 */
sealed interface SocketEvent {
    val id: String?

    data class ConnectionReady(override val id: String?) : SocketEvent

    data class TxStatusChanged(
        override val id: String?,
        val txId: String?,
        val txHash: String?,
        val chain: String?,
        val status: String?,
        val token: String?,
        val amount: String?
    ) : SocketEvent

    data class BalanceUpdated(
        override val id: String?,
        val chain: String?,
        val address: String?,
        val token: String?,
        val deltaRaw: String?
    ) : SocketEvent

    data class GrowthFeeShareAccrued(
        override val id: String?,
        val rewardAmountRaw: String?,
        val token: String?
    ) : SocketEvent

    data object Unknown : SocketEvent {
        override val id: String? get() = null
    }
}
