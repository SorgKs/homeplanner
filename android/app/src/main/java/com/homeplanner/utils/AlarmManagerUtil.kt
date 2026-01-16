package com.homeplanner.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.homeplanner.services.ReminderReceiver

class AlarmManagerUtil {

    companion object {

        fun scheduleAlarm(context: Context, taskId: Long, alarmTime: Long) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("taskId", taskId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
        }

        fun cancelAlarm(context: Context, taskId: Long) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)

            val intent = Intent(context, ReminderReceiver::class.java)

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
        }
    }
}