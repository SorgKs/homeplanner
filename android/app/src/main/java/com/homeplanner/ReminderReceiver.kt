package com.homeplanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ReminderReceiver", "onReceive called")
        val title = intent.getStringExtra("title") ?: "Напоминание"
        val message = intent.getStringExtra("message") ?: "Время выполнить задачу"
        val taskId = if (intent.hasExtra("taskId")) intent.getIntExtra("taskId", -1) else null

        Log.d("ReminderReceiver", "Showing reminder: title=$title, message=$message, taskId=$taskId")

        try {
            // Start ReminderActivity directly (will show over lock screen)
            val activityIntent = Intent(context, ReminderActivity::class.java).apply {
                action = "com.homeplanner.REMINDER"
                putExtra("title", title)
                putExtra("message", message)
                if (taskId != null && taskId != -1) putExtra("taskId", taskId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            context.startActivity(activityIntent)
            Log.d("ReminderReceiver", "ReminderActivity started")
            
            // Also show notification (for notification panel)
            val builder = NotificationHelper.buildFullScreenReminder(context, title, message, if (taskId == null || taskId == -1) null else taskId)
            val notification = builder.build()
            with(NotificationManagerCompat.from(context)) {
                val notificationId = System.currentTimeMillis().toInt()
                notify(notificationId, notification)
                Log.d("ReminderReceiver", "Notification sent with id=$notificationId")
            }
        } catch (e: Exception) {
            Log.e("ReminderReceiver", "Failed to show reminder", e)
        }
    }
}


