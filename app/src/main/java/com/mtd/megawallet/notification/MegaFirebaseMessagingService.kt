package com.mtd.megawallet.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mtd.data.socket.PushMessageHandler
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Item 10 (FCM) — receives Firebase Cloud Messages and hands them to the shared realtime pipeline:
 *  - [onNewToken] → [FcmTokenRegistrar] registers/refreshes the device token with the relayer.
 *  - [onMessageReceived] → [PushMessageHandler] dedups (cross-transport with the WebSocket), refreshes
 *    the relevant repository, and shows the server-provided notification.
 *
 * Declared in the manifest with the `com.google.firebase.MESSAGING_EVENT` intent filter.
 */
@AndroidEntryPoint
class MegaFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushMessageHandler: PushMessageHandler
    @Inject lateinit var fcmTokenRegistrar: FcmTokenRegistrar

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("[FCM] onNewToken")
        fcmTokenRegistrar.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        pushMessageHandler.handle(
            data = message.data,
            notificationTitle = message.notification?.title,
            notificationBody = message.notification?.body
        )
    }
}
