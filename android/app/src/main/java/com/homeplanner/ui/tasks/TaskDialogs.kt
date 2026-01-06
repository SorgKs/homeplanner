package com.homeplanner.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homeplanner.model.Task

@Composable
private fun TaskForm(
    currentTitle: String,
    currentDescription: String,
    currentTime: String,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTimeChange: (String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = currentTitle,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = currentDescription,
            onValueChange = onDescriptionChange,
            label = { Text("Description (optional)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = currentTime,
            onValueChange = onTimeChange,
            label = { Text("Reminder time") },
            placeholder = { Text("HH:mm") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CreateTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (Task) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var reminderTime by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Task") },
        text = {
            TaskForm(title, description, reminderTime,
                    { title = it }, { description = it }, { reminderTime = it })
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        // TODO: Create proper Task object with all required fields
                        val task = Task(
                            id = 0, // Will be assigned by server
                            title = title,
                            description = description.takeIf { it.isNotBlank() },
                            taskType = "regular", // Default
                            recurrenceType = null,
                            recurrenceInterval = null,
                            intervalDays = null,
                            reminderTime = reminderTime,
                            groupId = null,
                            enabled = true,
                            completed = false,
                            assignedUserIds = emptyList(),
                            updatedAt = System.currentTimeMillis(),
                            lastAccessed = System.currentTimeMillis(),
                            lastShownAt = null,
                            createdAt = System.currentTimeMillis()
                        )
                        onConfirm(task)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditTaskDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirm: (Task) -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description ?: "") }
    var reminderTime by remember { mutableStateOf(task.reminderTime) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Task") },
        text = {
            TaskForm(title, description, reminderTime,
                    { title = it }, { description = it }, { reminderTime = it })
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        val updatedTask = task.copy(
                            title = title,
                            description = description.takeIf { it.isNotBlank() },
                            reminderTime = reminderTime,
                            updatedAt = System.currentTimeMillis()
                        )
                        onConfirm(updatedTask)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteTaskDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Task") },
        text = { Text("Are you sure you want to delete \"${task.title}\"?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}