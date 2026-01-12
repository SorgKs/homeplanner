package com.homeplanner.ui.tasks

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.homeplanner.model.Task
import com.homeplanner.model.Group
import com.homeplanner.viewmodel.TaskScreenState
import com.homeplanner.UserSettings
import com.homeplanner.utils.TodayTaskFilter

@Composable
fun TodayScreen(
    state: TaskScreenState,
    groups: List<Group>,
    onCreateTask: () -> Unit,
    onTaskClick: (Task) -> Unit,
    onTaskComplete: (Int, Boolean) -> Unit,
    onTaskDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val selectedUser by userSettings.selectedUserFlow.collectAsState(initial = null)

    val todayTasks = TodayTaskFilter.filterTodayTasks(state.tasks, selectedUser, 4)

    TaskListContent(
        tasks = todayTasks,
        groups = groups,
        isLoading = state.isLoading,
        error = state.error,
        onCreateTask = onCreateTask,
        onTaskClick = onTaskClick,
        onTaskComplete = onTaskComplete,
        onTaskDelete = onTaskDelete,
        isAllTasksView = false,
        modifier = modifier
    )
}