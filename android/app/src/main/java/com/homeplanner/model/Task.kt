package com.homeplanner.model

/** Data model for Task items. */
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
    val active: Boolean,
    val completed: Boolean,
    val assignedUserIds: List<Int> = emptyList(),
)


