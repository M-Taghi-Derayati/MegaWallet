package com.mtd.data.socket

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.mtd.core.notification.EventDeduplicationCache
import com.mtd.core.notification.NotificationService
import com.mtd.domain.interfaceRepository.IAppEventBus
import com.mtd.domain.interfaceRepository.INetworkCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Item 10 (FCM) — the push-message counterpart of [NotificationSocketManager]. FCM delivers the SAME
 * thin monitoring signals as the WebSocket, but as a **data-only** message (a flat `Map<String,String>`)
 * so the app is in charge of both the refresh and the notification (§8.1 of the server contract).
 *
 * Routes each push through the exact same pipeline as the socket so the two transports stay consistent:
 *  1. **Dedup** on `eventId` via the shared [EventDeduplicationCache] — a signal that also arrived over
 *     the WebSocket within the 5s window is processed once, whichever transport won the race.
 *  2. **Refresh-on-signal** via the shared [SocketRefreshMapper] → [IAppEventBus] (never render the
 *     payload; re-read the repository).
 *  3. **Notify** using the server-provided (already-localized) title/body.
 */
@Singleton
class PushMessageHandler @Inject constructor(
    private val dedupeCache: EventDeduplicationCache,
    private val appEventBus: IAppEventBus,
    private val networkCatalog: INetworkCatalog,
    private val notificationService: NotificationService,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun handle(data: Map<String, String>, notificationTitle: String?, notificationBody: String?) {
        Timber.d("[FCM] push received keys=${data.keys} title=$notificationTitle")

        val event = parseFcmData(data)
        val dedupKey = event?.eventId ?: event?.id ?: data["eventId"] ?: data["id"]
        // Cross-transport dedup: the same singleton cache the WebSocket uses (dedup on eventId, 5s).
        if (!dedupeCache.shouldProcess(dedupKey)) {
            Timber.d("[FCM] duplicate push '$dedupKey' dropped.")
            return
        }

        if (event != null && event !is SocketEvent.Unknown) {
            val refreshEvents = SocketRefreshMapper.refreshEventsFor(event) { networkId ->
                networkCatalog.getNetworkInfoById(networkId)?.name?.name
            }
            if (refreshEvents.isNotEmpty()) {
                scope.launch { refreshEvents.forEach { appEventBus.postEvent(it) } }
            }
        }

        // Show whatever the server asked us to show (already Persian-localized). Prefer an explicit
        // notification block, then a data-payload title/body, and finally an event-type fallback so a
        // pure data-only signal (no title/body) still surfaces — matching the WS foreground behavior.
        val fallback = event?.let(::fallbackNotificationFor)
        val title = notificationTitle ?: data["title"] ?: fallback?.first
        val body = notificationBody ?: data["body"] ?: fallback?.second
        if (!title.isNullOrBlank() || !body.isNullOrBlank()) {
            // A new-tx signal routes to the deposit channel (custom sound); everything else uses the
            // default trade channel — mirrors [NotificationSocketManager]'s channel choice.
            showNotification(title.orEmpty(), body.orEmpty(), isDeposit = event is SocketEvent.TxNew)
        }
    }

    /**
     * Generic per-event notification text for data-only pushes the server didn't pre-render. Mirrors
     * [NotificationSocketManager.handleNotification] so background (FCM) and foreground (WS) stay
     * consistent. Returns null for signals that are refresh-only (no user-facing alert).
     */
    private fun fallbackNotificationFor(event: SocketEvent): Pair<String, String>? = when (event) {
        is SocketEvent.TxNew ->
            // Prefer the real amount/direction from the thin-signal hint (FCM is now DATA-ONLY — the server
            // sends no title/body), falling back to a generic alert when the hint is absent.
            TransactionNotificationText.forTx(event.descriptor)
                ?: ("تراکنش جدید" to "یک تراکنش جدید روی آدرس شما ثبت شد.")
        is SocketEvent.TxStatusUpdated -> when (event.status?.uppercase()) {
            "SUCCESS" -> "تراکنش موفق" to "تراکنش شما با موفقیت ثبت شد."
            "FAILED", "TIMEOUT" -> "تراکنش ناموفق" to "تراکنش شما تکمیل نشد."
            else -> null
        }
        is SocketEvent.TxStatusChanged -> when (event.status?.uppercase()) {
            "SUCCESS" -> "تراکنش موفق" to "تراکنش شما با موفقیت ثبت شد."
            "FAILED", "TIMEOUT" -> "تراکنش ناموفق" to "تراکنش شما تکمیل نشد."
            else -> null
        }
        is SocketEvent.GrowthFeeShareAccrued ->
            "پاداش جدید" to "سهم کارمزد دعوت به حساب شما اضافه شد."
        else -> null
    }

    private fun showNotification(title: String, body: String, isDeposit: Boolean) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (isDeposit) {
            // Background/closed: let the deposit channel play the custom sound (silent = false).
            notificationService.showTransactionNotification(title, body, silent = false)
        } else {
            notificationService.showTradeNotification(title, body)
        }
    }

    /** The optional tx.new display hint from the flat FCM map (every value arrives stringified). */
    private fun parseTxDescriptor(data: Map<String, String>): TxDescriptor? {
        val direction = data["direction"]?.takeIf { it.isNotBlank() }
        val amountRaw = data["amountRaw"]?.takeIf { it.isNotBlank() }
        val tokenSymbol = data["tokenSymbol"]?.takeIf { it.isNotBlank() }
        if (direction == null && amountRaw == null && tokenSymbol == null) return null
        return TxDescriptor(
            direction = direction,
            assetKind = data["assetKind"]?.takeIf { it.isNotBlank() },
            asset = data["asset"]?.takeIf { it.isNotBlank() },
            amountRaw = amountRaw,
            tokenSymbol = tokenSymbol,
            tokenDecimal = data["tokenDecimal"]?.toIntOrNull()
        )
    }

    /** Builds a [SocketEvent] from the flat FCM data map (mirrors [NotificationSocketManager]'s parser). */
    private fun parseFcmData(data: Map<String, String>): SocketEvent? {
        val name = data["name"] ?: data["type"] ?: return null
        val id = data["id"]?.takeIf { it.isNotBlank() }
        val eventId = data["eventId"]?.takeIf { it.isNotBlank() }
        return when (name) {
            "tx.new" -> SocketEvent.TxNew(
                id = id,
                eventId = eventId,
                txHash = data["txHash"],
                networkId = data["networkId"],
                addressIdentityId = data["addressIdentityId"],
                cursor = data["cursor"],
                descriptor = parseTxDescriptor(data)
            )
            "balance.invalidated" -> SocketEvent.BalanceInvalidated(
                id = id,
                eventId = eventId,
                walletId = data["walletId"],
                networkId = data["networkId"],
                assetId = data["assetId"],
                cursor = data["cursor"]
            )
            "tx.status.updated" -> SocketEvent.TxStatusUpdated(
                id = id,
                eventId = eventId,
                txHash = data["txHash"],
                networkId = data["networkId"],
                status = data["status"],
                cursor = data["cursor"]
            )
            "tx.status.changed" -> SocketEvent.TxStatusChanged(
                id = id,
                txId = data["id"],
                txHash = data["txHash"],
                chain = data["chain"],
                status = data["status"],
                token = data["token"],
                amount = data["amount"]
            )
            "balance.updated" -> SocketEvent.BalanceUpdated(
                id = id,
                chain = data["chain"],
                address = data["address"],
                token = data["token"],
                deltaRaw = data["deltaRaw"]
            )
            "growth.fee_share.accrued" -> SocketEvent.GrowthFeeShareAccrued(
                id = id,
                rewardAmountRaw = data["rewardAmountRaw"],
                token = data["token"]
            )
            else -> SocketEvent.Unknown
        }
    }
}
