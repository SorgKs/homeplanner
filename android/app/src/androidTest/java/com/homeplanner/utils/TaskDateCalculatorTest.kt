package com.homeplanner.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeplanner.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TaskDateCalculatorTest {

    @Test
    fun getDayStart_respectsDayStartHour_beforeAndAfterBorder() {
        val base = LocalDateTime.of(2025, 1, 10, 3, 30)
        val dayStartHour = 4

        val before = base
        val after = base.plusHours(2)

        val beforeStart = TaskDateCalculator.getDayStart(before, dayStartHour)
        val afterStart = TaskDateCalculator.getDayStart(after, dayStartHour)

        // До 4 утра — начало дня вчера
        assertEquals(9, beforeStart.dayOfMonth)
        assertEquals(4, beforeStart.hour)

        // После 4 утра — начало дня сегодня
        assertEquals(10, afterStart.dayOfMonth)
        assertEquals(4, afterStart.hour)
    }

    @Test
    fun isNewDay_returnsTrue_whenLogicalDayHasChanged() {
        val zone = ZoneId.systemDefault()
        val dayStartHour = 4

        val yesterdayMorning = LocalDateTime.of(2025, 1, 9, 10, 0)
        val todayMorning = yesterdayMorning.plusDays(1)

        val lastUpdateMillis = yesterdayMorning
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val nowMillis = todayMorning
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val isNew = TaskDateCalculator.isNewDay(
            lastUpdateMillis = lastUpdateMillis,
            nowMillis = nowMillis,
            lastDayStartHour = dayStartHour,
            currentDayStartHour = dayStartHour
        )

        assertEquals(true, isNew)
    }

    @Test
    fun calculateNextReminderTime_daily_movesToNextDay_whenTimePassed() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.of(2025, 1, 10, 12, 0)
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()

        val reminder = now.minusHours(2) // уже прошло

        val task = Task(
            id = 1,
            revision = 0,
            title = "Daily",
            description = null,
            taskType = "recurring",
            recurrenceType = "DAILY",
            recurrenceInterval = 1,
            intervalDays = null,
            reminderTime = reminder.toString(),
            groupId = null,
            active = true,
            completed = true,
            assignedUserIds = emptyList()
        )

        val nextStr = TaskDateCalculator.calculateNextReminderTime(
            task = task,
            nowMillis = nowMillis,
            dayStartHour = 4
        )
        val next = LocalDateTime.parse(nextStr.substringBefore('.'))

        // Должно быть завтра в то же время, что и reminder (10:00)
        assertEquals(11, next.dayOfMonth)
        assertEquals(reminder.hour, next.hour)
        assertEquals(reminder.minute, next.minute)
    }
}


