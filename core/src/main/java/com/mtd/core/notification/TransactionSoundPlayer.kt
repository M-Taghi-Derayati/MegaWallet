package com.mtd.core.notification

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.mtd.core.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Item 3 — plays the bundled transaction sound (`res/raw/deposit_alert`) IN-APP. Used on the foreground
 * realtime (WS) path so the alert is audible even on OEMs that suppress a notification channel's sound
 * while the app is in the foreground. The background/closed case stays on the notification channel's own
 * sound (see [NotificationService.showTransactionNotification] with `silent = false`), so the two never
 * double up.
 *
 * Each play spins up a short-lived [MediaPlayer] that releases itself on completion/error — no shared
 * state, safe to call from any thread. Uses the NOTIFICATION stream so it honors the user's ringer.
 */
@Singleton
class TransactionSoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun play() {
        try {
            val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.deposit_alert}")
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { it.release() }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                true
            }
            player.setDataSource(context, uri)
            player.prepareAsync()
        } catch (e: Exception) {
            Timber.e(e, "[TxSound] failed to play transaction sound")
        }
    }
}
