package com.homeplanner.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homeplanner.model.Task

@Composable
fun TaskItem(
    task: Task,
    onComplete: (Int) -> Unit,
    onClick: (Task) -> Unit,
    onDelete: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = { onClick(task) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = { onComplete(task.id) }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                TaskTitle(task.title)
                task.description?.let { TaskDescription(it) }
                TaskTime(task.reminderTime)
            }

            IconButton(onClick = { onDelete(task.id) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete task"
                )
            }
        }
    }
}

@Composable
private fun TaskTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun TaskDescription(description: String) {
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TaskTime(time: String) {
    Text(
        text = time,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}