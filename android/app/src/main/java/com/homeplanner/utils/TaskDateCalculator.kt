package com.homeplanner.utils

import android.util.Log
import com.homeplanner.model.Task
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Утилита для работы с логическим днём и пересчётом дат задач.
 *
 * Логика согласована с серверным TaskService:
 * - getDayStart / isNewDay используют day_start_hour;
 * - пересчёт выполняется только раз в новый логический день;
 * - one_time: reminderTime не меняется;
 * - recurring/interval: reminderTime переносится вперёд согласно типу повторения.
 */
object TaskDateCalculator {

    private const val TAG = "TaskDateCalculator"

    /**
     * Начало логического дня для заданного времени.
     */
    fun getDayStart(time: LocalDateTime, dayStartHour: Int): LocalDateTime {
        return if (time.hour >= dayStartHour) {
            time.withHour(dayStartHour).withMinute(0).withSecond(0).withNano(0)
        } else {
            time.minusDays(1)
                .withHour(dayStartHour)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
        }
    }

    /**
     * Проверка наступления нового логического дня.
     *
     * @param lastUpdateMillis время последнего пересчёта или null
     * @param nowMillis        текущее время
     * @param lastDayStartHour час начала дня, использованный при lastUpdate (может быть null)
     * @param currentDayStartHour текущий час начала дня
     */
    fun isNewDay(
        lastUpdateMillis: Long?,
        nowMillis: Long,
        lastDayStartHour: Int?,
        currentDayStartHour: Int
    ): Boolean {
        if (lastUpdateMillis == null) return true

        val zone = ZoneId.systemDefault()
        val lastUpdateTime = LocalDateTime.ofEpochSecond(
            lastUpdateMillis / 1000,
            ((lastUpdateMillis % 1000) * 1_000_000).toInt(),
            zone.rules.getOffset(java.time.Instant.ofEpochMilli(lastUpdateMillis))
        )
        val nowTime = LocalDateTime.ofEpochSecond(
            nowMillis / 1000,
            ((nowMillis % 1000) * 1_000_000).toInt(),
            zone.rules.getOffset(java.time.Instant.ofEpochMilli(nowMillis))
        )

        val effectiveLastHour = lastDayStartHour ?: currentDayStartHour
        val prevStart = getDayStart(lastUpdateTime, effectiveLastHour)
        val currStart = getDayStart(nowTime, currentDayStartHour)

        return currStart.isAfter(prevStart)
    }

    /**
     * Пересчёт reminderTime для recurring/interval задачи.
     *
     * Для one_time задач дата не меняется.
     */
    fun calculateNextReminderTime(
        task: Task,
        nowMillis: Long,
        dayStartHour: Int
    ): String {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofEpochSecond(
            nowMillis / 1000,
            ((nowMillis % 1000) * 1_000_000).toInt(),
            zone.rules.getOffset(java.time.Instant.ofEpochMilli(nowMillis))
        )

        val reminder = parseReminderTime(task.reminderTime)
            ?: return task.reminderTime

        return when (task.taskType) {
            "interval" -> {
                val intervalDays = (task.intervalDays ?: 1).toLong()
                val todayStart = getDayStart(now, dayStartHour)
                val next = todayStart.plusDays(intervalDays)
                    .withHour(reminder.hour)
                    .withMinute(reminder.minute)
                    .withSecond(0)
                    .withNano(0)
                next.toString()
            }
            "recurring" -> {
                // Для простоты поддерживаем базовые типы: DAILY, WEEKLY, MONTHLY, YEARLY.
                // Остальные типы повторения при необходимости можно расширить аналогично серверу.
                val type = task.recurrenceType
                val interval = (task.recurrenceInterval ?: 1).toLong()

                val base = now.truncatedTo(ChronoUnit.MINUTES)
                val next = when (type) {
                    "DAILY" -> {
                        var candidate = base.withHour(reminder.hour).withMinute(reminder.minute)
                        if (!candidate.isAfter(base)) {
                            candidate = candidate.plusDays(interval)
                        }
                        candidate
                    }
                    "WEEKLY" -> {
                        val targetDow = reminder.dayOfWeek
                        var candidate = base.withHour(reminder.hour).withMinute(reminder.minute)
                        var daysToAdd = ((targetDow.value - candidate.dayOfWeek.value) + 7) % 7
                        if (daysToAdd == 0 && !candidate.isAfter(base)) {
                            daysToAdd = (7 * interval).toInt()
                        } else if (interval > 1) {
                            daysToAdd += ((interval - 1) * 7).toInt()
                        }
                        candidate.plusDays(daysToAdd.toLong())
                    }
                    "MONTHLY" -> {
                        var candidate = base.withDayOfMonth(reminder.dayOfMonth)
                            .withHour(reminder.hour)
                            .withMinute(reminder.minute)
                        if (!candidate.isAfter(base)) {
                            candidate = candidate.plusMonths(interval)
                        }
                        candidate
                    }
                    "YEARLY" -> {
                        var candidate = base.withMonth(reminder.month.value)
                            .withDayOfMonth(reminder.dayOfMonth)
                            .withHour(reminder.hour)
                            .withMinute(reminder.minute)
                        if (!candidate.isAfter(base)) {
                            candidate = candidate.plusYears(interval)
                        }
                        candidate
                    }
                    else -> {
                        // Fallback: просто сдвигаем на interval дней
                        base.plusDays(interval)
                    }
                }

                next.withSecond(0).withNano(0).toString()
            }
            else -> {
                // one_time и прочие типы не пересчитываем
                task.reminderTime
            }
        }
    }

    private fun parseReminderTime(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return try {
            val trimmed = value.substringBefore('.')
            LocalDateTime.parse(trimmed)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse reminderTime='$value'", e)
            null
        }
    }
}


