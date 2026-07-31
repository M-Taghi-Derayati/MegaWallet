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
 * Item 3 — plays the bundled transaction sound (`res/raw/deposit_alert`) IN-APP.
 *
 * TASK-59a — this is now a **fallback only**, used when the user has denied POST_NOTIFICATIONS (playing
 * a sound needs no permission, so a deposit is still audible). When notifications are allowed, both the
 * foreground (WS) and background (FCM) paths post a non-silent notification and let the deposit channel
 * alert with its custom sound *and vibration*. Previously the foreground path posted `silent = true` and
 * relied on this player alone — which meant no vibration ever, and total silence whenever the MediaPlayer
 * failed. Do not call this alongside a non-silent notification, or the alert doubles.
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
