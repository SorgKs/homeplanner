package com.homeplanner.services

import com.homeplanner.model.Task

class TaskValidationService {

    fun validateTaskBeforeSend(task: Task): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        errors.addAll(validateTaskFields(task))
        errors.addAll(validateBusinessRules(task))

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    fun validateTaskFields(task: Task): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (task.title.isBlank()) {
            errors.add(ValidationError("title", "Title cannot be empty"))
        }

        if (task.title.length > 200) {
            errors.add(ValidationError("title", "Title cannot exceed 200 characters"))
        }

        errors.addAll(validateDescription(task.description))

        errors.addAll(validateReminderTime(task.reminderTime))

        return errors
    }

    fun validateBusinessRules(task: Task): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Business rule: recurring tasks must have recurrence type and interval
        if (task.taskType in listOf("recurring", "interval")) {
            if (task.recurrenceType.isNullOrBlank()) {
                errors.add(ValidationError("recurrenceType", "Recurring tasks must have recurrence type"))
            }
            if (task.recurrenceInterval == null || task.recurrenceInterval <= 0) {
                errors.add(ValidationError("recurrenceInterval", "Recurring tasks must have positive recurrence interval"))
            }
        }

        // Business rule: reminder time should be in future for new tasks
        // TODO: Add validation for reminder time being in future

        return errors
    }

    private fun validateDescription(description: String?): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        description?.let {
            if (it.length > 1000) {
                errors.add(ValidationError("description", "Description cannot exceed 1000 characters"))
            }
        }

        return errors
    }

    private fun validateReminderTime(reminderTime: String): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (reminderTime.isBlank()) {
            errors.add(ValidationError("reminderTime", "Reminder time cannot be empty"))
            return errors
        }

        try {
            // TODO: Parse and validate reminder time format
            // Expected format: ISO local date time
        } catch (e: Exception) {
            errors.add(ValidationError("reminderTime", "Invalid reminder time format"))
        }

        return errors
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<ValidationError>
    )

    data class ValidationError(
        val field: String,
        val message: String
    )
}