package com.homeplanner.ui.tasks

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import com.homeplanner.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSelectionDialogScreen(navController: NavHostController) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Выбор пользователя") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                ListItem(
                    headlineContent = { Text("Не выбран") },
                    leadingContent = {
                        RadioButton(
                            selected = state.selectedUser == null,
                            onClick = {
                                viewModel.clearSelectedUser()
                                navController.popBackStack()
                            }
                        )
                    }
                )
            }
            items(state.users) { user ->
                ListItem(
                    headlineContent = { Text(user.name) },
                    leadingContent = {
                        RadioButton(
                            selected = state.selectedUser?.id == user.id,
                            onClick = {
                                viewModel.saveSelectedUser(user)
                                navController.popBackStack()
                            }
                        )
                    }
                )
            }
        }
    }
}