package com.homeplanner.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homeplanner.model.Task
import com.homeplanner.model.Group

@Composable
fun TaskListContent(
    tasks: List<Task>,
    groups: List<Group>,
    isLoading: Boolean,
    error: String?,
    onCreateTask: () -> Unit,
    onTaskClick: (Task) -> Unit,
    onTaskComplete: (Int, Boolean) -> Unit,
    onTaskDelete: (Int) -> Unit,
    isAllTasksView: Boolean = false,
    modifier: Modifier = Modifier
) {
    when {
        isLoading -> LoadingIndicator(modifier = modifier)
        error != null -> ErrorMessage(error, modifier = modifier)
        tasks.isEmpty() -> EmptyState(onCreateTask, modifier = modifier)
        else -> AllTasksList(
            tasks = tasks,
            groups = groups,
            onTaskClick = onTaskClick,
            onTaskComplete = onTaskComplete,
            onTaskDelete = onTaskDelete,
            isAllTasksView = isAllTasksView,
            modifier = modifier
        )
    }
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorMessage(error: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Error: ${error ?: "Unknown error"}")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { /* TODO: retry */ }) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun EmptyState(onCreateTask: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "No tasks yet")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCreateTask) {
                Text("Create first task")
            }
        }
    }
}

@Composable
private fun AllTasksList(
    tasks: List<Task>,
    groups: List<Group>,
    onTaskClick: (Task) -> Unit,
    onTaskComplete: (Int, Boolean) -> Unit,
    onTaskDelete: (Int) -> Unit,
    isAllTasksView: Boolean = false,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(tasks) { task ->
            if (isAllTasksView) {
                TaskItemAll(
                    task = task,
                    groups = groups,
                    onComplete = onTaskComplete,
                    onClick = onTaskClick,
                    onDelete = onTaskDelete
                )
            } else {
                TaskItemToday(
                    task = task,
                    groups = groups,
                    onComplete = onTaskComplete,
                    onClick = onTaskClick,
                    onDelete = onTaskDelete
                )
            }
        }
    }
}