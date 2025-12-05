package com.homeplanner.model

/** Data model for Task items used across network, cache, and UI layers. */
data class Task(
    val id: Int,
    val revision: Int = 0,
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


