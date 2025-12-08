package com.homeplanner.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeplanner.SelectedUser
import com.homeplanner.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class TodayTaskFilterTest {

    private fun task(
        id: Int,
        type: String,
        reminder: LocalDateTime,
        completed: Boolean = false,
        active: Boolean = true
    ): Task {
        return Task(
            id = id,
            title = "T$id",
            description = null,
            taskType = type,
            recurrenceType = null,
            recurrenceInterval = null,
            intervalDays = null,
            reminderTime = reminder.withSecond(0).withNano(0).toString(),
            groupId = null,
            active = active,
            completed = completed,
            assignedUserIds = listOf(1)
        )
    }

    @Test
    fun one_time_pastAndToday_visibleRegardlessOfCompletedAndActive() {
        val now = LocalDateTime.now()
        val today = now.withHour(10)
        val yesterday = now.minusDays(1).withHour(10)

        val tasks = listOf(
            task(1, "one_time", yesterday, completed = false, active = true),
            task(2, "one_time", yesterday, completed = true, active = false),
            task(3, "one_time", today, completed = false, active = true),
            task(4, "one_time", today, completed = true, active = false),
        )

        val filtered = TodayTaskFilter.filterTodayTasks(
            tasks = tasks,
            selectedUser = SelectedUser(id = 1, name = "User"),
            dayStartHour = 4
        )

        // Все 4 должны быть видимы
        assertEquals(setOf(1, 2, 3, 4), filtered.map { it.id }.toSet())
    }

    @Test
    fun one_time_future_visibleOnlyWhenCompleted() {
        val now = LocalDateTime.now()
        val future = now.plusDays(1).withHour(10)

        val tasks = listOf(
            task(1, "one_time", future, completed = false, active = true),
            task(2, "one_time", future, completed = true, active = false)
        )

        val filtered = TodayTaskFilter.filterTodayTasks(
            tasks = tasks,
            selectedUser = SelectedUser(id = 1, name = "User"),
            dayStartHour = 4
        )

        assertEquals(setOf(2), filtered.map { it.id }.toSet())
    }

    @Test
    fun recurringAndInterval_visibleWhenDueTodayOrPast() {
        val now = LocalDateTime.now()
        val today = now.withHour(10)
        val yesterday = now.minusDays(1).withHour(10)
        val future = now.plusDays(1).withHour(10)

        val tasks = listOf(
            task(1, "recurring", yesterday, completed = false),
            task(2, "recurring", today, completed = true),
            task(3, "recurring", future, completed = false),
            task(4, "interval", yesterday, completed = true),
            task(5, "interval", today, completed = false),
            task(6, "interval", future, completed = true),
        )

        val filtered = TodayTaskFilter.filterTodayTasks(
            tasks = tasks,
            selectedUser = SelectedUser(id = 1, name = "User"),
            dayStartHour = 4
        )

        // Должны попасть только задачи с PAST/TODAY
        assertEquals(setOf(1, 2, 4, 5), filtered.map { it.id }.toSet())
    }
}


