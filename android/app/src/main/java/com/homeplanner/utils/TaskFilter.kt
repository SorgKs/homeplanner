package com.homeplanner.utils

import com.homeplanner.model.Task
import com.homeplanner.SelectedUser
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.homeplanner.utils.TaskFilterType

object TaskFilter {

    fun filterTasks(
        tasks: List<Task>,
        filter: TaskFilterType,
        selectedUser: SelectedUser?,
        dayStartHour: Int
    ): List<Task> {
        return when (filter) {
            TaskFilterType.TODAY -> filterByTime(filterByUser(tasks, selectedUser), dayStartHour)
            TaskFilterType.ALL -> filterByUser(tasks, selectedUser)
            TaskFilterType.COMPLETED -> filterByCompletion(filterByUser(tasks, selectedUser), true)
            TaskFilterType.PENDING -> filterByCompletion(filterByUser(tasks, selectedUser), false)
        }
    }

    fun isTaskVisibleToday(task: Task, dayStartHour: Int): Boolean {
        return TodayTaskFilter.filterTodayTasks(listOf(task), null, dayStartHour).isNotEmpty()
    }

    fun getDayStartTime(dayStartHour: Int): LocalDateTime {
        val now = LocalDateTime.now()
        return if (now.hour >= dayStartHour) {
            now.withHour(dayStartHour).withMinute(0).withSecond(0).withNano(0)
        } else {
            now.minusDays(1)
                .withHour(dayStartHour)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
        }
    }

    private fun filterByUser(tasks: List<Task>, selectedUser: SelectedUser?): List<Task> {
        if (selectedUser == null) return tasks

        return tasks.filter { task ->
            task.assignedUserIds.isEmpty() || task.assignedUserIds.contains(selectedUser.id)
        }
    }

    private fun filterByTime(tasks: List<Task>, dayStartHour: Int): List<Task> {
        return TodayTaskFilter.filterTodayTasks(tasks, null, dayStartHour)
    }

    private fun filterByCompletion(tasks: List<Task>, completed: Boolean): List<Task> {
        return tasks.filter { it.completed == completed }
    }
}