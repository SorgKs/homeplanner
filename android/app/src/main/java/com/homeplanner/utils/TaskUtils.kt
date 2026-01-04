package com.homeplanner.utils

import com.homeplanner.model.Task
import org.json.JSONObject

/**
 * Утилитарные функции для работы с задачами.
 */
object TaskUtils {

    /**
     * Преобразует задачу в JSON строку для синхронизации.
     */
    fun taskToJson(task: Task): String {
        return JSONObject().apply {
            // Не включаем id в payload для обновления через очередь синхронизации
            // id передается отдельно как task_id
            put("title", task.title)
            put("description", task.description)
            put("task_type", task.taskType)
            put("recurrence_type", task.recurrenceType)
            put("recurrence_interval", task.recurrenceInterval)
            put("interval_days", task.intervalDays)
            put("reminder_time", task.reminderTime)
            put("group_id", task.groupId)
            put("active", task.active)
            put("completed", task.completed)
            put("assigned_user_ids", org.json.JSONArray(task.assignedUserIds))
        }.toString()
    }
}