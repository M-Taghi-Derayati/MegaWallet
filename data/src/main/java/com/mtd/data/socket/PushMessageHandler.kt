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
        // notification block; fall back to data-payload title/body for pure data-only messages.
        val title = notificationTitle ?: data["title"]
        val body = notificationBody ?: data["body"]
        if (!title.isNullOrBlank() || !body.isNullOrBlank()) {
            showNotification(title.orEmpty(), body.orEmpty())
        }
    }

    private fun showNotification(title: String, body: String) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationService.showTradeNotification(title, body)
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
                cursor = data["cursor"]
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
