package com.homeplanner.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.homeplanner.repository.OfflineRepository
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Планировщик пересчета задач при наступлении нового дня.
 * Использует AlarmManager для точного выполнения раз в сутки через 1 минуту после начала дня.
 */
class DayChangeScheduler(private val context: Context) {

    companion object {
        private const val TAG = "DayChangeScheduler"
        private const val ACTION_DAY_CHANGE = "com.homeplanner.DAY_CHANGE"
        private const val DAY_START_HOUR = 4 // TODO: Получать из настроек
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Запускает планировщик для ежедневного пересчета задач.
     */
    fun scheduleDailyRecalculation() {
        val nextTriggerTime = calculateNextTriggerTime()
        val intent = Intent(ACTION_DAY_CHANGE).apply {
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextTriggerTime,
            pendingIntent
        )

        Log.d(TAG, "Scheduled daily recalculation at ${LocalDateTime.ofEpochSecond(nextTriggerTime / 1000, 0, ZoneOffset.UTC)}")
    }

    /**
     * Отменяет планировщик.
     */
    fun cancel() {
        val intent = Intent(ACTION_DAY_CHANGE).apply {
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled daily recalculation")
    }

    /**
     * Вычисляет время следующего срабатывания: следующий день + DAY_START_HOUR + 1 минута.
     */
    private fun calculateNextTriggerTime(): Long {
        val now = System.currentTimeMillis()
        val currentDateTime = LocalDateTime.ofEpochSecond(now / 1000, 0, ZoneOffset.UTC)

        // Начало сегодняшнего дня
        val todayStart = currentDateTime.withHour(DAY_START_HOUR).withMinute(0).withSecond(0).withNano(0)
        val todayTrigger = todayStart.plusMinutes(1)

        val nextTrigger = if (currentDateTime.isBefore(todayTrigger)) {
            todayTrigger
        } else {
            // Следующий день
            todayTrigger.plusDays(1)
        }

        return nextTrigger.toEpochSecond(ZoneOffset.UTC) * 1000
    }


}