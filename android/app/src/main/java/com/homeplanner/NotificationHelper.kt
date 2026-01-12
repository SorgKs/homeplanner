package com.homeplanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    const val REMINDER_CHANNEL_ID: String = "reminder_alarm_channel"
    private const val REMINDER_CHANNEL_NAME: String = "Напоминания"
    private const val REMINDER_CHANNEL_DESC: String = "Срочные напоминания, показываемые поверх экрана"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = mgr.getNotificationChannel(REMINDER_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    REMINDER_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = REMINDER_CHANNEL_DESC
                    enableLights(true)
                    lightColor = Color.RED
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    fun buildFullScreenReminder(
        context: Context,
        title: String,
        message: String,
        taskId: Int? = null
    ): NotificationCompat.Builder {
        ensureChannels(context)
        val fullScreenIntent = Intent(context, ReminderActivity::class.java).apply {
            action = "com.homeplanner.REMINDER"
            putExtra("title", title)
            putExtra("message", message)
            if (taskId != null) putExtra("taskId", taskId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 500, 500))
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
    }
}

