package com.homeplanner.ui.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.homeplanner.model.Task
import com.homeplanner.model.Group
import com.homeplanner.viewmodel.TaskScreenState

@Composable
fun AllTasksScreen(
    state: TaskScreenState,
    groups: List<Group>,
    onCreateTask: () -> Unit,
    onTaskClick: (Task) -> Unit,
    onTaskComplete: (Int) -> Unit,
    onTaskDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    TaskListContent(
        tasks = state.tasks,
        groups = groups,
        isLoading = state.isLoading,
        error = state.error,
        onCreateTask = onCreateTask,
        onTaskClick = onTaskClick,
        onTaskComplete = onTaskComplete,
        onTaskDelete = onTaskDelete,
        modifier = modifier
    )
}