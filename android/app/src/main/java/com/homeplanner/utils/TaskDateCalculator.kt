package com.homeplanner.utils

import com.homeplanner.model.Task
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
        // Логика расчета следующего времени выполнения задачи на основе recurrenceType и interval
        // Пока возвращаем текущее время, полная реализация зависит от бизнес-логики
        return currentTime
    }

    private fun adjustForDayStart(time: LocalDateTime, dayStartHour: Int): LocalDateTime {
        val dayStart = getDayStart(time, dayStartHour)
        return if (time.isBefore(dayStart)) {
            dayStart
        } else {
            time
        }
    }
}
