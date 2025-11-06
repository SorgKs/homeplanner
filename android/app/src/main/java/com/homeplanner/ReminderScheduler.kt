package com.homeplanner

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.homeplanner.model.Task
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
        tasks.forEach { scheduleForTaskIfUpcoming(it) }
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

    fun scheduleForTaskIfUpcoming(task: Task) {
        val isActive = task.isActive()
        Log.d("ReminderScheduler", "scheduleForTaskIfUpcoming: task=${task.id} (${task.title}), isActive=$isActive, reminderTime=${task.reminderTime}, nextDueDate=${task.nextDueDate}, lastCompletedAt=${task.lastCompletedAt}")
        
        // For reminders, we want to schedule if:
        // 1. Task is not completed (not active but not completed yet), OR
        // 2. Task is active (not completed)
        // But we'll be more permissive: schedule if reminder time is in future, regardless of completion status
        // (completed tasks shouldn't show reminders, but we'll let the time check handle it)
        
        val reminderTimeStr = task.reminderTime ?: task.nextDueDate
        Log.d("ReminderScheduler", "Using reminderTimeStr=$reminderTimeStr for task ${task.id}")
        
        val whenMs = parseDateTime(reminderTimeStr)
        
        if (whenMs == null) {
            Log.w("ReminderScheduler", "Failed to parse reminder time for task ${task.id}, reminderTimeStr=$reminderTimeStr")
            return
        }
        
        val now = System.currentTimeMillis()
        val delayMs = whenMs - now
        val delayMinutes = delayMs / 60000.0
        
        Log.d("ReminderScheduler", "Task ${task.id}: now=$now, when=$whenMs, delay=${delayMinutes} minutes")
        
        if (whenMs <= now) {
            Log.d("ReminderScheduler", "Task ${task.id} reminder time is in the past (${-delayMinutes} minutes ago), skipping")
            return
        }
        
        // Only schedule if task is not completed (active)
        // Completed tasks shouldn't show reminders
        if (!isActive) {
            Log.d("ReminderScheduler", "Task ${task.id} is completed, skipping reminder registration")
            return
        }
        
        val pi = pendingIntentFor(task)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, whenMs, pi)
            }
            Log.d("ReminderScheduler", "Scheduled reminder for task ${task.id} in ${delayMinutes} minutes")
        } catch (e: Exception) {
            Log.e("ReminderScheduler", "Failed to schedule reminder for task ${task.id}", e)
        }
    }
}

private fun Task.isActive(): Boolean {
    // For reminders, consider task active if:
    // 1. Not completed (lastCompletedAt == null), OR
    // 2. One-time task (can show even if completed)
    // For recurring/interval tasks, if completed today, we still want to show reminder
    // But we can't check "today" here easily, so we'll be more permissive:
    // Schedule reminders for all active tasks (not completed) or one-time tasks
    val isNotCompleted = this.lastCompletedAt == null
    val isOneTime = this.taskType == "one_time"
    
    // For scheduling reminders, we want to schedule if task is not completed
    // or if it's a one-time task (which might still show in "today" view)
    // Actually, for reminders, we should schedule if the reminder time hasn't passed yet
    // and the task is not completed. But we can't check time here, so we'll schedule
    // for all non-completed tasks and one-time tasks.
    val result = isNotCompleted || isOneTime
    android.util.Log.d("ReminderScheduler", "isActive for task ${this.id}: lastCompletedAt=${this.lastCompletedAt}, taskType=${this.taskType}, result=$result")
    return result
}


