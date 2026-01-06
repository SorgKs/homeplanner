package com.homeplanner.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homeplanner.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier
) {
    val viewModel: SettingsViewModel = org.koin.androidx.compose.koinViewModel()
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Настройки",
            style = MaterialTheme.typography.titleLarge
        )

        UserSelectionSection(
            users = state.users,
            selectedUser = state.selectedUser,
            isLoading = state.isUsersLoading,
            networkConfig = state.networkConfig,
            onSelectUser = { navController.navigate("settings_user_selection") },
            onClearUser = { viewModel.clearSelectedUser() },
            onRefreshUsers = { viewModel.refreshUsersFromServer() }
        )

        AppVersionSection()

        DebugPanelSection(state)

        NetworkSettingsSection(
            networkConfig = state.networkConfig,
            onEditClick = { navController.navigate("settings_network_edit") },
            onClearClick = { viewModel.clearNetworkConfig() },
            onQrScanClick = { navController.navigate("settings_qr_scanner") },
            onTestConnection = { navController.navigate("settings_connection_test") },
            isTesting = false // TODO: Add testing state to ViewModel
        )
    }
}