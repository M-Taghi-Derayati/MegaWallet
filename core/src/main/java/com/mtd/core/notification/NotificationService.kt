package com.mtd.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
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

    private val channelIdTrade = context.getString(R.string.notification_channel_id_trade)
    private val channelIdDeposit = context.getString(R.string.notification_channel_id_deposit)

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        // General trade/status channel — system default sound.
        val trade = NotificationChannel(
            channelIdTrade,
            "اطلاع‌رسانی معاملات",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "نمایش وضعیت واریز و برداشت‌ها" }

        // TASK-59 — retire superseded deposit channels so the user isn't left with a dead, silent
        // entry in the system notification settings alongside the live one.
        context.resources.getStringArray(R.array.legacy_deposit_channel_ids)
            .forEach { notificationManager.deleteNotificationChannel(it) }

        // Dedicated deposit channel — plays the custom bundled sound (res/raw/deposit_alert). On
        // Android 8+ the sound and importance are properties of the CHANNEL and are IMMUTABLE after
        // creation: calling createNotificationChannel() on an id the system already knows only
        // refreshes the name/description and silently drops the sound/importance below. Channel state
        // also survives uninstall/reinstall (the system backs it up), so a channel that ended up
        // silent cannot be repaired in place or by reinstalling — the id itself must change. That is
        // why the id is versioned in `notification_channels.xml`; see that file before bumping it.
        val soundUri = Uri.parse(
            "android.resource://${context.packageName}/${R.raw.deposit_alert}"
        )
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
        val deposit = NotificationChannel(
            channelIdDeposit,
            "واریز و تراکنش جدید",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "اطلاع‌رسانی تراکنش‌های جدید همراه با صدای اختصاصی"
            setSound(soundUri, audioAttributes)
            enableVibration(true)
        }

        notificationManager.createNotificationChannel(trade)
        notificationManager.createNotificationChannel(deposit)
    }

    /**
     * TASK-59 — true when the deposit channel exists but can no longer alert the user: either it was
     * blocked (`IMPORTANCE_NONE`) or its sound was cleared in the system settings.
     *
     * An app **cannot** re-enable or re-sound an existing channel programmatically, so there is no
     * in-app fix — the only remedy is to send the user to the system channel screen via
     * [depositChannelSettingsIntent]. Settings (TASK S) should surface a warning row driven by this.
     */
    fun isDepositChannelSilenced(): Boolean {
        val channel = notificationManager.getNotificationChannel(channelIdDeposit) ?: return false
        return channel.importance == NotificationManager.IMPORTANCE_NONE || channel.sound == null
    }

    /** System settings screen for the deposit channel — the only way to restore a silenced channel. */
    fun depositChannelSettingsIntent(): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelIdDeposit)
        }

    /** Status/reward alerts — default channel sound. */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showTradeNotification(title: String, message: String) =
        show(channelIdTrade, title, message)

    /**
     * New-transaction/deposit alerts on the deposit channel. `silent = false` lets the channel play the
     * custom bundled sound (background/closed via FCM). `silent = true` suppresses THIS notification's
     * sound — used on the foreground WS path, where [TransactionSoundPlayer] plays the sound explicitly
     * so it is audible in-app without doubling up. Item 3.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showTransactionNotification(title: String, message: String, silent: Boolean = false) =
        show(channelIdDeposit, title, message, silent)

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
}