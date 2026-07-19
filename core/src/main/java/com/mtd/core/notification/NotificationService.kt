package com.mtd.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mtd.core.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        // General trade/status channel — system default sound.
        val trade = NotificationChannel(
            CHANNEL_ID_TRADE,
            "اطلاع‌رسانی معاملات",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "نمایش وضعیت واریز و برداشت‌ها" }

        // Dedicated deposit channel — plays the custom bundled sound (res/raw/deposit_alert). On
        // Android 8+ the sound is a property of the CHANNEL and is immutable after creation, so the
        // sound lives here rather than on each notification. If the sound file ever changes, bump
        // CHANNEL_ID_DEPOSIT (e.g. _v2) so the system recreates the channel with the new sound.
        val soundUri = Uri.parse(
            "android.resource://${context.packageName}/${R.raw.deposit_alert}"
        )
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
        val deposit = NotificationChannel(
            CHANNEL_ID_DEPOSIT,
            "واریز و تراکنش جدید",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "اطلاع‌رسانی تراکنش‌های جدید همراه با صدای اختصاصی"
            setSound(soundUri, audioAttributes)
        }

        notificationManager.createNotificationChannel(trade)
        notificationManager.createNotificationChannel(deposit)
    }

    /** Status/reward alerts — default channel sound. */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showTradeNotification(title: String, message: String) =
        show(CHANNEL_ID_TRADE, title, message)

    /**
     * New-transaction/deposit alerts on [CHANNEL_ID_DEPOSIT]. `silent = false` lets the channel play the
     * custom bundled sound (background/closed via FCM). `silent = true` suppresses THIS notification's
     * sound — used on the foreground WS path, where [TransactionSoundPlayer] plays the sound explicitly
     * so it is audible in-app without doubling up. Item 3.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showTransactionNotification(title: String, message: String, silent: Boolean = false) =
        show(CHANNEL_ID_DEPOSIT, title, message, silent)

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun show(channelId: String, title: String, message: String, silent: Boolean = false) {
        // برای اندروید ۱۳ به بالا، به اجازه نوتیفیکیشن نیاز داریم
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // در یک اپ واقعی، باید کاربر را برای دادن اجازه هدایت کنیم
            return
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setSilent(silent)

        // یک ID منحصر به فرد برای هر نوتیفیکیشن
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private companion object {
        const val CHANNEL_ID_TRADE = "trade_notifications"
        const val CHANNEL_ID_DEPOSIT = "deposit_notifications"
    }
}