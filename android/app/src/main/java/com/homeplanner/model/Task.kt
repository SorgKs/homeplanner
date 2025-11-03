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
    val nextDueDate: String,
    val reminderTime: String?,
    val groupId: Int?,
    val isCompleted: Boolean? = null,
    val lastCompletedAt: String? = null,
)


