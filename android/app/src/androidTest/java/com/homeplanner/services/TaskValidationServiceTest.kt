package com.homeplanner.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeplanner.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskValidationServiceTest {

    private val validationService = TaskValidationService()

    private val validTask = Task(
        id = 1,
        title = "Valid Task",
        description = "Valid description",
        taskType = "one_time",
        recurrenceType = null,
        recurrenceInterval = null,
        intervalDays = null,
        reminderTime = "2025-12-31T10:00:00",
        groupId = null,
        active = true,
        completed = false,
        assignedUserIds = emptyList(),
        updatedAt = System.currentTimeMillis(),
        lastAccessed = System.currentTimeMillis(),
        lastShownAt = null,
        createdAt = System.currentTimeMillis()
    )

    @Test
    fun validateTaskBeforeSend_validTask_returnsValidResult() {
        // When
        val result = validationService.validateTaskBeforeSend(validTask)

        // Then
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun validateTaskBeforeSend_emptyTitle_returnsInvalid() {
        // Given
        val task = validTask.copy(title = "")

        // When
        val result = validationService.validateTaskBeforeSend(task)

        // Then
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals("title", result.errors[0].field)
        assertEquals("Title cannot be empty", result.errors[0].message)
    }

    @Test
    fun validateTaskBeforeSend_titleTooLong_returnsInvalid() {
        // Given
        val longTitle = "a".repeat(201)
        val task = validTask.copy(title = longTitle)

        // When
        val result = validationService.validateTaskBeforeSend(task)

        // Then
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals("title", result.errors[0].field)
        assertEquals("Title cannot exceed 200 characters", result.errors[0].message)
    }

    @Test
    fun validateTaskBeforeSend_descriptionTooLong_returnsInvalid() {
        // Given
        val longDescription = "a".repeat(1001)
        val task = validTask.copy(description = longDescription)

        // When
        val result = validationService.validateTaskBeforeSend(task)

        // Then
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals("description", result.errors[0].field)
        assertEquals("Description cannot exceed 1000 characters", result.errors[0].message)
    }

    @Test
    fun validateTaskBeforeSend_emptyReminderTime_returnsInvalid() {
        // Given
        val task = validTask.copy(reminderTime = "")

        // When
        val result = validationService.validateTaskBeforeSend(task)

        // Then
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals("reminderTime", result.errors[0].field)
        assertEquals("Reminder time cannot be empty", result.errors[0].message)
    }

    @Test
    fun validateTaskBeforeSend_recurringTaskWithoutRecurrenceType_returnsInvalid() {
        // Given
        val task = validTask.copy(
            taskType = "recurring",
            recurrenceType = null,
            recurrenceInterval = 1
        )

        // When
        val result = validationService.validateTaskBeforeSend(task)

        // Then
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals("recurrenceType", result.errors[0].field)
        assertEquals("Recurring tasks must have recurrence type", result.errors[0].message)
    }

    @Test
    fun validateTaskBeforeSend_recurringTaskWithoutRecurrenceInterval_returnsInvalid() {
        // Given
        val task = validTask.copy(
            taskType = "recurring",
            recurrenceType = "DAILY",
            recurrenceInterval = null
        )

        // When
        val result = validationService.validateTaskBeforeSend(task)

        // Then
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals("recurrenceInterval", result.errors[0].field)
        assertEquals("Recurring tasks must have positive recurrence interval", result.errors[0].message)
    }

    @Test
    fun validateTaskBeforeSend_recurringTaskWithZeroRecurrenceInterval_returnsInvalid() {
        // Given
        val task = validTask.copy(
            taskType = "recurring",
            recurrenceType = "DAILY",
            recurrenceInterval = 0
        )

        // When
        val result = validationService.validateTaskBeforeSend(task)

        // Then
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals("recurrenceInterval", result.errors[0].field)
        assertEquals("Recurring tasks must have positive recurrence interval", result.errors[0].message)
    }

    @Test
    fun validateTaskBeforeSend_intervalTaskWithoutIntervalDays_returnsInvalid() {
        // Given
        val task = validTask.copy(
            taskType = "interval",
            recurrenceType = "DAILY",
            recurrenceInterval = 1,
            intervalDays = null
        )

        // When
        val result = validationService.validateTaskBeforeSend(task)

        // Then
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals("recurrenceType", result.errors[0].field)
        assertEquals("Recurring tasks must have recurrence type", result.errors[0].message)
    }

    @Test
    fun validateTaskFields_validTask_returnsNoErrors() {
        // When
        val errors = validationService.validateTaskFields(validTask)

        // Then
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateBusinessRules_validTask_returnsNoErrors() {
        // When
        val errors = validationService.validateBusinessRules(validTask)

        // Then
        assertTrue(errors.isEmpty())
    }
}