package com.homeplanner.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.homeplanner.model.Task

/**
 * Entity для хранения задач в Room Database.
 * Хранит полные данные задачи с оптимизациями индексов.
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["reminderTime"]),
        Index(value = ["updatedAt"]),
        Index(value = ["taskType"]),
        Index(value = ["groupId"]),
        Index(value = ["hash"])
    ]
)
data class TaskEntity(
    @PrimaryKey
    val id: Int,

    // Поля из Task
    val title: String,
    val description: String?,
    val taskType: String,
    val recurrenceType: String?,
    val recurrenceInterval: Int?,
    val intervalDays: Int?,
    val reminderTime: String,
    val groupId: Int?,
    val enabled: Boolean,
    val completed: Boolean,
    val assignedUserIds: String?, // JSON array как строка
    val alarm: Boolean = false,

    // Индивидуальный SHA-256 хеш для синхронизации
    val hash: String,

    // Метаданные для LRU очистки
    val updatedAt: Long = System.currentTimeMillis(),
    val lastAccessed: Long = System.currentTimeMillis(),
    val lastShownAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toTask(): Task {
        val assignedIds = if (!assignedUserIds.isNullOrEmpty()) {
            assignedUserIds.trim('[', ']').split(",")
                .mapNotNull { it.trim().toIntOrNull() }
        } else {
            emptyList()
        }

        return Task(
            id = id,
            title = title,
            description = description,
            taskType = taskType,
            recurrenceType = recurrenceType,
            recurrenceInterval = recurrenceInterval,
            intervalDays = intervalDays,
            reminderTime = reminderTime,
            groupId = groupId,
            enabled = enabled,
            completed = completed,
            assignedUserIds = assignedIds,
            alarm = alarm,
            updatedAt = updatedAt,
            lastAccessed = lastAccessed,
            lastShownAt = lastShownAt,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromTask(task: Task, hash: String): TaskEntity {
            val assignedIdsJson = if (task.assignedUserIds.isNotEmpty()) {
                task.assignedUserIds.joinToString(",", "[", "]")
            } else {
                ""
            }

            return TaskEntity(
                id = task.id,
                title = task.title,
                description = task.description,
                taskType = task.taskType,
                recurrenceType = task.recurrenceType,
                recurrenceInterval = task.recurrenceInterval,
                intervalDays = task.intervalDays,
                reminderTime = task.reminderTime,
                groupId = task.groupId,
                enabled = task.enabled,
                completed = task.completed,
                assignedUserIds = assignedIdsJson,
                alarm = task.alarm,
                hash = hash,
                updatedAt = task.updatedAt,
                lastAccessed = task.lastAccessed,
                lastShownAt = task.lastShownAt,
                createdAt = task.createdAt
            )
        }
    }
}
