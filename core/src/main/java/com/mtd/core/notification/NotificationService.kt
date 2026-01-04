package com.mtd.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
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
    private val CHANNEL_ID = "trade_notifications"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val name = "اطلاع‌رسانی معاملات"
        val descriptionText = "نمایش وضعیت واریز و برداشت‌ها"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        notificationManager.createNotificationChannel(channel)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showTradeNotification(title: String, message: String) {
        // برای اندروید ۱۳ به بالا، به اجازه نوتیفیکیشن نیاز داریم
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // در یک اپ واقعی، باید کاربر را برای دادن اجازه هدایت کنیم
            return
        }
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)

        // یک ID منحصر به فرد برای هر نوتیفیکیشن
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}