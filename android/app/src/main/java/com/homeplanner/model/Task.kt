package com.homeplanner.model

data class Task(
    val id: Int,
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
    val assignedUserIds: List<Int>,
    val alarm: Boolean = false,
    val updatedAt: Long,
    val lastAccessed: Long,
    val lastShownAt: Long?,
    val createdAt: Long
)

