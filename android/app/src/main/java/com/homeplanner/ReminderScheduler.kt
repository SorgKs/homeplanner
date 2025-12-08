package com.homeplanner

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.homeplanner.model.Task
import com.homeplanner.utils.TaskDateCalculator
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class ReminderScheduler(private val context: Context) {
    private val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun cancelAll(tasks: List<Task>) {
        tasks.forEach { cancelForTask(it) }
    }

    private fun pendingIntentFor(task: Task): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", task.title)
            val groupName = task.groupId?.toString() ?: ""
            val msg = if (groupName.isNotEmpty()) "Задача: ${task.title} (группа ${groupName})" else "Задача: ${task.title}"
            putExtra("message", msg)
            putExtra("taskId", task.id)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, task.id, intent, flags)
    }

    fun cancelForTask(task: Task) {
        val pi = pendingIntentFor(task)
        alarmManager.cancel(pi)
    }

    fun scheduleForTasks(tasks: List<Task>) {
        Log.d("ReminderScheduler", "Scheduling reminders for ${tasks.size} tasks")
        var scheduledCount = 0
        var skippedCount = 0
        var errorCount = 0
        tasks.forEach { task ->
            val wasScheduled = scheduleForTaskIfUpcoming(task)
            when {
                wasScheduled == true -> scheduledCount++
                wasScheduled == false -> skippedCount++
                else -> errorCount++
            }
        }
        Log.d("ReminderScheduler", "Планирование завершено: запланировано=$scheduledCount, пропущено=$skippedCount, ошибок=$errorCount из ${tasks.size} задач")
    }

    private fun parseDateTime(dt: String?): Long? {
        if (dt == null) {
            Log.w("ReminderScheduler", "parseDateTime: null datetime")
            return null
        }
        return try {
            // Try ISO format first (e.g., "2024-01-15T10:30:00" or "2024-01-15T10:30:00.123456")
            val ldt = if (dt.contains('T')) {
                LocalDateTime.parse(dt.substringBefore('.'))
            } else {
                LocalDateTime.parse(dt)
            }
            val epochMs = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            Log.d("ReminderScheduler", "parseDateTime: $dt -> $epochMs (${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(epochMs))})")
            epochMs
        } catch (e: DateTimeParseException) {
            Log.e("ReminderScheduler", "parseDateTime failed for: $dt", e)
            null
        }
    }

    fun scheduleForTaskIfUpcoming(task: Task): Boolean? {
        val isEligible = task.shouldScheduleReminder()
        Log.d(
            "ReminderScheduler",
            "scheduleForTaskIfUpcoming: task=${task.id} (${task.title}), active=${task.active}, completed=${task.completed}, eligible=$isEligible, reminderTime=${task.reminderTime}"
        )
        
        // For reminders, we want to schedule if:
        // 1. Task is not completed (not active but not completed yet), OR
        // 2. Task is active (not completed)
        // But we'll be more permissive: schedule if reminder time is in future, regardless of completion status
        // (completed tasks shouldn't show reminders, but we'll let the time check handle it)
        
        var effectiveReminderTimeStr = task.reminderTime
        Log.d("ReminderScheduler", "Using reminderTimeStr=$effectiveReminderTimeStr for task ${task.id}")
        
        var whenMs = parseDateTime(effectiveReminderTimeStr)
        
        if (whenMs == null) {
            val msg = "ОПОВЕЩЕНИЕ НЕ ЗАПЛАНИРОВАНО: задача ${task.id} (${task.title}) - не удалось распарсить время напоминания: $effectiveReminderTimeStr"
            Log.w("ReminderScheduler", msg)
            return null // ошибка
        }
        
        val now = System.currentTimeMillis()
        var delayMs = whenMs - now
        var delayMinutes = delayMs / 60000.0
        
        val timeFormatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        val whenFormatted = timeFormatter.format(java.util.Date(whenMs))
        val nowFormatted = timeFormatter.format(java.util.Date(now))
        
        Log.d("ReminderScheduler", "Task ${task.id}: now=$nowFormatted, when=$whenFormatted, delay=${String.format("%.1f", delayMinutes)} минут")
        
        // Если время напоминания в прошлом и задача повторяющаяся/интервальная и ещё не завершена,
        // пересчитываем ближайшее будущее время через TaskDateCalculator, не меняя кэш.
        if (whenMs <= now &&
            (task.taskType == "recurring" || task.taskType == "interval") &&
            !task.completed
        ) {
            Log.d("ReminderScheduler", "Task ${task.id} reminder time in past, recalculating next occurrence")
            val dayStartHour = 4
            val nextReminder = TaskDateCalculator.calculateNextReminderTime(
                task = task,
                nowMillis = now,
                dayStartHour = dayStartHour
            )
            val nextWhenMs = parseDateTime(nextReminder)
            if (nextWhenMs == null || nextWhenMs <= now) {
                val msg = "ОПОВЕЩЕНИЕ НЕ ЗАПЛАНИРОВАНО: задача ${task.id} (${task.title}) - пересчитанное время напоминания невалидно или в прошлом: $nextReminder"
                Log.w("ReminderScheduler", msg)
                return null // ошибка
            }
            effectiveReminderTimeStr = nextReminder
            whenMs = nextWhenMs
            delayMs = whenMs - now
            delayMinutes = delayMs / 60000.0
            val nextWhenFormatted = timeFormatter.format(java.util.Date(whenMs))
            Log.d("ReminderScheduler", "Task ${task.id}: rescheduled when=$nextWhenFormatted, delay=${String.format("%.1f", delayMinutes)} минут")
        }
        
        if (whenMs <= now) {
            val msg = "ОПОВЕЩЕНИЕ НЕ ЗАПЛАНИРОВАНО: задача ${task.id} (${task.title}) - время напоминания в прошлом (${String.format("%.1f", -delayMinutes)} минут назад)"
            Log.d("ReminderScheduler", msg)
            return false // пропущено
        }
        
        // Only schedule if task is not completed (active)
        // Completed tasks shouldn't show reminders
        if (!isEligible) {
            val msg = "ОПОВЕЩЕНИЕ НЕ ЗАПЛАНИРОВАНО: задача ${task.id} (${task.title}) - задача завершена или неактивна (active=${task.active}, completed=${task.completed})"
            Log.d("ReminderScheduler", msg)
            return false // пропущено
        }
        
        val pi = pendingIntentFor(task)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, whenMs, pi)
            }
            val msg = "ОПОВЕЩЕНИЕ ЗАПЛАНИРОВАНО: задача ${task.id} (${task.title}) - сработает через ${String.format("%.1f", delayMinutes)} минут (${whenFormatted})"
            Log.d("ReminderScheduler", msg)
            return true // успешно запланировано
        } catch (e: Exception) {
            val msg = "ОПОВЕЩЕНИЕ НЕ ЗАПЛАНИРОВАНО: задача ${task.id} (${task.title}) - ошибка при установке будильника"
            Log.e("ReminderScheduler", msg, e)
            return null // ошибка
        }
    }
}

private fun Task.shouldScheduleReminder(): Boolean {
    if (!active) {
        return false
    }
    return !completed
}


