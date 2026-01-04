package com.homeplanner.ui.tasks

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.homeplanner.model.Task
import com.homeplanner.viewmodel.TaskScreenState

@Composable
fun MainScreen(
    state: TaskScreenState,
    onTaskClick: (Task) -> Unit,
    onTaskComplete: (Int) -> Unit,
    onTaskDelete: (Int) -> Unit,
    onCreateTask: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    Screen.Today,
                    Screen.AllTasks,
                    Screen.Settings
                )
                items.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Today.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Today.route) {
                TodayScreen(
                    state = state,
                    onCreateTask = onCreateTask,
                    onTaskClick = onTaskClick,
                    onTaskComplete = onTaskComplete,
                    onTaskDelete = onTaskDelete
                )
            }
            composable(Screen.AllTasks.route) {
                AllTasksScreen(
                    state = state,
                    onCreateTask = onCreateTask,
                    onTaskClick = onTaskClick,
                    onTaskComplete = onTaskComplete,
                    onTaskDelete = onTaskDelete
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(navController)
            }
            composable("settings_user_selection") {
                UserSelectionDialogScreen(navController)
            }
            composable("settings_network_edit") {
                // TODO: NetworkEditDialogScreen(navController)
                androidx.compose.material3.Text("Network Edit Dialog - TODO")
            }
            composable("settings_connection_test") {
                // TODO: ConnectionTestDialogScreen(navController)
                androidx.compose.material3.Text("Connection Test Dialog - TODO")
            }
            composable("settings_qr_scanner") {
                // TODO: QrScannerDialogScreen(navController)
                androidx.compose.material3.Text("QR Scanner Dialog - TODO")
            }
        }
    }
}