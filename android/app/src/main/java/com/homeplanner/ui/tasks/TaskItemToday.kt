package com.homeplanner.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.homeplanner.model.Task
import com.homeplanner.model.Group
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun TaskItemToday(
    task: Task,
    groups: List<Group>,
    onComplete: (Int, Boolean) -> Unit,
    onClick: (Task) -> Unit,
    onDelete: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(24.dp),
        onClick = { onClick(task) },
        shape = RoundedCornerShape(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (task.groupId != null) {
                    val groupName = groups.find { it.id == task.groupId }?.name ?: ""
                    val timeStr = formatTime(task.reminderTime, task.completed)
                    if (groupName.isNotEmpty()) "$timeStr $groupName ${task.title}" else "$timeStr ${task.title}"
                } else "${formatTime(task.reminderTime, task.completed)} ${task.title}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = (-4).dp)
            )

            Checkbox(
                checked = task.completed,
                onCheckedChange = { checked -> onComplete(task.id, checked) },
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = (-4).dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

private fun formatTime(reminderTime: String, completed: Boolean): String {
    // reminderTime - ISO datetime, extract HH:MM
    return reminderTime.substringAfter('T').take(5)
}