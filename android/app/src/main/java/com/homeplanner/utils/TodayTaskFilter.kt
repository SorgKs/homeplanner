package com.homeplanner.utils

import android.util.Log
import com.homeplanner.SelectedUser
import com.homeplanner.model.Task
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Утилита для локальной фильтрации задач для вкладки «Сегодня».
 *
 * Логика соответствует каноническим правилам из OFFLINE_REQUIREMENTS:
 * - one_time:
 *   - видна, если reminder_time сегодня или в прошлом (независимо от completed/active);
 *   - видна, если completed = true, даже если reminder_time в будущем.
 * - recurring / interval:
 *   - видна, если reminder_time сегодня или в прошлом (completed не влияет).
 */
object TodayTaskFilter {

    private const val TAG = "TodayTaskFilter"

    /**
     * Фильтрация задач для вкладки «Сегодня».
     *
     * @param tasks        полный список задач из кэша/сервера
     * @param selectedUser выбранный пользователь (может быть null)
     * @param dayStartHour час начала логического дня (0–23)
     */
    fun filterTodayTasks(
        tasks: List<Task>,
        selectedUser: SelectedUser?,
        dayStartHour: Int
    ): List<Task> {
        if (tasks.isEmpty()) return emptyList()

        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.now(zoneId)
        val todayStart = getDayStart(now, dayStartHour)
        val tomorrowStart = todayStart.plusDays(1)

        return tasks.filter { task ->
            // Фильтрация по пользователю (если задан)
            // Показываем задачи, если:
            // 1. Пользователь не выбран (показываем все)
            // 2. Задача назначена выбранному пользователю
            // 3. Задача не назначена никому (пустой список assignedUserIds)
            if (selectedUser != null) {
                val taskAssignedIds = task.assignedUserIds
                if (taskAssignedIds.isNotEmpty() && !taskAssignedIds.contains(selectedUser.id)) {
                    return@filter false
                }
            }

            val reminderLdt = parseReminderTime(task.reminderTime) ?: return@filter false
            val taskDayStart = getDayStart(reminderLdt, dayStartHour)

            val pos = when {
                taskDayStart.isBefore(todayStart) -> "PAST"
                taskDayStart.isEqual(todayStart) -> "TODAY"
                else -> "FUTURE"
            }

            when (task.taskType) {
                "one_time" -> {
                    // 1) reminder_time в прошлом или сегодня — всегда видна
                    if (pos == "PAST" || pos == "TODAY") {
                        true
                    } else {
                        // 2) reminder_time в будущем — видна только если completed = true
                        task.completed
                    }
                }
                "recurring", "interval" -> {
                    // Видна, если reminder_time сегодня или в прошлом
                    pos == "PAST" || pos == "TODAY"
                }
                else -> {
                    // Неизвестный тип — по умолчанию не показываем, чтобы не ломать UX
                    Log.w(TAG, "Unknown taskType='${task.taskType}' for task id=${task.id}, skipping in Today view")
                    false
                }
            }
        }
    }

    /**
     * Начало логического дня для заданного времени.
     */
    private fun getDayStart(time: LocalDateTime, dayStartHour: Int): LocalDateTime {
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
     * Разбор ISO‑строки reminder_time в LocalDateTime.
     * Поддерживает форматы вида "2024-01-15T10:30:00" и "2024-01-15T10:30:00.123456".
     */
    private fun parseReminderTime(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return try {
            val trimmed = value.substringBefore('.') // отбрасываем микросекунды, если есть
            LocalDateTime.parse(trimmed)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse reminderTime='$value'", e)
            null
        }
    }
}


