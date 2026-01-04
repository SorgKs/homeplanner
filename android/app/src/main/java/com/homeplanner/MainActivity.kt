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
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.rememberNavController
import com.homeplanner.viewmodel.TaskViewModel
import com.homeplanner.viewmodel.TaskScreenState
import com.homeplanner.model.Task
import com.homeplanner.ui.tasks.MainScreen
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
            val navController = rememberNavController()
            TasksScreen(state, navController)
        }
    }
}





@Composable
fun TasksScreen(state: TaskScreenState, navController: androidx.navigation.NavHostController) {
    val viewModel: TaskViewModel = koinViewModel()
    // Initialize viewModel with network settings if not already done
    // viewModel.initialize(networkConfig, apiBaseUrl, selectedUser) // No longer needed
    val filteredTasks = viewModel.getFilteredTasks(state.tasks, state.currentFilter)

    val filteredState = state.copy(tasks = filteredTasks)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        MainScreen(
            state = filteredState,
            onTaskClick = { /* TODO: Navigate to task details */ },
            onTaskComplete = { /* TODO: Handle task completion */ },
            onTaskDelete = { /* TODO: Handle task deletion */ },
            onCreateTask = { /* TODO: Show create dialog */ },
            navController = navController
        )
    }
}