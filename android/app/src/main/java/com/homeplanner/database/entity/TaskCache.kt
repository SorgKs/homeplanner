package com.homeplanner.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.homeplanner.model.Task

/**
 * Entity для кэширования задач в Room Database.
 * Хранит полные данные задачи.
 * 
 * Примечание: Поля hasConflict, conflictReason, conflictTimestamp больше не используются,
 * так как конфликты обрабатываются только на сервере. Сервер - источник истины.
 */
    @Entity(
    tableName = "tasks_cache",
    indices = [
        Index(value = ["reminderTime"]),
        Index(value = ["updatedAt"]),
        Index(value = ["taskType"])
    ]
)
data class TaskCache(
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
    val assignedUserIds: String, // JSON array как строка
    
    // Метаданные конфликтов больше не используются (конфликты обрабатываются только на сервере).
    // Поля сохранены только для совместимости со старой схемой БД и могут быть удалены в будущих миграциях.
    val hasConflict: Boolean = false,
    val conflictReason: String? = null,
    val conflictTimestamp: Long? = null,
    val localVersion: String? = null,
    val serverVersion: String? = null,

    // Метаданные для LRU очистки
    val updatedAt: Long = System.currentTimeMillis(),
    val lastAccessed: Long = System.currentTimeMillis(),
    val lastShownAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toTask(): Task {
        val assignedIds = if (assignedUserIds.isNotEmpty()) {
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
            updatedAt = updatedAt,
            lastAccessed = lastAccessed,
            lastShownAt = lastShownAt,
            createdAt = createdAt
        )
    }
    
    companion object {
        fun fromTask(task: Task, hasConflict: Boolean = false): TaskCache {
            val assignedIdsJson = task.assignedUserIds.joinToString(",", "[", "]")

            return TaskCache(
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
                hasConflict = hasConflict,
                updatedAt = task.updatedAt,
                lastAccessed = task.lastAccessed,
                lastShownAt = task.lastShownAt,
                createdAt = task.createdAt
            )
        }
    }
}
