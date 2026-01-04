package com.homeplanner.ui.tasks

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Today : Screen("today", "Сегодня", Icons.Filled.Home)
    object AllTasks : Screen("all_tasks", "Все задачи", Icons.AutoMirrored.Filled.List)
    object Settings : Screen("settings", "Настройки", Icons.Filled.Settings)
}