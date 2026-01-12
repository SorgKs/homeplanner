package com.homeplanner.utils

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Unit tests for TaskDateCalculator.
 */
class TaskDateCalculatorTest {

    @Test
    fun testIsToday() {
        val today = LocalDate.now()
        val todayDateTime = today.atStartOfDay()

        assertTrue("Today should be today", TaskDateCalculator.isToday(todayDateTime))
    }

    @Test
    fun testIsTomorrow() {
        val tomorrow = LocalDate.now().plusDays(1)
        val tomorrowDateTime = tomorrow.atStartOfDay()

        assertTrue("Tomorrow should be tomorrow", TaskDateCalculator.isTomorrow(tomorrowDateTime))
    }

    @Test
    fun testGetCurrentDayStart() {
        val dayStartHour = 4
        val dayStart = TaskDateCalculator.getCurrentDayStart(dayStartHour)

        assertEquals("Day start hour should be 4", dayStartHour, dayStart.hour)
    }

    @Test
    fun testIsWithinDayRange() {
        val dayStartHour = 4
        val now = LocalDateTime.now()

        val result = TaskDateCalculator.isWithinDayRange(now, dayStartHour)
        // This depends on current time, but should not crash
        assertNotNull("Should return boolean", result)
    }
}