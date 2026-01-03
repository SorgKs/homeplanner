package com.homeplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import com.homeplanner.viewmodel.TaskViewModel
import com.homeplanner.viewmodel.TaskScreenState
import com.homeplanner.model.Task
import com.homeplanner.ui.tasks.TaskListScreen
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupComposeContent()
    }

    override fun onResume() {
        super.onResume()
        // Нет вызовов
    }

    override fun onPause() {
        super.onPause()
        // Нет вызовов
    }

    override fun onDestroy() {
        super.onDestroy()
        // Нет вызовов
    }

    private fun setupComposeContent() {
        setContent {
            val viewModel: TaskViewModel = koinViewModel()
            val state by viewModel.state.collectAsState()
            TasksScreen(state, ::handleUiEvents)
        }
    }

    private fun handleUiEvents(event: UiEvent) {
        when (event) {
            is UiEvent.NavigateToTodayTab -> navigateToTodayTab()
            is UiEvent.NavigateToAllTasksTab -> navigateToAllTasksTab()
            is UiEvent.NavigateToSettingsTab -> navigateToSettingsTab()
        }
    }

    private fun navigateToTodayTab() {
        // TODO: Implement navigation to today tab
    }

    private fun navigateToAllTasksTab() {
        // TODO: Implement navigation to all tasks tab
    }

    private fun navigateToSettingsTab() {
        // TODO: Implement navigation to settings tab
    }
}

// UI Events
sealed class UiEvent {
    object NavigateToTodayTab : UiEvent()
    object NavigateToAllTasksTab : UiEvent()
    object NavigateToSettingsTab : UiEvent()
}



@Composable
fun TasksScreen(state: TaskScreenState, onEvent: (UiEvent) -> Unit) {
    val viewModel: TaskViewModel = koinViewModel()
    // Initialize viewModel with network settings if not already done
    // viewModel.initialize(networkConfig, apiBaseUrl, selectedUser) // No longer needed
    val filteredTasks = viewModel.getFilteredTasks(state.tasks, state.currentFilter)

    val filteredState = state.copy(tasks = filteredTasks)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        TaskListScreen(
            state = filteredState,
            onEvent = onEvent,
            onTaskClick = { /* TODO: Navigate to task details */ },
            onTaskComplete = { /* TODO: Handle task completion */ },
            onTaskDelete = { /* TODO: Handle task deletion */ },
            onCreateTask = { /* TODO: Show create dialog */ }
        )
    }
}