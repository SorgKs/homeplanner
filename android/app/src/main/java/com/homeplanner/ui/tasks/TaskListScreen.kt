package com.homeplanner.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homeplanner.model.Task
import com.homeplanner.viewmodel.TaskScreenState
import com.homeplanner.UiEvent
import com.homeplanner.NetworkSettings
import com.homeplanner.NetworkConfig
import com.homeplanner.SupportedApiVersions
import com.homeplanner.QrCodeScannerDialog
import com.homeplanner.BuildConfig
import com.homeplanner.UserSettings
import com.homeplanner.SelectedUser
import com.homeplanner.api.LocalApi
import com.homeplanner.model.User
import com.homeplanner.database.AppDatabase
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.utils.TaskDateCalculator
import com.homeplanner.debug.BinaryLogger
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class TabItem(val title: String, val icon: @Composable () -> Unit, val event: UiEvent)

@Composable
fun TaskListScreen(
    state: TaskScreenState,
    onEvent: (UiEvent) -> Unit,
    onTaskClick: (Task) -> Unit,
    onTaskComplete: (Int) -> Unit,
    onTaskDelete: (Int) -> Unit,
    onCreateTask: () -> Unit
) {
    android.util.Log.i("TaskListScreen", "TaskListScreen composable called")
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Log tab changes
    LaunchedEffect(selectedTabIndex) {
        android.util.Log.i("TaskListScreen", "Selected tab changed to: $selectedTabIndex")
    }

    val tabs = listOf(
        TabItem("Сегодня", { Icon(Icons.Filled.Home, contentDescription = "Сегодня") }, UiEvent.NavigateToTodayTab),
        TabItem("Все задачи", { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Все задачи") }, UiEvent.NavigateToAllTasksTab),
        TabItem("Настройки", { Icon(Icons.Filled.Settings, contentDescription = "Настройки") }, UiEvent.NavigateToSettingsTab)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = {
                            selectedTabIndex = index
                            onEvent(tab.event)
                        },
                        icon = tab.icon,
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { paddingValues ->
        when (selectedTabIndex) {
            0 -> { // Сегодня
                android.util.Log.i("TaskListScreen", "Rendering tab 0 (Сегодня)")
                android.util.Log.i("TaskListScreen", "About to render Today tab content")
                when {
                    state.isLoading -> LoadingIndicator(modifier = Modifier.padding(paddingValues))
                    state.error != null -> ErrorMessage(state.error, modifier = Modifier.padding(paddingValues))
                    state.tasks.isEmpty() -> EmptyState(onCreateTask, modifier = Modifier.padding(paddingValues))
                    else -> {
                        // Фильтровать задачи на сегодня
                        val todayTasks = state.tasks.filter { task ->
                            // Простая фильтрация: задачи, активные и не завершенные
                            task.active && !task.completed
                        }
                        if (todayTasks.isEmpty()) {
                            EmptyState(onCreateTask, modifier = Modifier.padding(paddingValues))
                        } else {
                            AllTasksList(
                                tasks = todayTasks,
                                onTaskClick = onTaskClick,
                                onTaskComplete = onTaskComplete,
                                onTaskDelete = onTaskDelete,
                                modifier = Modifier.padding(paddingValues)
                            )
                        }
                    }
                }
            }
            1 -> { // Все задачи
                android.util.Log.i("TaskListScreen", "Rendering tab 1 (Все задачи)")
                android.util.Log.i("TaskListScreen", "About to render All Tasks tab content")
                when {
                    state.isLoading -> LoadingIndicator(modifier = Modifier.padding(paddingValues))
                    state.error != null -> ErrorMessage(state.error, modifier = Modifier.padding(paddingValues))
                    state.tasks.isEmpty() -> EmptyState(onCreateTask, modifier = Modifier.padding(paddingValues))
                    else -> {
                        AllTasksList(
                            tasks = state.tasks,
                            onTaskClick = onTaskClick,
                            onTaskComplete = onTaskComplete,
                            onTaskDelete = onTaskDelete,
                            modifier = Modifier.padding(paddingValues)
                        )
                    }
                }
            }
            2 -> { // Настройки
                android.util.Log.i("TaskListScreen", "Rendering tab 2 (Настройки)")
                android.util.Log.i("TaskListScreen", "About to call SettingsScreen composable")
                SettingsScreen(modifier = Modifier.padding(paddingValues))
            }
        }
    }
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorMessage(error: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Error: ${error ?: "Unknown error"}")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { /* TODO: retry */ }) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun EmptyState(onCreateTask: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "No tasks yet")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCreateTask) {
                Text("Create first task")
            }
        }
    }
}

@Composable
private fun AllTasksList(
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    onTaskComplete: (Int) -> Unit,
    onTaskDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(tasks) { task ->
            TaskItem(
                task = task,
                onComplete = onTaskComplete,
                onClick = onTaskClick,
                onDelete = onTaskDelete
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(modifier: Modifier = Modifier) {
    android.util.Log.i("SettingsScreen", "SettingsScreen composable called")
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val networkSettings = remember { NetworkSettings(context) }
    val networkConfig by networkSettings.configFlow.collectAsState(initial = null)

    val userSettings = remember { UserSettings(context) }
    val selectedUser by userSettings.selectedUserFlow.collectAsState(initial = null)

    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var isUsersLoading by remember { mutableStateOf(false) }
    var showUserSelectionDialog by remember { mutableStateOf(false) }

    // Log when settings screen is opened
    LaunchedEffect(Unit) {
        android.util.Log.i("SettingsScreen", "SettingsScreen opened, networkConfig = $networkConfig")
        android.util.Log.i("SettingsScreen", "Users list size: ${users.size}")
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showConnectionTest by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<String?>(null) }
    var isLoadingTest by remember { mutableStateOf(false) }

    // Edit dialog state
    var editHost by remember { mutableStateOf("") }
    var editPort by remember { mutableStateOf("8000") }
    var editApiVersion by remember { mutableStateOf(SupportedApiVersions.defaultVersion) }
    var editUseHttps by remember { mutableStateOf(true) }
    var editApiVersionExpanded by remember { mutableStateOf(false) }

    suspend fun refreshUsersFromServer() {
        try {
            android.util.Log.d("SettingsScreen", "Attempting to refresh users from server")
            // Get network config
            val networkConfig = networkConfig
            if (networkConfig == null) {
                android.util.Log.w("SettingsScreen", "No network config, cannot refresh users from server")
                return
            }

            val apiBaseUrl = networkConfig.toApiBaseUrl()
            android.util.Log.d("SettingsScreen", "Using API base URL: $apiBaseUrl")

            val api = com.homeplanner.api.UsersServerApi(baseUrl = apiBaseUrl)
            android.util.Log.d("SettingsScreen", "Created UsersServerApi, calling getUsers()")
            // syncCacheWithServer: вызов загрузки пользователей с сервера
            BinaryLogger.getInstance()?.log(400u, emptyList())
            val fetchedSummaries = withContext(Dispatchers.IO) {
                api.getUsers()
            }
            android.util.Log.d("SettingsScreen", "Successfully fetched ${fetchedSummaries.size} users from server")
            android.util.Log.d("SettingsScreen", "Fetched users: ${fetchedSummaries.map { "${it.id}:${it.name}" }}")
            // syncCacheWithServer: получен ответ от сервера с пользователями
            BinaryLogger.getInstance()?.log(401u, listOf(fetchedSummaries.size))

            // Convert UserSummary to User model
            val fetchedUsers = fetchedSummaries.map { summary ->
                com.homeplanner.model.User(
                    id = summary.id,
                    name = summary.name
                )
            }
            android.util.Log.d("SettingsScreen", "Converted to User models: ${fetchedUsers.size} users")

            // Update UI
            android.util.Log.d("SettingsScreen", "Updating UI with users: ${fetchedUsers.map { "${it.id}:${it.name}" }}")
            users = fetchedUsers
            android.util.Log.d("SettingsScreen", "UI updated, users.size = ${users.size}")

            // Save to cache
            val db = AppDatabase.getDatabase(context)
            val offlineRepository = OfflineRepository(db, context)
            withContext(Dispatchers.IO) {
                offlineRepository.saveUsersToCache(fetchedUsers)
            }
            android.util.Log.d("SettingsScreen", "Saved users to local cache")
            // syncCacheWithServer: пользователи сохранены в локальный кеш
            BinaryLogger.getInstance()?.log(402u, listOf(fetchedUsers.size))
        } catch (e: Exception) {
            android.util.Log.e("SettingsScreen", "Failed to refresh users from server", e)
            e.printStackTrace()
        }
    }

    // Load users from cache first
    LaunchedEffect(Unit) {
        android.util.Log.d("SettingsScreen", "Starting to load users from local cache")
        isUsersLoading = true
        try {
            val db = AppDatabase.getDatabase(context)
            val offlineRepository = OfflineRepository(db, context)
            val localApi = LocalApi(offlineRepository, TaskDateCalculator)
            val result = localApi.getUsersLocal()
            users = result.getOrDefault(emptyList())
            android.util.Log.d("SettingsScreen", "Loaded ${users.size} users from local cache")
        } catch (e: Exception) {
            android.util.Log.e("SettingsScreen", "Error loading users from local cache", e)
            // Handle error silently or show toast
        } finally {
            isUsersLoading = false
        }
    }

    // Refresh from server when network config is available
    LaunchedEffect(networkConfig) {
        android.util.Log.i("SettingsScreen", "LaunchedEffect(networkConfig) triggered, config = $networkConfig")
        val config = networkConfig
        if (config != null) {
            android.util.Log.d("SettingsScreen", "Network config available, refreshing users from server")
            isUsersLoading = true
            try {
                refreshUsersFromServer()
            } finally {
                isUsersLoading = false
            }
        } else {
            android.util.Log.d("SettingsScreen", "Network config not available, skipping server refresh")
        }
    }

    // Initialize edit dialog with current config
    LaunchedEffect(networkConfig, showEditDialog) {
        val config = networkConfig
        if (showEditDialog && config != null) {
            editHost = config.host
            editPort = config.port.toString()
            editApiVersion = config.apiVersion
            editUseHttps = config.useHttps
        } else if (showEditDialog && config == null) {
            editHost = ""
            editPort = "8000"
            editApiVersion = SupportedApiVersions.defaultVersion
            editUseHttps = true
        }
    }

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

        // User selection
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

                if (isUsersLoading) {
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
                                onClick = { showUserSelectionDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Выбрать")
                            }

                            if (selectedUser != null) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            userSettings.clearSelectedUser()
                                        }
                                    },
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
                            onClick = {
                                scope.launch {
                                    isUsersLoading = true
                                    try {
                                        refreshUsersFromServer()
                                    } finally {
                                        isUsersLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isUsersLoading && networkConfig != null
                        ) {
                            Text(if (isUsersLoading) "Обновление..." else "Обновить список пользователей")
                        }
                    }
                }
            }
        }

        // Version info
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

        // Network settings
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
                        onClick = { showEditDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (networkConfig == null) "Настроить" else "Изменить")
                    }

                    if (networkConfig != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    networkSettings.clearConfig()
                                }
                            },
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
                    onClick = { showQrScanner = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Сканировать QR-код")
                }

                if (networkConfig != null) {
                    Button(
                        onClick = {
                            showConnectionTest = true
                            isLoadingTest = true
                            connectionTestResult = null
                            scope.launch {
                                try {
                                    val results = mutableListOf<String>()
                                    val config = networkConfig

                                    if (config == null) {
                                        results.add("✗ Настройки сети не заданы")
                                        connectionTestResult = results.joinToString("\n")
                                        return@launch
                                    }

                                    // Test HTTP connection (simplified)
                                    results.add("✓ Настройки сети заданы")

                                    connectionTestResult = results.joinToString("\n")
                                } finally {
                                    isLoadingTest = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoadingTest
                    ) {
                        Text(if (isLoadingTest) "Проверка..." else "Проверить подключение")
                    }
                }
            }
        }
    }

    // User selection dialog
    if (showUserSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showUserSelectionDialog = false },
            title = { Text("Выбор пользователя") },
            text = {
                LazyColumn {
                    item {
                        ListItem(
                            headlineContent = { Text("Не выбран") },
                            leadingContent = {
                                RadioButton(
                                    selected = selectedUser == null,
                                    onClick = {
                                        scope.launch {
                                            userSettings.clearSelectedUser()
                                            showUserSelectionDialog = false
                                        }
                                    }
                                )
                            }
                        )
                    }
                    items(users) { user ->
                        ListItem(
                            headlineContent = { Text(user.name) },
                            leadingContent = {
                                RadioButton(
                                    selected = selectedUser?.id == user.id,
                                    onClick = {
                                        scope.launch {
                                            userSettings.saveSelectedUser(SelectedUser(user.id, user.name))
                                            showUserSelectionDialog = false
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUserSelectionDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    // Edit dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Настройка подключения") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editHost,
                        onValueChange = { editHost = it },
                        label = { Text("Хост (IP или домен)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editPort,
                        onValueChange = { editPort = it },
                        label = { Text("Порт") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    ExposedDropdownMenuBox(
                        expanded = editApiVersionExpanded,
                        onExpandedChange = { editApiVersionExpanded = !editApiVersionExpanded }
                    ) {
                        OutlinedTextField(
                            value = editApiVersion,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("API Версия") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editApiVersionExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        DropdownMenu(
                            expanded = editApiVersionExpanded,
                            onDismissRequest = { editApiVersionExpanded = false }
                        ) {
                            SupportedApiVersions.versions.forEach { version ->
                                DropdownMenuItem(
                                    text = { Text(version) },
                                    onClick = {
                                        editApiVersion = version
                                        editApiVersionExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Использовать HTTPS")
                        Switch(
                            checked = editUseHttps,
                            onCheckedChange = { editUseHttps = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val port = editPort.toIntOrNull()
                        if (editHost.isBlank() || port == null || port !in 1..65535) {
                            // Validation error - could show toast here
                            return@TextButton
                        }
                        if (editApiVersion !in SupportedApiVersions.versions) {
                            return@TextButton
                        }
                        scope.launch {
                            val config = NetworkConfig(
                                host = editHost.trim(),
                                port = port,
                                apiVersion = editApiVersion,
                                useHttps = editUseHttps
                            )
                            networkSettings.saveConfig(config)
                            showEditDialog = false
                        }
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Connection test dialog
    if (showConnectionTest) {
        AlertDialog(
            onDismissRequest = { showConnectionTest = false },
            title = { Text("Проверка подключения") },
            text = {
                if (isLoadingTest) {
                    Text("Проверка подключения...")
                } else {
                    Text(connectionTestResult ?: "")
                }
            },
            confirmButton = {
                TextButton(onClick = { showConnectionTest = false }) {
                    Text("OK")
                }
            }
        )
    }

    // QR Scanner dialog
    if (showQrScanner) {
        AlertDialog(
            onDismissRequest = { showQrScanner = false },
            title = { Text("Сканер QR-кода") },
            text = {
                Box(modifier = Modifier.height(400.dp)) {
                    QrCodeScannerDialog(
                        onScanResult = { qrContent ->
                            scope.launch {
                                val success = networkSettings.parseAndSaveFromJson(qrContent)
                                if (success) {
                                    showQrScanner = false
                                    // Show success message (could use Toast here)
                                } else {
                                    // Show error message
                                    connectionTestResult = "Неверный формат QR-кода. Убедитесь, что QR-код содержит настройки подключения."
                                    showConnectionTest = true
                                }
                            }
                        },
                        onDismiss = { showQrScanner = false }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrScanner = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}