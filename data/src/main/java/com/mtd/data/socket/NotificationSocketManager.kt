package com.mtd.data.socket

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.mtd.core.notification.EventDeduplicationCache
import com.mtd.core.notification.NotificationService
import com.mtd.core.notification.TransactionSoundPlayer
import com.mtd.data.BuildConfig
import com.mtd.data.di.ForWebSocket
import com.mtd.domain.interfaceRepository.IAppEventBus
import com.mtd.domain.interfaceRepository.INetworkCatalog
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
 *  - **Adaptive heartbeat** — the OkHttp client also pings at the protocol level, but we additionally
 *    send app-level `{"type":"ping"}` frames on the interval the server advertises in
 *    `connection.ready.payload.heartbeatMs` (default 30s until the welcome frame arrives), and run a
 *    watchdog that force-reconnects a socket gone silent (no inbound frame within 2× the interval).
 *  - **Exponential backoff** — reconnect delay doubles (2s → 4s → … capped at 60s) and resets on a
 *    clean open.
 *  - **De-duplication** — every inbound event is gated through [EventDeduplicationCache] on its
 *    `payload.eventId` (thin signals) or envelope `id` (legacy) so an event that also arrives via FCM
 *    is not surfaced twice.
 */
@Singleton
class NotificationSocketManager @Inject constructor(
    @ForWebSocket private val okHttpClient: OkHttpClient,
    private val notificationService: NotificationService,
    private val transactionSoundPlayer: TransactionSoundPlayer,
    private val tokenStore: ITokenStore,
    private val dedupeCache: EventDeduplicationCache,
    private val appEventBus: IAppEventBus,
    private val networkCatalog: INetworkCatalog,
    @ApplicationContext private val context: Context
) {
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var shouldBeConnected = false
    @Volatile private var reconnectAttempts = 0
    @Volatile private var lastInboundAtMs = 0L
    // Heartbeat cadence — starts at the default and is refined by `connection.ready.payload.heartbeatMs`
    // once the welcome frame arrives, so we ping on the server's actual interval instead of a guess.
    @Volatile private var heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS

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
                val interval = heartbeatIntervalMs
                delay(interval)
                val socket = webSocket ?: break
                val silentForMs = System.currentTimeMillis() - lastInboundAtMs
                if (silentForMs > interval * 2) {
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
            // Deliberately NO state re-sync here. A (re)connect — including the foreground reconnect
            // after the screen turns on — must re-establish ONLY the socket; it must not fan out a
            // balance/history refresh. Any state that actually changed while we were away is delivered
            // as a thin signal over this socket (or via FCM) and refreshed on receipt; a manual pull
            // covers anything else. (Reverts the earlier TD-46 reconnect fan-out per product decision.)
        }

        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        override fun onMessage(webSocket: WebSocket, text: String) {
            lastInboundAtMs = System.currentTimeMillis()
            Timber.d("[NotificationSocket] ⬇ $text")

            val parsed = parseEnvelope(text) ?: return
            if (parsed.isHeartbeat) return // pong — liveness only, nothing to surface.

            // The welcome frame tells us the server's real heartbeat interval — adopt it and restart
            // the ping loop so we track the peer's cadence instead of a hardcoded 30s.
            (parsed.event as? SocketEvent.ConnectionReady)?.heartbeatMs
                ?.takeIf { it in MIN_HEARTBEAT_INTERVAL_MS..MAX_HEARTBEAT_INTERVAL_MS }
                ?.let { serverInterval ->
                    if (serverInterval != heartbeatIntervalMs) {
                        heartbeatIntervalMs = serverInterval
                        startHeartbeat()
                    }
                }

            // Dedup across transports (WS + FCM). Thin signals carry a `payload.eventId`; legacy events
            // and the welcome frame fall back to the envelope `id`.
            val dedupKey = parsed.event.eventId ?: parsed.event.id
            if (!dedupeCache.shouldProcess(dedupKey)) {
                Timber.d("[NotificationSocket] Duplicate event '$dedupKey' dropped.")
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
     * Fan a live event out to the app-wide refresh bus so the foreground UI reflects it immediately —
     * the server sends *thin invalidation signals*, never data, so the client re-reads the relevant
     * repository and never renders the payload (§8 of the contract).
     *
     * The **thin signals** carry the app's own `networkId` verbatim (+ `assetId`/`walletId`), so they
     * map to **targeted** refreshes: `balance.invalidated` → refresh just that asset;
     * `tx.new`/`tx.status.updated` → refresh history (network-scoped when the `networkId` reverse-maps
     * to a local `NetworkName`). A missing id degrades to the broader refresh so an update is never
     * silently dropped. The **legacy** events stay coarse (whole-wallet) as before.
     */
    private fun dispatchRefreshFor(event: SocketEvent) {
        val refreshEvents = SocketRefreshMapper.refreshEventsFor(event) { networkId ->
            networkCatalog.getNetworkInfoById(networkId)?.name?.name
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
                "connection.ready" -> SocketEvent.ConnectionReady(
                    id = id,
                    heartbeatMs = payload.optLongOrNull("heartbeatMs")
                )
                // Thin monitoring signals (current server model) — carry `eventId` + the app's `networkId`.
                "tx.new" -> SocketEvent.TxNew(
                    id = id,
                    eventId = payload.optStringOrNull("eventId"),
                    txHash = payload.optStringOrNull("txHash"),
                    networkId = payload.optStringOrNull("networkId"),
                    addressIdentityId = payload.optStringOrNull("addressIdentityId"),
                    cursor = payload.optStringOrNull("cursor")
                )
                "balance.invalidated" -> SocketEvent.BalanceInvalidated(
                    id = id,
                    eventId = payload.optStringOrNull("eventId"),
                    walletId = payload.optStringOrNull("walletId"),
                    networkId = payload.optStringOrNull("networkId"),
                    assetId = payload.optStringOrNull("assetId"),
                    cursor = payload.optStringOrNull("cursor")
                )
                "tx.status.updated" -> SocketEvent.TxStatusUpdated(
                    id = id,
                    eventId = payload.optStringOrNull("eventId"),
                    txHash = payload.optStringOrNull("txHash"),
                    networkId = payload.optStringOrNull("networkId"),
                    status = payload.optStringOrNull("status"),
                    cursor = payload.optStringOrNull("cursor")
                )
                // Legacy per-user events — still accepted, handled coarsely below.
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
        // The in-app sound needs no POST_NOTIFICATIONS. Play it (this path is foreground-only — the
        // socket is disconnected in the background) BEFORE the permission gate, so a deposit is audible
        // in-app even if the user declined notifications; the notification below stays silent. Item 3.
        if (event is SocketEvent.TxNew) transactionSoundPlayer.play()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        when (event) {
            // A monitored address is involved in a new tx (incoming or outgoing — the thin signal
            // doesn't distinguish, and it carries no amount/token). Surface a generic alert in the
            // foreground too so the user gets an immediate ping the moment funds move. The paired
            // dispatchRefreshFor() already refreshes history, so the list updates behind the alert.
            // Foreground (WS): the sound was already played above, so post a SILENT notification to
            // avoid doubling the alert sound. Item 3.
            is SocketEvent.TxNew -> notificationService.showTransactionNotification(
                "تراکنش جدید",
                "یک تراکنش جدید روی آدرس شما ثبت شد.",
                silent = true
            )
            is SocketEvent.TxStatusChanged -> notifyForTxStatus(event.status)
            is SocketEvent.TxStatusUpdated -> notifyForTxStatus(event.status)
            is SocketEvent.GrowthFeeShareAccrued -> notificationService.showTradeNotification(
                "پاداش جدید",
                "سهم کارمزد دعوت به حساب شما اضافه شد."
            )
            // balance.invalidated / balance.updated / connection.ready / unknown → silent
            // (they drive a refresh, not a user-facing alert).
            else -> Unit
        }
    }

    private fun notifyForTxStatus(status: String?) {
        when (status?.uppercase()) {
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

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key).takeIf { it > 0 } else null

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val DEFAULT_HEARTBEAT_INTERVAL_MS = 30_000L
        // Sanity bounds for a server-advertised heartbeat: ignore absurd values that would either
        // hammer the socket or let a dead peer linger.
        const val MIN_HEARTBEAT_INTERVAL_MS = 5_000L
        const val MAX_HEARTBEAT_INTERVAL_MS = 300_000L
        const val RECONNECT_BASE_MS = 2_000.0
        const val RECONNECT_MAX_MS = 60_000L
    }
}

/**
 * Pure mapping from a [SocketEvent] to the app-wide refresh events it should trigger (TASK-22). The
 * server sends *thin invalidation signals*, never data — so each maps to a repository re-read, never a
 * payload render (§8 of `ANDROID_SERVER_INTEGRATION.md`).
 *
 * Kept side-effect-free and Android-free (no `JSONObject`/`Context`) so the targeting rules are unit
 * testable in isolation. [resolveNetworkName] reverse-maps the signal's bundle `networkId` to a local
 * [com.mtd.domain.model.core.NetworkName] name (or null when unknown).
 */
internal object SocketRefreshMapper {

    fun refreshEventsFor(
        event: SocketEvent,
        resolveNetworkName: (networkId: String) -> String?
    ): List<AppEvent> = when (event) {
        // ── Thin monitoring signals (targeted) ──────────────────────────────────────────────────
        // balance.invalidated → refresh only the affected asset when both ids are present; otherwise a
        // whole-wallet refresh (a missing assetId must never silently skip the balance update).
        is SocketEvent.BalanceInvalidated ->
            if (!event.networkId.isNullOrBlank() && !event.assetId.isNullOrBlank()) {
                listOf(AppEvent.WalletAssetNeedsRefresh(assetId = event.assetId, networkId = event.networkId))
            } else {
                listOf(AppEvent.WalletNeedsRefresh)
            }
        // tx.new / tx.status.updated → refresh history, scoped to the network when the networkId
        // reverse-maps to a local NetworkName; a null/unknown network yields an unscoped refresh
        // (consumers treat a blank network as "refresh the current view").
        is SocketEvent.TxNew -> listOf(historyRefresh(event.networkId, resolveNetworkName))
        is SocketEvent.TxStatusUpdated -> listOf(historyRefresh(event.networkId, resolveNetworkName))
        // ── Legacy per-user events (coarse) ─────────────────────────────────────────────────────
        is SocketEvent.BalanceUpdated -> listOf(AppEvent.WalletNeedsRefresh)
        is SocketEvent.TxStatusChanged -> listOf(
            AppEvent.WalletNeedsRefresh,              // a status change can move the balance
            AppEvent.TransactionHistoryNeedsRefresh() // …and the history list
        )
        is SocketEvent.GrowthFeeShareAccrued -> listOf(AppEvent.WalletNeedsRefresh)
        is SocketEvent.ConnectionReady, SocketEvent.Unknown -> emptyList()
    }

    private fun historyRefresh(
        networkId: String?,
        resolveNetworkName: (String) -> String?
    ): AppEvent {
        val networkName = networkId?.takeIf { it.isNotBlank() }?.let(resolveNetworkName)
        return AppEvent.TransactionHistoryNeedsRefresh(networkName = networkName)
    }
}

/**
 * Realtime frames the client surfaces, aligned to the relayer's WS contract (§8 of
 * `ANDROID_SERVER_INTEGRATION.md`). Two generations coexist:
 *
 *  - **Thin monitoring signals** ([TxNew], [BalanceInvalidated], [TxStatusUpdated]) — the current
 *    server model. Payloads carry a dedicated `eventId` (dedup key, 5s window) and the app's own
 *    `networkId` verbatim (e.g. `sepolia`, `base_sepolia`, `shasta_testnet` — the bundle id, NOT a
 *    relayPrefix), so they map cleanly to targeted refreshes. `cursor` is opaque — never parsed.
 *  - **Legacy per-user events** ([TxStatusChanged], [BalanceUpdated], [GrowthFeeShareAccrued]) — may
 *    still arrive; handled coarsely (whole-wallet refresh) as before.
 *
 * All money fields stay raw `String` (BigInt-as-String invariant) — never parsed to Long/Double here.
 */
sealed interface SocketEvent {
    /** Envelope frame id (`{ id, name, ts, payload }`). Welcome frame = `"welcome"`. */
    val id: String?

    /**
     * The monitoring-signal dedup id (`payload.eventId`). Present only on the thin signals; dedup
     * falls back to [id] for everything else. See [EventDeduplicationCache].
     */
    val eventId: String? get() = null

    data class ConnectionReady(
        override val id: String?,
        /** Server heartbeat interval from `payload.heartbeatMs`; drives the app-level ping cadence. */
        val heartbeatMs: Long? = null
    ) : SocketEvent

    /** `tx.new` — a monitored address is involved in a new transaction. → refresh history. */
    data class TxNew(
        override val id: String?,
        override val eventId: String?,
        val txHash: String?,
        val networkId: String?,
        val addressIdentityId: String?,
        val cursor: String?
    ) : SocketEvent

    /** `balance.invalidated` — a specific `(walletId, networkId, assetId)` balance is stale. → refresh that asset. */
    data class BalanceInvalidated(
        override val id: String?,
        override val eventId: String?,
        val walletId: String?,
        val networkId: String?,
        val assetId: String?,
        val cursor: String?
    ) : SocketEvent

    /** `tx.status.updated` — a tracked tx changed status. → refresh history / status. */
    data class TxStatusUpdated(
        override val id: String?,
        override val eventId: String?,
        val txHash: String?,
        val networkId: String?,
        val status: String?,
        val cursor: String?
    ) : SocketEvent

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
