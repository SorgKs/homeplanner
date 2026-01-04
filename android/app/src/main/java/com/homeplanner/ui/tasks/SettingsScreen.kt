package com.homeplanner.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.homeplanner.*
import com.homeplanner.model.User
import com.homeplanner.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel




@Composable
private fun NetworkSettingsSection(
    networkConfig: NetworkConfig?,
    onEditClick: () -> Unit,
    onClearClick: () -> Unit,
    onQrScanClick: () -> Unit,
    onTestConnection: () -> Unit,
    isTesting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (networkConfig == null) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Сетевые настройки",
                style = MaterialTheme.typography.titleMedium
            )

            val config = networkConfig
            if (config != null) {
                val apiUrl = config.toApiBaseUrl()
                val host = config.host
                val port = config.port
                val apiVersion = config.apiVersion
                val protocol = if (config.useHttps) "HTTPS" else "HTTP"
                Text(
                    text = "API URL: $apiUrl",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Хост: $host",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Порт: $port",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "API Версия: $apiVersion",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Протокол: $protocol",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                // Показывать дефолтные значения если настройки не заданы
                val defaultHost = "localhost"
                val defaultPort = 8000
                val defaultApiVersion = SupportedApiVersions.defaultVersion
                val defaultUseHttps = true
                val apiUrl = NetworkConfig(defaultHost, defaultPort, defaultApiVersion, defaultUseHttps).toApiBaseUrl()
                val protocol = if (defaultUseHttps) "HTTPS" else "HTTP"
                Text(
                    text = "⚠️ Подключение не настроено",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "API URL: $apiUrl (по умолчанию)",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Хост: $defaultHost (по умолчанию)",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Порт: $defaultPort (по умолчанию)",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "API Версия: $defaultApiVersion (по умолчанию)",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Протокол: $protocol (по умолчанию)",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onEditClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (networkConfig == null) "Настроить" else "Изменить")
                }

                if (networkConfig != null) {
                    Button(
                        onClick = onClearClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Сбросить")
                    }
                }
            }

            Button(
                onClick = onQrScanClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сканировать QR-код")
            }

            if (networkConfig != null) {
                Button(
                    onClick = onTestConnection,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting
                ) {
                    Text(if (isTesting) "Проверка..." else "Проверить подключение")
                }
            }
        }
    }
}

@Composable
private fun AppVersionSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Версия приложения",
                style = MaterialTheme.typography.titleSmall
            )
            val debugStatus = if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"
            Text(text = "Версия: ${BuildConfig.VERSION_NAME} ($debugStatus)")
        }
    }
}

@Composable
private fun UserSelectionSection(
    users: List<User>,
    selectedUser: SelectedUser?,
    isLoading: Boolean,
    networkConfig: NetworkConfig?,
    onSelectUser: () -> Unit,
    onClearUser: () -> Unit,
    onRefreshUsers: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Выбор пользователя",
                style = MaterialTheme.typography.titleMedium
            )

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (users.isEmpty()) {
                Text(
                    text = "Пользователи не найдены",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val userOptions = listOf("Не выбран") + users.map { it.name }
                val currentSelection = selectedUser?.name ?: "Не выбран"

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Текущий пользователь: $currentSelection",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSelectUser,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Выбрать")
                        }

                        if (selectedUser != null) {
                            Button(
                                onClick = onClearUser,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Text("Сбросить")
                            }
                        }
                    }

                    // Refresh users button
                    Button(
                        onClick = onRefreshUsers,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && networkConfig != null
                    ) {
                        Text(if (isLoading) "Обновление..." else "Обновить список пользователей")
                    }
                }
            }
        }
    }
}

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