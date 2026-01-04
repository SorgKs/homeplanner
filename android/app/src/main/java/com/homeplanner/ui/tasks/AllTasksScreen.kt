package com.homeplanner.ui.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.homeplanner.model.Task
import com.homeplanner.viewmodel.TaskScreenState

@Composable
fun AllTasksScreen(
    state: TaskScreenState,
    onCreateTask: () -> Unit,
    onTaskClick: (Task) -> Unit,
    onTaskComplete: (Int) -> Unit,
    onTaskDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    TaskListContent(
        tasks = state.tasks,
        isLoading = state.isLoading,
        error = state.error,
        onCreateTask = onCreateTask,
        onTaskClick = onTaskClick,
        onTaskComplete = onTaskComplete,
        onTaskDelete = onTaskDelete,
        modifier = modifier
    )
}