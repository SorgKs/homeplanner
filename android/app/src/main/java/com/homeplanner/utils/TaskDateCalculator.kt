package com.homeplanner.utils

import com.homeplanner.model.Task
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.LocalDate

object TaskDateCalculator {

    fun getDayStart(time: LocalDateTime, dayStartHour: Int): LocalDateTime {
        return time.withHour(dayStartHour).withMinute(0).withSecond(0).withNano(0)
    }

    fun isNewDay(lastUpdateMillis: Long?, nowMillis: Long, lastDayStartHour: Int?, currentDayStartHour: Int): Boolean {
        if (lastUpdateMillis == null || lastDayStartHour == null) return false

        val lastUpdate = LocalDateTime.ofEpochSecond(lastUpdateMillis / 1000, 0, java.time.ZoneOffset.UTC)
        val now = LocalDateTime.ofEpochSecond(nowMillis / 1000, 0, java.time.ZoneOffset.UTC)

        val lastDayStart = getDayStart(lastUpdate, lastDayStartHour)
        val currentDayStart = getDayStart(now, currentDayStartHour)

        return currentDayStart.isAfter(lastDayStart)
    }

    fun calculateNextReminderTime(task: Task, nowMillis: Long, dayStartHour: Int): String {
        val currentTime = LocalDateTime.ofEpochSecond(nowMillis / 1000, 0, java.time.ZoneOffset.UTC)
        val nextTime = getNextOccurrence(task, currentTime)
        val adjustedTime = adjustForDayStart(nextTime, dayStartHour)

        return adjustedTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    fun shouldRecalculateTask(task: Task, dayStartHour: Int): Boolean {
        // Проверяем, нужно ли пересчитать время выполнения задачи
        // Для повторяющихся задач или задач с reminderTime
        return task.recurrenceType != null || task.reminderTime.isNotBlank()
    }

    private fun getNextOccurrence(task: Task, currentTime: LocalDateTime): LocalDateTime {
        return when (task.recurrenceType?.uppercase()) {
            "DAILY" -> {
                val interval = task.recurrenceInterval ?: 1
                currentTime.plusDays(interval.toLong())
            }
            "WEEKLY" -> {
                val interval = task.recurrenceInterval ?: 1
                currentTime.plusWeeks(interval.toLong())
            }
            "MONTHLY" -> {
                val interval = task.recurrenceInterval ?: 1
                currentTime.plusMonths(interval.toLong())
            }
            "YEARLY" -> {
                val interval = task.recurrenceInterval ?: 1
                currentTime.plusYears(interval.toLong())
            }
            null -> {
                // Для interval задач используем intervalDays
                if (task.taskType == "interval") {
                    val interval = task.intervalDays ?: 1
                    currentTime.plusDays(interval.toLong())
                } else {
                    currentTime
                }
            }
            else -> currentTime // Неизвестный тип повторения
        }
    }

    private fun adjustForDayStart(time: LocalDateTime, dayStartHour: Int): LocalDateTime {
        val dayStart = getDayStart(time, dayStartHour)
        return if (time.isBefore(dayStart)) {
            dayStart
        } else {
            time
        }
    }

    fun isToday(dateTime: LocalDateTime): Boolean {
        val today = LocalDate.now()
        return dateTime.toLocalDate() == today
    }

    fun isTomorrow(dateTime: LocalDateTime): Boolean {
        val tomorrow = LocalDate.now().plusDays(1)
        return dateTime.toLocalDate() == tomorrow
    }

    fun getCurrentDayStart(dayStartHour: Int): LocalDateTime {
        val now = LocalDateTime.now()
        return getDayStart(now, dayStartHour)
    }

    fun isWithinDayRange(dateTime: LocalDateTime, dayStartHour: Int): Boolean {
        val dayStart = getDayStart(dateTime, dayStartHour)
        val dayEnd = dayStart.plusDays(1)
        return dateTime.isAfter(dayStart) && dateTime.isBefore(dayEnd)
    }
}
