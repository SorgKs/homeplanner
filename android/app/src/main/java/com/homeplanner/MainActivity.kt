package com.homeplanner

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import com.homeplanner.BuildConfig
import com.homeplanner.SelectedUser
import com.homeplanner.UserSettings
import com.homeplanner.debug.BinaryLogger
import com.homeplanner.debug.ChunkSender
import com.homeplanner.debug.LogCleanupManager
import com.homeplanner.debug.LogSender
import com.homeplanner.debug.LogLevel
import com.homeplanner.debug.LogMessageCode
import com.homeplanner.api.TasksApi
import com.homeplanner.api.GroupsApi
import com.homeplanner.api.UsersApi
import com.homeplanner.api.UserSummary
import com.homeplanner.api.LocalApi
import com.homeplanner.database.AppDatabase
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.sync.SyncService
import com.homeplanner.model.Task
import com.homeplanner.utils.TodayTaskFilter
import com.homeplanner.ui.AppStatusBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult

// View tabs for bottom navigation
enum class ViewTab { TODAY, ALL, SETTINGS }

// Connection status enum
enum class ConnectionStatus {
    UNKNOWN,    // Серая иконка - начальное состояние при старте
    ONLINE,     // Зеленая иконка - есть успешная попытка и не превышено время
    DEGRADED,   // Желтая иконка - есть неуспешные попытки, но меньше 5 подряд
    OFFLINE     // Красная иконка - 5 неуспешных подряд или превышено время
}

class MainActivity : ComponentActivity() {
    // Activity Result Launcher for notification permission
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "POST_NOTIFICATIONS permission granted")
        } else {
            Log.w("MainActivity", "POST_NOTIFICATIONS permission denied")
        }
    }

    // Activity Result Launcher for exact alarm settings
    val requestExactAlarmSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Check if permission was granted after returning from settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (am != null && am.canScheduleExactAlarms()) {
                Log.d("MainActivity", "SCHEDULE_EXACT_ALARM permission granted")
            } else {
                Log.w("MainActivity", "SCHEDULE_EXACT_ALARM permission not granted")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensurePermissions()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TasksScreen()
                }
            }
        }
    }

    private fun ensurePermissions() {
        // Request all permissions at once when app is first launched
        Log.d("MainActivity", "Requesting all required permissions")
        
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        
        // Notifications permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.d("MainActivity", "Requesting POST_NOTIFICATIONS permission")
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Log.d("MainActivity", "POST_NOTIFICATIONS permission already granted")
            }
        }
        
        // Exact alarms permission (Android 12+)
        // Устанавливаем флаг, что нужно показать диалог с объяснением (будет показан в UI)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (am != null && !am.canScheduleExactAlarms()) {
                // Проверяем, был ли уже показан запрос
                val exactAlarmRequestShown = prefs.getBoolean("exact_alarm_request_shown", false)
                if (!exactAlarmRequestShown) {
                    Log.d("MainActivity", "SCHEDULE_EXACT_ALARM permission needed, will show dialog in UI")
                    // Устанавливаем флаг, что нужно показать диалог
                    prefs.edit().putBoolean("show_exact_alarm_dialog", true).apply()
                } else {
                    Log.d("MainActivity", "SCHEDULE_EXACT_ALARM permission request was already shown, skipping")
                }
            } else {
                Log.d("MainActivity", "SCHEDULE_EXACT_ALARM permission already granted")
            }
        }
    }
}

private fun resolveWebSocketUrl(networkConfig: NetworkConfig?): String? {
    return networkConfig?.toWebSocketUrl()
}

@Composable
@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
fun TasksScreen() {
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var users by remember { mutableStateOf<List<com.homeplanner.api.UserSummary>>(emptyList()) }
    // Key used to trigger refresh via LaunchedEffect (full sync with server)
    var refreshKey by remember { mutableStateOf(0) }
    // Key used to trigger UI update from cache only (no server sync)
    var uiRefreshKey by remember { mutableStateOf(0) }
    // Store all tasks (offline-first: always loaded from cache, filtered by getVisibleTasks())
    var allTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    // Store last known today task IDs for offline mode
    var lastTodayTaskIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // Storage info for settings
    var cachedTasksCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(ViewTab.TODAY) }
    var wsConnected by remember { mutableStateOf(false) }
    var wsConnecting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val reminderScheduler = remember(context) { ReminderScheduler(context) }
    
    // Диалог для запроса разрешения на точные будильники
    var showExactAlarmDialog by remember { mutableStateOf(false) }
    val mainActivity = context as? MainActivity
    
    // Network settings
    val networkSettings = remember { NetworkSettings(context) }
    val userSettings = remember { UserSettings(context) }
    val networkConfig by networkSettings.configFlow.collectAsState(initial = null)
    val apiBaseUrl = remember(networkConfig) {
        try {
            networkConfig?.toApiBaseUrl()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to create API base URL from network config", e)
            null
        }
    }
    val selectedUser by userSettings.selectedUserFlow.collectAsState(initial = null)
    
    // Offline support: инициализация базы данных и репозиториев
    val database = remember(context) { AppDatabase.getDatabase(context) }
    val offlineRepository = remember(database, context) { OfflineRepository(database, context) }
    var storagePercentage by remember { mutableStateOf(0f) }
    var pendingOperations by remember { mutableStateOf(0) }
    var isOnline by remember { mutableStateOf(false) } // Для обратной совместимости с существующим кодом
    var isSyncing by remember { mutableStateOf(false) }
    var statusBarCompactMode by remember { mutableStateOf(getStatusBarCompactMode(context)) }
    
    // Connection status tracking
    // Статус соединения не зависит от наличия конфигурации - только от реальных запросов к серверу
    var connectionStatus by remember { mutableStateOf(ConnectionStatus.UNKNOWN) }
    var lastSuccessfulRequestTime by remember { mutableStateOf<Long?>(null) }
    var consecutiveFailures by remember { mutableStateOf(0) }
    var connectionCheckIntervalMinutes by remember { mutableStateOf(5) }
    
    // Hash tracking for data version validation
    var localDataHash by remember { mutableStateOf<String?>(null) }
    var markedChangedTaskIds by remember { mutableStateOf(setOf<Int>()) }
    
    // Function to mark successful request
    fun markSuccessfulRequest() {
        lastSuccessfulRequestTime = System.currentTimeMillis()
        consecutiveFailures = 0
        connectionStatus = ConnectionStatus.ONLINE
        isOnline = true
        android.util.Log.d("TasksScreen", "Marked successful request, status=ONLINE")
        BinaryLogger.getInstance()?.log(com.homeplanner.debug.LogLevel.INFO, "MainActivity", com.homeplanner.debug.LogMessageCode.CONNECTION_ONLINE, emptyMap<String, Any>())
    }
    
    // Function to mark failed request
    fun markFailedRequest() {
        consecutiveFailures++
        android.util.Log.d("TasksScreen", "Marked failed request, consecutive failures=$consecutiveFailures")

        // Обновляем статус на основе количества неудач
        connectionStatus = when {
            consecutiveFailures >= 5 -> {
                android.util.Log.d("TasksScreen", "5+ consecutive failures, status=OFFLINE")
                BinaryLogger.getInstance()?.log(com.homeplanner.debug.LogLevel.ERROR, "MainActivity", com.homeplanner.debug.LogMessageCode.CONNECTION_OFFLINE, mapOf<String, Any>("failures" to consecutiveFailures))
                ConnectionStatus.OFFLINE
            }
            consecutiveFailures > 0 -> {
                android.util.Log.d("TasksScreen", "1-4 consecutive failures, status=DEGRADED")
                BinaryLogger.getInstance()?.log(com.homeplanner.debug.LogLevel.WARN, "MainActivity", com.homeplanner.debug.LogMessageCode.CONNECTION_DEGRADED, mapOf<String, Any>("failures" to consecutiveFailures))
                ConnectionStatus.DEGRADED
            }
            else -> connectionStatus // Не должно случиться, но на всякий случай
        }
        isOnline = false
    }
    
    val baseTasksApi = remember(apiBaseUrl, selectedUser) {
        TasksApi(baseUrl = apiBaseUrl ?: BuildConfig.API_BASE_URL, selectedUserId = selectedUser?.id)
    }
    val syncService = remember(baseTasksApi, offlineRepository) {
        SyncService(offlineRepository, baseTasksApi, context)
    }
    val localApi = remember(offlineRepository) {
        LocalApi(offlineRepository)
    }
    
    // Initialize binary logging for debug builds (only when network config is available)
    LaunchedEffect(networkConfig) {
        val currentConfig = networkConfig
        if (BuildConfig.DEBUG && currentConfig != null) {
            // Initialize BinaryLogger
            BinaryLogger.initialize(context)
            
            // Start LogSender for sending logs to server (JSON v1 format)
            LogSender.start(context, currentConfig)
            
            // Link BinaryLogger with LogSender
            val logger = BinaryLogger.getInstance()
            val sender = LogSender.getInstance()
            if (logger != null && sender != null) {
                logger.setLogSender(sender)
            }
            
            // Start ChunkSender for sending binary chunks (v2 format)
            val storage = BinaryLogger.getStorage()
            if (storage != null) {
                ChunkSender.start(context, currentConfig, storage)
            }
            
            // Start LogCleanupManager for automatic cleanup of old logs
            LogCleanupManager.start(context)
        }
    }
    
    // Load connection check interval from settings
    // Проверяем, нужно ли показать диалог запроса разрешения на точные будильники
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val showDialog = prefs.getBoolean("show_exact_alarm_dialog", false)
            if (showDialog) {
                showExactAlarmDialog = true
            }
        }
    }
    
    LaunchedEffect(userSettings) {
        scope.launch {
            connectionCheckIntervalMinutes = userSettings.getConnectionCheckIntervalMinutes()
        }
    }
    
    // Also observe flow for real-time updates
    val connectionCheckIntervalFlow by userSettings.connectionCheckIntervalMinutesFlow.collectAsState(initial = 5)
    LaunchedEffect(connectionCheckIntervalFlow) {
        connectionCheckIntervalMinutes = connectionCheckIntervalFlow
    }
    // Function to update connection status based on time since last success
    // Статус не зависит от наличия конфигурации - только от реальных запросов
    fun updateConnectionStatusByTime() {
        val now = System.currentTimeMillis()
        val intervalMs = connectionCheckIntervalMinutes * 60 * 1000L
        
        // Если есть последняя успешная попытка, проверяем время
        if (lastSuccessfulRequestTime != null) {
            val timeSinceLastSuccess = now - lastSuccessfulRequestTime!!
            if (timeSinceLastSuccess >= intervalMs) {
                // Превышено время с последней успешной попытки
                android.util.Log.d("TasksScreen", "Time exceeded since last success (${timeSinceLastSuccess / 1000}s), status=OFFLINE")
                connectionStatus = ConnectionStatus.OFFLINE
                isOnline = false
                return
            }
        }
        
        // Если статус еще UNKNOWN и нет успешных попыток, оставляем UNKNOWN
        // Иначе статус уже установлен markSuccessfulRequest/markFailedRequest
    }
    
    // Function to perform diagnostic request
    suspend fun performDiagnosticRequest(): Boolean {
        if (apiBaseUrl == null) return false
        return try {
            val api = UsersApi(baseUrl = apiBaseUrl)
            withContext(Dispatchers.IO) {
                api.getUsers()
                true
            }
        } catch (e: Exception) {
            android.util.Log.d("TasksScreen", "Diagnostic request failed: ${e.message}")
            false
        }
    }
    
    // Обновляем статус при изменении конфигурации
    LaunchedEffect(networkConfig, apiBaseUrl) {
        if (networkConfig == null || apiBaseUrl == null) {
            connectionStatus = ConnectionStatus.OFFLINE
            isOnline = false
            lastSuccessfulRequestTime = null
            consecutiveFailures = 0
        } else if (connectionStatus == ConnectionStatus.OFFLINE && lastSuccessfulRequestTime == null) {
            // Если конфигурация появилась, но еще не было попыток - статус UNKNOWN
            connectionStatus = ConnectionStatus.UNKNOWN
        }
    }
    
    LaunchedEffect(syncService, offlineRepository, networkConfig, apiBaseUrl, lastSuccessfulRequestTime, connectionCheckIntervalMinutes, consecutiveFailures) {
        while (true) {
            storagePercentage = offlineRepository.getStoragePercentage()
            pendingOperations = offlineRepository.getPendingOperationsCount()
            // Update cached tasks count for settings
            scope.launch {
                cachedTasksCount = offlineRepository.getCachedTasksCount()
            }
            
            // Обновляем статус на основе времени с последней успешной попытки
            updateConnectionStatusByTime()
            
            // Если статус ONLINE и прошло больше интервала - делаем диагностический запрос
            val now = System.currentTimeMillis()
            val intervalMs = connectionCheckIntervalMinutes * 60 * 1000L
            if (connectionStatus == ConnectionStatus.ONLINE && 
                lastSuccessfulRequestTime != null && 
                (now - lastSuccessfulRequestTime!!) >= intervalMs) {
                android.util.Log.d("TasksScreen", "Performing diagnostic request (${(now - lastSuccessfulRequestTime!!) / 1000}s since last request)")
                val diagnosticSuccess = performDiagnosticRequest()
                if (diagnosticSuccess) {
                    markSuccessfulRequest()
                } else {
                    markFailedRequest()
                }
            }
            
            delay(5000)
        }
    }
    
    // Периодическая фоновая синхронизация (offline-first стратегия)
    LaunchedEffect(syncService, offlineRepository, connectionStatus) {
        while (true) {
            delay(30_000L) // Синхронизация каждые 30 секунд
            
            // Синхронизируем только если есть соединение и есть операции в очереди
            if (connectionStatus == ConnectionStatus.ONLINE && pendingOperations > 0) {
                android.util.Log.d("TasksScreen", "Periodic sync: syncing ${pendingOperations} pending operations")
                isSyncing = true
                try {
                    val syncResult = syncService.syncQueue()
                    if (syncResult.isSuccess) {
                        markSuccessfulRequest()
                        android.util.Log.d("TasksScreen", "Periodic sync: completed successfully")
                    } else {
                        android.util.Log.w("TasksScreen", "Periodic sync: had failures")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("TasksScreen", "Periodic sync: error", e)
                } finally {
                    isSyncing = false
                }
            }
        }
    }
    
    // Function to test notification for a task
    fun testNotificationForTask(task: com.homeplanner.model.Task) {
        android.util.Log.d("TasksScreen", "Testing notification for task ${task.id}: ${task.title}")
        try {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("title", task.title)
                val groupName = task.groupId?.let { groups[it] } ?: ""
                val msg = if (groupName.isNotEmpty()) "Задача: ${task.title} (группа ${groupName})" else "Задача: ${task.title}"
                putExtra("message", msg)
                putExtra("taskId", task.id)
            }
            // Create a receiver instance and call onReceive directly
            val receiver = ReminderReceiver()
            receiver.onReceive(context, intent)
            android.util.Log.d("TasksScreen", "Notification test triggered for task ${task.id}")
        } catch (e: Exception) {
            android.util.Log.e("TasksScreen", "Failed to test notification for task ${task.id}", e)
        }
    }
    
    var showEditDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editTaskType by remember { mutableStateOf("one_time") }
    var editRecurrenceType by remember { mutableStateOf<String?>(null) }
    var editRecurrenceInterval by remember { mutableStateOf<String>("") }
    var editIntervalDays by remember { mutableStateOf<String>("") }
    var editReminderTime by remember { mutableStateOf("") }
    var editGroupId by remember { mutableStateOf<Int?>(null) }
    var editAssignedUserIds by remember { mutableStateOf<List<Int>>(emptyList()) }
    var editTaskTypeExpanded by remember { mutableStateOf(false) }
    var editRecurrenceTypeExpanded by remember { mutableStateOf(false) }
    var editGroupExpanded by remember { mutableStateOf(false) }
    var editUsersExpanded by remember { mutableStateOf(false) }

    // Validation state
    var titleError by remember { mutableStateOf<String?>(null) }
    var reminderTimeError by remember { mutableStateOf<String?>(null) }
    var recurrenceError by remember { mutableStateOf<String?>(null) }

    // Date/Time pickers (use platform dialogs for reliability)
    val activity = (context as? ComponentActivity)
    fun pickDateTime(initial: LocalDateTime?, onPicked: (LocalDateTime) -> Unit) {
        val now = initial ?: LocalDateTime.now()
        val datePicker = android.app.DatePickerDialog(
            context,
            { _, y, m, d ->
                val timePicker = android.app.TimePickerDialog(
                    context,
                    { _, h, min ->
                        onPicked(LocalDateTime.of(y, m + 1, d, h, min))
                    },
                    now.hour,
                    now.minute,
                    true
                )
                timePicker.show()
            },
            now.year,
            now.monthValue - 1,
            now.dayOfMonth
        )
        datePicker.show()
    }

    fun formatIso(dt: LocalDateTime): String = dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    fun parseIsoOrNull(s: String?): LocalDateTime? = try { if (s.isNullOrBlank()) null else LocalDateTime.parse(s) } catch (_: Exception) { null }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteTaskId by remember { mutableStateOf<Int?>(null) }

    // Calculate SHA-256 hash of tasks list for version validation
    fun calculateTasksHash(tasks: List<Task>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        // Sort tasks by ID for consistent hashing
        val sortedTasks = tasks.sortedBy { it.id }
        val data = sortedTasks.joinToString("|") { task ->
            "${task.id}:${task.title}:${task.reminderTime}:${task.completed}:${task.active}"
        }
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    // Compare hashes and handle mismatch
    // Returns true if hash mismatch was detected and full refresh is needed
    suspend fun handleHashMismatch(serverHash: String, currentTasks: List<Task>): Boolean {
        val newLocalHash = calculateTasksHash(currentTasks)
        if (newLocalHash != serverHash) {
            android.util.Log.w("TasksScreen", "Hash mismatch detected. Local: ${newLocalHash.take(16)}..., Server: ${serverHash.take(16)}...")
            
            // Mark all tasks as changed for full update
            val changedIds = currentTasks.map { it.id }.toSet()
            markedChangedTaskIds = markedChangedTaskIds + changedIds
            
            // Return true to indicate full refresh is needed
            return true
        } else {
            // Hashes match, but don't clear marks - they should be cleared manually by user
            return false
        }
    }

    /**
     * Задача 1: Синхронизация локального кэша с сервером.
     * Проверяет синхронность, при различиях запрашивает данные с сервера, кладёт в кэш.
     * При внесении фактических изменений возвращает true для обновления UI.
     * 
     * @param tasksApi API для работы с задачами
     * @return Pair<Boolean, List<UserSummary>> где Boolean - были ли изменения в кэше, List - обновлённые пользователи
     */
    suspend fun syncCacheWithServer(tasksApi: com.homeplanner.api.TasksApi): Pair<Boolean, List<com.homeplanner.api.UserSummary>> {
        if (networkConfig == null || apiBaseUrl == null) {
            android.util.Log.d("TasksScreen", "syncCacheWithServer: No network config, skipping")
            return Pair(false, emptyList())
        }
        
        // Проверяем наличие интернета, но пытаемся синхронизироваться даже если статус неизвестен
        // Это гарантирует обновление локального хранилища при запуске приложения
        val hasInternet = syncService.isOnline()
        if (!hasInternet) {
            android.util.Log.d("TasksScreen", "syncCacheWithServer: No internet connection detected, but will try sync anyway")
        }
        
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("TasksScreen", "syncCacheWithServer: Starting sync from $apiBaseUrl")
                BinaryLogger.getInstance()?.log(com.homeplanner.debug.LogLevel.INFO, "MainActivity", com.homeplanner.debug.LogMessageCode.SYNC_START, mapOf("cache_size" to cachedTasksCount))

                // 1. Загружаем текущие данные из кэша для сравнения
                val cachedTasks = offlineRepository.loadTasksFromCache()
                val cachedHash = calculateTasksHash(cachedTasks)
                
                // 2. Запрашиваем данные с сервера
                val serverTasks = try {
                    tasksApi.getTasks(activeOnly = false)
                } catch (e: Exception) {
                    android.util.Log.w("TasksScreen", "syncCacheWithServer: Failed to fetch from server", e)
                    return@withContext Pair(false, emptyList())
                }
                
                val serverHash = calculateTasksHash(serverTasks)
                
                // 3. Загружаем группы и пользователей с сервера (всегда, даже если задачи совпадают)
                val syncedGroups = try {
                    GroupsApi(baseUrl = apiBaseUrl).getAll()
                } catch (e: Exception) {
                    android.util.Log.w("TasksScreen", "syncCacheWithServer: Failed to load groups", e)
                    emptyMap()
                }
                
                val syncedUsers = try {
                    UsersApi(baseUrl = apiBaseUrl).getUsers()
                } catch (e: Exception) {
                    android.util.Log.w("TasksScreen", "syncCacheWithServer: Failed to load users", e)
                    emptyList()
                }
                
                // 4. Сохраняем группы в кэш
                if (syncedGroups.isNotEmpty()) {
                    saveGroupsToCache(context, syncedGroups)
                }
                
                // 5. Проверяем синхронность задач
                // Если кэш пустой, а на сервере есть задачи - хеши будут разными, и задачи сохранятся автоматически
                // Если на сервере нет задач - хеши совпадут (оба пустые), и сохранять нечего
                if (cachedHash == serverHash) {
                    android.util.Log.d("TasksScreen", "syncCacheWithServer: Cache is in sync with server")
                    // Синхронизируем очередь операций даже если данные совпадают
                    try {
                        syncService.syncQueue()
                    } catch (e: Exception) {
                        android.util.Log.w("TasksScreen", "syncCacheWithServer: Queue sync failed", e)
                    }
                    // Возвращаем пользователей даже если задачи не изменились
                    return@withContext Pair(false, syncedUsers)
                }
                
                // 6. Есть различия - сохраняем данные с сервера в кэш
                android.util.Log.d("TasksScreen", "syncCacheWithServer: Cache differs from server, updating cache")
                offlineRepository.saveTasksToCache(serverTasks)
                BinaryLogger.getInstance()?.log(
                    com.homeplanner.debug.LogLevel.INFO,
                    "MainActivity",
                    com.homeplanner.debug.LogMessageCode.SYNC_CACHE_UPDATED,
                    mapOf<String, Any>("tasks_count" to serverTasks.size, "source" to "syncCacheWithServer")
                )
                
                // 7. Синхронизируем очередь операций
                try {
                    syncService.syncQueue()
                } catch (e: Exception) {
                    android.util.Log.w("TasksScreen", "syncCacheWithServer: Queue sync failed", e)
                }
                
                android.util.Log.d("TasksScreen", "syncCacheWithServer: Cache updated, ${serverTasks.size} tasks, ${syncedGroups.size} groups, ${syncedUsers.size} users")
                BinaryLogger.getInstance()?.log(com.homeplanner.debug.LogLevel.INFO, "MainActivity", com.homeplanner.debug.LogMessageCode.SYNC_SUCCESS, mapOf<String, Any>("server_tasks" to serverTasks.size, "groups" to syncedGroups.size, "users" to syncedUsers.size))
                return@withContext Pair(true, syncedUsers) // Были изменения
            } catch (e: Exception) {
                android.util.Log.e("TasksScreen", "syncCacheWithServer: Error during sync", e)
                BinaryLogger.getInstance()?.log(com.homeplanner.debug.LogLevel.ERROR, "MainActivity", com.homeplanner.debug.LogMessageCode.SYNC_FAIL_NETWORK, mapOf<String, Any>("error" to (e.message ?: "unknown")))
                return@withContext Pair(false, emptyList())
            }
        }
    }
    
    /**
     * Задача 2: Обновление UI из кэша.
     * Проверяет синхронность UI с кэшем, при наличии фактических расхождений обновляет UI.
     * 
     * @return true если UI был обновлён
     */
    suspend fun updateUIFromCache(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Загружаем данные из кэша
                val cachedTasks = offlineRepository.loadTasksFromCache()
                val uniqueCachedTasks = cachedTasks.distinctBy { it.id }
                
                // 2. Загружаем группы из кэша
                val cachedGroups = loadGroupsFromCache(context)
                
                // 3. Проверяем синхронность UI с кэшем
                val currentHash = calculateTasksHash(allTasks)
                val cachedHash = calculateTasksHash(uniqueCachedTasks)
                
                if (currentHash == cachedHash && groups == cachedGroups) {
                    android.util.Log.d("TasksScreen", "updateUIFromCache: UI is in sync with cache")
                    return@withContext false
                }
                
                // 4. Есть расхождения - обновляем UI, сохраняя более свежие данные
                withContext(Dispatchers.Main) {
                    // Объединяем данные из кэша с текущими allTasks
                    val mergedTasks = uniqueCachedTasks.map { cachedTask ->
                        val currentTask = allTasks.find { it.id == cachedTask.id }
                        if (currentTask != null) {
                            // Если есть оптимистичное обновление (completed/active отличается), сохраняем текущую
                            if (currentTask.completed != cachedTask.completed || 
                                currentTask.active != cachedTask.active) {
                                android.util.Log.d("TasksScreen", "updateUIFromCache: Keeping current task id=${currentTask.id} (optimistic update)")
                                currentTask
                            } else {
                                // Используем кэшированную задачу
                                cachedTask
                            }
                        } else {
                            // Новой задачи нет в текущих - добавляем
                            cachedTask
                        }
                    }
                    
                    // Добавляем задачи, которых нет в кэше (локальные изменения, которые ещё не сохранены)
                    val cachedTaskIds = mergedTasks.map { it.id }.toSet()
                    val additionalTasks = allTasks.filter { it.id !in cachedTaskIds }
                    allTasks = (mergedTasks + additionalTasks).distinctBy { it.id }
                    
                    // Обновляем группы только если они изменились
                    if (cachedGroups.isNotEmpty() && groups != cachedGroups) {
                        groups = cachedGroups
                    }
                    
                    android.util.Log.d("TasksScreen", "updateUIFromCache: UI updated, ${allTasks.size} tasks, ${groups.size} groups")
                }
                
                return@withContext true // UI был обновлён
            } catch (e: Exception) {
                android.util.Log.e("TasksScreen", "updateUIFromCache: Error updating UI from cache", e)
                return@withContext false
            }
        }
    }
    
    suspend fun loadTasks() {
        // Offline-first: ВСЕГДА сначала загружаем из локального кэша для мгновенного отображения
        android.util.Log.d("TasksScreen", "loadTasks: Starting offline-first load from cache")
        isLoading = true
        error = null
        
        try {
            val dayStartHour = 4
            
            // 1. Обновляем рекуррентные задачи по новому дню
            withContext(Dispatchers.IO) {
                try {
                    val isNewDay = offlineRepository.updateRecurringTasksForNewDay(dayStartHour)
                    if (isNewDay && networkConfig != null && apiBaseUrl != null && syncService.isOnline()) {
                        // После локального пересчёта выполняем полную синхронизацию состояния с сервером
                        syncService.syncStateBeforeRecalculation()
                    } else {
                        // Новый день не наступил или нет интернета
                    }
                } catch (e: Exception) {
                    Log.e("TasksScreen", "loadTasks: error during local new-day recalculation", e)
                }
            }
            
            // 2. Обновляем UI из кэша (мгновенное отображение)
            val uiUpdated = updateUIFromCache()
            android.util.Log.d("TasksScreen", "loadTasks: UI updated from cache: $uiUpdated")
            
            // 3. Синхронизация с сервером в фоне (не блокирует UI)
            // При запуске приложения всегда пытаемся синхронизироваться с сервером, если есть networkConfig
            // Это гарантирует обновление локального хранилища при старте, даже если кэш пустой
            if (networkConfig != null && apiBaseUrl != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        // Проверяем наличие интернета, но пытаемся синхронизироваться даже если статус неизвестен
                        val hasInternet = syncService.isOnline()
                        if (!hasInternet) {
                            android.util.Log.d("TasksScreen", "loadTasks: No internet connection, but will try sync anyway")
                        }
                        
                        val groupsApi = GroupsApi(baseUrl = apiBaseUrl)
                        val usersApi = UsersApi(baseUrl = apiBaseUrl)
                        android.util.Log.d("TasksScreen", "loadTasks: Starting syncCacheWithServer")
                        val syncResult = syncService.syncCacheWithServer(groupsApi, usersApi)
                        
                        android.util.Log.d("TasksScreen", "loadTasks: syncCacheWithServer completed, success=${syncResult.isSuccess}")
                        if (syncResult.isSuccess) {
                            val result = syncResult.getOrNull()
                            val cacheUpdated = result?.cacheUpdated ?: false
                            val syncedUsers = result?.users ?: emptyList()
                            
                            withContext(Dispatchers.Main) {
                                // Обновляем пользователей всегда (даже если задачи не изменились)
                                if (syncedUsers.isNotEmpty()) {
                                    users = syncedUsers
                                }
                                if (result?.groups != null) {
                                    saveGroupsToCache(context, result.groups)
                                }
                            }
                            
                            if (cacheUpdated) {
                                // Кэш был обновлён — обновляем UI только если изменения влияют на текущий вид
                                updateUIFromCache()
                                
                                // Обновляем счетчик задач в хранилище
                                cachedTasksCount = offlineRepository.getCachedTasksCount()
                            }
                            
                            // Отмечаем успешный запрос для обновления статуса соединения
                            markSuccessfulRequest()
                        } else {
                            android.util.Log.w("TasksScreen", "loadTasks: Sync failed, marking as failed request")
                            markFailedRequest()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("TasksScreen", "loadTasks: Background sync failed", e)
                        markFailedRequest()
                    }
                }
            } else {
                android.util.Log.d("TasksScreen", "loadTasks: No network config, skipping background sync")
                users = emptyList()
            }

            // 4. Перепланировка напоминаний по локальным данным
            try {
                reminderScheduler.cancelAll(allTasks)
                reminderScheduler.scheduleForTasks(allTasks)
            } catch (e: Exception) {
                Log.e("TasksScreen", "loadTasks: Scheduling error", e)
            }
            
            // 5. Calculate and store local hash after loading
            localDataHash = calculateTasksHash(allTasks)
            android.util.Log.d("TasksScreen", "loadTasks: Calculated local hash: ${localDataHash?.take(16)}...")
            
        } catch (e: Exception) {
            android.util.Log.e("TasksScreen", "loadTasks: Error loading tasks from cache", e)
            error = e.message ?: "Unknown error"
            // Не вызываем markFailedRequest() - это не реальный запрос к серверу, только локальная загрузка из кэша
        } finally {
            isLoading = false
            android.util.Log.d("TasksScreen", "loadTasks: Completed, isLoading=false")
        }
    }

    LaunchedEffect(networkConfig) {
        val config = networkConfig
        if (config != null) {
            android.util.Log.d("TasksScreen", "Network config changed, loading tasks from ${config.toApiBaseUrl()}")
            loadTasks()
        } else {
            android.util.Log.d("TasksScreen", "Network config is null, loading tasks from offline cache")
            loadTasks() // Загружаем задачи из кэша даже в оффлайн режиме
        }
    }

    // Helper to parse Task from JSON
    fun parseTaskFromJson(json: JSONObject): Task {
        val reminderValue = json.optString("reminder_time", null)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Отсутствует reminder_time в ответе сервера: $json")
        val activeValue = if (json.isNull("active")) true else json.getBoolean("active")
        val completedValue = if (json.isNull("completed")) false else json.getBoolean("completed")
        
        // Extract assigned_user_ids from JSON array
        val assignedUserIds = mutableListOf<Int>()
        if (json.has("assigned_user_ids") && !json.isNull("assigned_user_ids")) {
            val idsArray = json.getJSONArray("assigned_user_ids")
            for (i in 0 until idsArray.length()) {
                assignedUserIds.add(idsArray.getInt(i))
            }
        }
        
        return Task(
            id = json.getInt("id"),
            title = json.getString("title"),
            description = json.optString("description", null),
            taskType = json.getString("task_type"),
            recurrenceType = json.optString("recurrence_type", null),
            recurrenceInterval = if (json.isNull("recurrence_interval")) null else json.getInt("recurrence_interval"),
            intervalDays = if (json.isNull("interval_days")) null else json.getInt("interval_days"),
            reminderTime = reminderValue,
            groupId = if (json.isNull("group_id")) null else json.getInt("group_id"),
            active = activeValue,
            completed = completedValue,
            assignedUserIds = assignedUserIds,
        )
    }

    // Update tasks list based on WebSocket event
    fun updateTasksFromEvent(action: String, taskJson: JSONObject?, taskId: Int?) {
        scope.launch {
            try {
                android.util.Log.d("TasksScreen", "updateTasksFromEvent: action=$action, selectedTab=$selectedTab")
                
                // For today view, reload from backend to ensure correct filtering
                // (backend filters tasks based on unified logic)
                // But also update allTasks to keep it in sync
                if (selectedTab == ViewTab.TODAY) {
                    // Reload tasks from backend to ensure correct filtering
                    android.util.Log.d("TasksScreen", "Today tab: reloading from backend")
                    refreshKey += 1
                    // Note: loadTasks() will update both allTasks and tasks
                    // Also reschedule reminders after reload
                    try {
                        reminderScheduler.cancelAll(allTasks)
                        reminderScheduler.scheduleForTasks(allTasks)
                    } catch (e: Exception) {
                        Log.e("TasksScreen", "Reschedule error after WebSocket update", e)
                    }
                    return@launch
                }
                
                // For other views, update locally
                // Update allTasks (getVisibleTasks() will be recalculated on next recomposition)
                val currentAllTasksList = allTasks.toMutableList()
                var needsReschedule = false
                when (action) {
                    "created", "updated" -> {
                        if (taskJson != null) {
                            val updatedTask = parseTaskFromJson(taskJson)
                            android.util.Log.d("TasksScreen", "Updating task locally: id=${updatedTask.id}, title=${updatedTask.title}")
                            
                            // Update in allTasks
                            val allIndex = currentAllTasksList.indexOfFirst { it.id == updatedTask.id }
                            if (allIndex >= 0) {
                                currentAllTasksList[allIndex] = updatedTask
                                android.util.Log.d("TasksScreen", "Task replaced at index $allIndex")
                            } else {
                                currentAllTasksList.add(updatedTask)
                                android.util.Log.d("TasksScreen", "Task added to list")
                            }
                            
                            allTasks = currentAllTasksList.toList()
                            needsReschedule = true
                        } else {
                            android.util.Log.w("TasksScreen", "No taskJson for action=$action")
                        }
                    }
                    "completed", "uncompleted", "shown" -> {
                        if (taskJson != null) {
                            val updatedTask = parseTaskFromJson(taskJson)
                            
                            // Update in allTasks только если задача существует
                            // Проверяем, не была ли задача только что обновлена локально (чтобы избежать моргания)
                            val allIndex = currentAllTasksList.indexOfFirst { it.id == updatedTask.id }
                            if (allIndex >= 0) {
                                val currentTask = currentAllTasksList[allIndex]
                                // Обновляем только если данные действительно изменились
                                // Это предотвращает моргание при дублирующих обновлениях
                                if (updatedTask.completed != currentTask.completed ||
                                    updatedTask.active != currentTask.active ||
                                    updatedTask.reminderTime != currentTask.reminderTime ||
                                    updatedTask.title != currentTask.title) {
                                    currentAllTasksList[allIndex] = updatedTask
                                    android.util.Log.d("TasksScreen", "WebSocket: Updated task id=${updatedTask.id}, completed=${updatedTask.completed}")
                                } else {
                                    android.util.Log.d("TasksScreen", "WebSocket: Skipping update for task id=${updatedTask.id} (no changes)")
                                }
                            } else {
                                currentAllTasksList.add(updatedTask)
                            }
                            
                            allTasks = currentAllTasksList.toList()
                        }
                    }
                    "deleted" -> {
                        taskId?.let { id ->
                            val removedFromAll = currentAllTasksList.removeAll { it.id == id }
                            android.util.Log.d("TasksScreen", "Task deleted: id=$id, removed from all=$removedFromAll")
                            allTasks = currentAllTasksList.toList()
                            needsReschedule = true
                            
                            // Remove from marked list if it was there (task no longer exists)
                            markedChangedTaskIds = markedChangedTaskIds - id
                        }
                    }
                    "hash_mismatch" -> {
                        // Server detected hash mismatch, trigger full refresh
                        android.util.Log.w("TasksScreen", "Server reported hash mismatch, doing full refresh")
                        refreshKey += 1
                        return@launch
                    }
                    else -> {
                        // Unknown action, do full refresh
                        android.util.Log.w("TasksScreen", "Unknown action: $action, doing full refresh")
                        refreshKey += 1
                        return@launch
                    }
                }
                
                // Reschedule reminders if task was created, updated, or deleted
                if (needsReschedule) {
                    try {
                        reminderScheduler.cancelAll(allTasks)
                        reminderScheduler.scheduleForTasks(allTasks)
                    } catch (e: Exception) {
                        Log.e("TasksScreen", "Reschedule error after WebSocket update", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TasksScreen", "updateTasksFromEvent error", e)
                e.printStackTrace()
            }
        }
    }

    val webSocketUrl = remember(networkConfig) { 
        val url = resolveWebSocketUrl(networkConfig)
        android.util.Log.d("TasksScreen", "WebSocket URL resolved: $url")
        url
    }

    // WebSocket auto-refresh with reconnection
    LaunchedEffect(networkConfig) {
        // Don't connect if network config is not set
        if (networkConfig == null || webSocketUrl == null) {
            android.util.Log.d("TasksScreen", "WebSocket: config or URL is null, not connecting")
            wsConnected = false
            wsConnecting = false
            return@LaunchedEffect
        }
        
        android.util.Log.d("TasksScreen", "WebSocket: Starting connection to $webSocketUrl")
        val client = OkHttpClient()
        var currentWs: WebSocket? = null
        var reconnectDelay = 2000L // Start with 2 seconds
        var shouldReconnect = true
        
        suspend fun connectWebSocket() {
            if (!shouldReconnect) {
                android.util.Log.d("TasksScreen", "WebSocket: Reconnection disabled")
                return
            }
            
            try {
                android.util.Log.d("TasksScreen", "WebSocket: Attempting connection to $webSocketUrl")
                wsConnecting = true
                val request = Request.Builder()
                    .url(webSocketUrl!!)
                    .build()
                val listener = object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        // Log ALL incoming messages first
                        android.util.Log.d("TasksScreen", "WS raw message: $text")
                        
                        try {
                            val msg = JSONObject(text)
                            val msgType = msg.optString("type", "")
                            
                            if (msgType == "task_update") {
                                val action = msg.getString("action")
                                val taskId = if (msg.has("task_id")) msg.getInt("task_id") else null
                                val taskJson = if (msg.has("task")) msg.getJSONObject("task") else null
                                android.util.Log.d("TasksScreen", "WS message: action=$action, taskId=$taskId")
                                updateTasksFromEvent(action, taskJson, taskId)
                                
                                // After updating tasks, check hash if provided
                                if (msg.has("data_hash")) {
                                    val serverHash = msg.getString("data_hash")
                                    scope.launch {
                                        // Wait a bit for tasks to update
                                        delay(100)
                                        val needsRefresh = handleHashMismatch(serverHash, allTasks)
                                        if (needsRefresh) {
                                            loadTasks()
                                        }
                                    }
                                }
                            } else if (msgType == "hash_check" && msg.has("data_hash")) {
                                // Standalone hash check message
                                val serverHash = msg.getString("data_hash")
                                scope.launch {
                                    val needsRefresh = handleHashMismatch(serverHash, allTasks)
                                    if (needsRefresh) {
                                        loadTasks()
                                    }
                                }
                            } else {
                                android.util.Log.d("TasksScreen", "WS message type ignored: $msgType")
                            }
                        } catch (e: Exception) {
                            // If parsing fails, do full refresh
                            android.util.Log.e("TasksScreen", "WS parse error", e)
                            scope.launch { refreshKey += 1 }
                        }
                    }
                    
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        android.util.Log.d("TasksScreen", "WS connection opened")
                        scope.launch {
                            wsConnected = true
                            wsConnecting = false
                            markSuccessfulRequest()
                        }
                        reconnectDelay = 2000L // Reset delay on successful connection
                    }
                    
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                        android.util.Log.e("TasksScreen", "WS connection failure: ${t.message}", t)
                        scope.launch {
                            wsConnected = false
                            wsConnecting = false
                            markFailedRequest()
                            // Retry after delay with exponential backoff (max 30 seconds)
                            reconnectDelay = minOf(reconnectDelay * 2, 30000L)
                            android.util.Log.d("TasksScreen", "WS: Will retry in ${reconnectDelay}ms")
                            kotlinx.coroutines.delay(reconnectDelay)
                            if (shouldReconnect) {
                                wsConnecting = true
                                connectWebSocket()
                            }
                        }
                    }
                    
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        android.util.Log.d("TasksScreen", "WS connection closed: code=$code, reason=$reason")
                        scope.launch {
                            wsConnected = false
                            wsConnecting = false
                            // Если закрытие не нормальное (не код 1000) - считаем неуспешной попыткой
                            if (code != 1000) {
                                markFailedRequest()
                            }
                            // Reconnect after delay (only if not a normal closure)
                            if (code != 1000 && shouldReconnect) {
                                reconnectDelay = minOf(reconnectDelay * 2, 30000L)
                                android.util.Log.d("TasksScreen", "WS: Will retry in ${reconnectDelay}ms")
                                kotlinx.coroutines.delay(reconnectDelay)
                                if (shouldReconnect) {
                                    wsConnecting = true
                                    connectWebSocket()
                                }
                            }
                        }
                    }
                }
                currentWs = client.newWebSocket(request, listener)
                android.util.Log.d("TasksScreen", "WebSocket: Connection request sent")
            } catch (e: Exception) {
                android.util.Log.e("TasksScreen", "Failed to create WebSocket", e)
                scope.launch {
                    wsConnected = false
                    wsConnecting = false
                    markFailedRequest()
                    // Retry after delay with exponential backoff (max 30 seconds)
                    reconnectDelay = minOf(reconnectDelay * 2, 30000L)
                    kotlinx.coroutines.delay(reconnectDelay)
                    if (shouldReconnect) {
                        connectWebSocket()
                    }
                }
            }
        }
        
        // Initial connection
        connectWebSocket()
        
        // Keep alive until disposed
        try {
            awaitCancellation()
        } finally {
            shouldReconnect = false
            currentWs?.close(1000, "LaunchedEffect cancelled")
            android.util.Log.d("TasksScreen", "WebSocket: LaunchedEffect cancelled, closing connection")
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == ViewTab.TODAY,
                    onClick = { 
                        selectedTab = ViewTab.TODAY
                        uiRefreshKey += 1
                    },
                    icon = { Icon(Icons.Outlined.Today, contentDescription = "Сегодня") },
                    label = { Text("Сегодня") }
                )
                NavigationBarItem(
                    selected = selectedTab == ViewTab.ALL,
                    onClick = { 
                        selectedTab = ViewTab.ALL
                        uiRefreshKey += 1
                    },
                    icon = { Icon(Icons.Outlined.List, contentDescription = "Все задачи") },
                    label = { Text("Все задачи") }
                )
                NavigationBarItem(
                    selected = selectedTab == ViewTab.SETTINGS,
                    onClick = { selectedTab = ViewTab.SETTINGS },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "Настройки") },
                    label = { Text("Настройки") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == ViewTab.ALL) {
                FloatingActionButton(onClick = {
                    editingTask = null
                    editTitle = ""
                    editDescription = ""
                    editTaskType = "one_time"
                    editRecurrenceType = "daily"  // Default value, will be hidden for one_time
                    editRecurrenceInterval = ""
                    editIntervalDays = ""
                    editReminderTime = formatIso(LocalDateTime.now())
                    editGroupId = null
                    // Set current user as default
                    editAssignedUserIds = selectedUser?.id?.let { listOf(it) } ?: emptyList()
                    titleError = null
                    reminderTimeError = null
                    recurrenceError = null
                    showEditDialog = true
                }) {
                    Text("+")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val currentTitle = when (selectedTab) {
                ViewTab.TODAY -> "Сегодня"
                ViewTab.ALL -> "Все задачи"
                ViewTab.SETTINGS -> "Настройки"
            }
            AppStatusBar(
                appIcon = Icons.Filled.Home,
                screenTitle = currentTitle,
                isOnline = isOnline,
                connectionStatus = connectionStatus,
                isSyncing = isSyncing,
                storagePercentage = storagePercentage,
                pendingOperations = pendingOperations,
                wsConnected = wsConnected,
                wsConnecting = wsConnecting,
                compactMode = statusBarCompactMode,
                onNetworkClick = {
                    // Явное действие пользователя для синхронизации - это нормально
                    scope.launch {
                        isSyncing = true
                        try {
                            syncService.syncQueue()
                            // После синхронизации очереди обновляем UI из кэша
                            uiRefreshKey += 1
                        } finally {
                            isSyncing = false
                        }
                    }
                },
                onStorageClick = { selectedTab = ViewTab.SETTINGS },
                onSyncClick = {
                    // Явное действие пользователя для синхронизации - это нормально
                    scope.launch {
                        isSyncing = true
                        try {
                            syncService.syncQueue()
                            // После синхронизации очереди обновляем UI из кэша
                            uiRefreshKey += 1
                        } finally {
                            isSyncing = false
                        }
                    }
                },
                onWebSocketClick = { /* WebSocket status indicator only */ }
            )
            
            Text(
                text = selectedUser?.let { "Пользователь: ${it.name} (#${it.id})" } ?: "Пользователь не выбран",
                style = MaterialTheme.typography.bodySmall,
                color = if (selectedUser == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            LaunchedEffect(refreshKey) {
                if (refreshKey > 0) {
                    loadTasks()
                }
            }
            
            // Обновление UI из кэша при переключении вкладок (без синхронизации с сервером)
            LaunchedEffect(uiRefreshKey) {
                if (uiRefreshKey > 0) {
                    android.util.Log.d("TasksScreen", "Tab switched, updating UI from cache only")
                    scope.launch {
                        updateUIFromCache()
                    }
                }
            }

            // Filter tasks based on current tab (computed directly when needed)
            val isTodayTab = selectedTab == ViewTab.TODAY
            val isTodayWithoutUser = isTodayTab && selectedUser == null
            
            // Helper function to get visible tasks for current tab with stable sorting
            // Мемоизируем отсортированный список, чтобы избежать лишних перерисовок
            val visibleTasks = remember(selectedTab, allTasks, selectedUser) {
                when (selectedTab) {
                    ViewTab.TODAY -> {
                        android.util.Log.d("TasksScreen", "getVisibleTasks: TODAY tab, allTasks.size=${allTasks.size}")
                        if (selectedUser == null) {
                            android.util.Log.w("TasksScreen", "getVisibleTasks: selected user is null, Today view will be empty")
                            emptyList()
                        } else {
                            // Use local filtering for Today view (works in both online and offline modes)
                            val dayStartHour = 4
                            val filtered = TodayTaskFilter.filterTodayTasks(
                                tasks = allTasks,
                                selectedUser = selectedUser,
                                dayStartHour = dayStartHour
                            )
                            // Сортировка по хронологическому порядку (reminder_time), затем по ID для стабильности
                            val sorted = filtered.sortedWith(compareBy(
                                { task ->
                                    try {
                                        java.time.LocalDateTime.parse(task.reminderTime)
                                    } catch (e: Exception) {
                                        java.time.LocalDateTime.MIN
                                    }
                                },
                                { it.id } // Вторичная сортировка по ID для стабильности
                            ))
                            android.util.Log.d("TasksScreen", "getVisibleTasks: Filtered ${allTasks.size} tasks to ${sorted.size} for Today view (chronological order)")
                            sorted
                        }
                    }
                    ViewTab.ALL -> {
                        // Сортировка по алфавитному порядку (title), затем по ID для стабильности
                        val sorted = allTasks.sortedWith(compareBy(
                            { it.title.lowercase() },
                            { it.id } // Вторичная сортировка по ID для стабильности
                        ))
                        android.util.Log.d("TasksScreen", "getVisibleTasks: ALL tab, showing ${sorted.size} tasks (alphabetical order)")
                        sorted
                    }
                    ViewTab.SETTINGS -> emptyList()
                }
            }

            when (selectedTab) {
                ViewTab.SETTINGS -> {
                    SettingsScreen(
                        networkSettings = networkSettings,
                        userSettings = userSettings,
                        networkConfig = networkConfig,
                        selectedUser = selectedUser,
                        apiBaseUrl = apiBaseUrl,
                        onConfigChanged = { 
                            // При изменении networkConfig запускаем полную синхронизацию с сервером
                            // Это единственное место, где UI функция может вызвать синхронизацию
                            scope.launch {
                                android.util.Log.d("TasksScreen", "Network config changed, triggering immediate sync")
                                loadTasks()
                            }
                        },
                        onUserChanged = { 
                            // При изменении пользователя обновляем только UI из кэша, без синхронизации с сервером
                            uiRefreshKey += 1
                        },
                        statusBarCompactMode = statusBarCompactMode,
                        onStatusBarCompactModeChanged = { enabled ->
                            statusBarCompactMode = enabled
                            setStatusBarCompactMode(context, enabled)
                        },
                        storagePercentage = storagePercentage,
                        pendingOperations = pendingOperations,
                        cachedTasksCount = cachedTasksCount
                    )
                }

                ViewTab.TODAY, ViewTab.ALL -> {
                    // Offline-first: задачи всегда загружаются из кэша, даже без networkConfig
                    if (isTodayWithoutUser) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "⚠️ Выберите пользователя",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Для просмотра задач на сегодня необходимо выбрать пользователя в настройках.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { selectedTab = ViewTab.SETTINGS }) {
                                Text("Открыть настройки")
                            }
                        }
                    } else {
                        if (visibleTasks.isEmpty()) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(text = "No tasks")
                            }
                        } else {
                            val activeList = visibleTasks.filter { it.active }
                            val inactiveList = visibleTasks.filter { !it.active }

                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(activeList, key = { it.id }) { task ->
                                val dismissState = rememberDismissState(
                                    confirmStateChange = { newValue ->
                                        when (newValue) {
                                            DismissValue.DismissedToStart -> {
                                                pendingDeleteTaskId = task.id
                                                showDeleteConfirm = true
                                                false
                                            }

                                            DismissValue.DismissedToEnd -> {
                                                editingTask = task
                                                editTitle = task.title
                                                editDescription = task.description?.takeIf { it != "null" } ?: ""
                                                editTaskType = task.taskType
                                                // Set default to "daily" if recurring and recurrenceType is null
                                                editRecurrenceType = if (task.taskType == "recurring") {
                                                    task.recurrenceType ?: "daily"
                                                } else {
                                                    "daily"  // Default value, will be hidden for non-recurring
                                                }
                                                editRecurrenceInterval = task.recurrenceInterval?.toString() ?: ""
                                                editIntervalDays = task.intervalDays?.toString() ?: ""
                                                editReminderTime = task.reminderTime
                                                editGroupId = task.groupId
                                                editAssignedUserIds = task.assignedUserIds.ifEmpty { 
                                                    // Set current user as default if no users assigned
                                                    selectedUser?.id?.let { listOf(it) } ?: emptyList()
                                                }
                                                titleError = null
                                                reminderTimeError = null
                                                recurrenceError = null
                                                showEditDialog = true
                                                false
                                            }

                                            else -> true
                                        }
                                    }
                                )
                                SwipeToDismiss(
                                    state = dismissState,
                                    directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
                                    background = {},
                                    dismissContent = {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            // Форматирование времени/даты: для вкладки "Сегодня" - дата для просроченных, время для сегодняшних
                                            // Для вкладки "Все задачи" - всегда время
                                            fun formatTimeOrDate(dt: String?): String {
                                                if (dt.isNullOrBlank()) return "--:--"
                                                return try {
                                                    val ldt = LocalDateTime.parse(dt)
                                                    
                                                    // Для вкладки "Сегодня" используем специальную логику
                                                    if (selectedTab == ViewTab.TODAY) {
                                                        val now = LocalDateTime.now()
                                                        val dayStartHour = 4
                                                        
                                                        // Определяем начало сегодняшнего дня
                                                        val todayStart = if (now.hour >= dayStartHour) {
                                                            now.withHour(dayStartHour).withMinute(0).withSecond(0).withNano(0)
                                                        } else {
                                                            now.minusDays(1).withHour(dayStartHour).withMinute(0).withSecond(0).withNano(0)
                                                        }
                                                        
                                                        // Определяем начало дня задачи
                                                        val taskDayStart = if (ldt.hour >= dayStartHour) {
                                                            ldt.withHour(dayStartHour).withMinute(0).withSecond(0).withNano(0)
                                                        } else {
                                                            ldt.minusDays(1).withHour(dayStartHour).withMinute(0).withSecond(0).withNano(0)
                                                        }
                                                        
                                                        // Если задача просрочена (в прошлом), показываем дату
                                                        if (taskDayStart.isBefore(todayStart)) {
                                                            // Формат даты: "15.01"
                                                            val dayOfMonth = ldt.dayOfMonth
                                                            val month = ldt.month.value
                                                            "$dayOfMonth.${String.format("%02d", month)}"
                                                        } else {
                                                            // Если задача сегодняшняя, показываем время
                                                            ldt.format(DateTimeFormatter.ofPattern("HH:mm"))
                                                        }
                                                    } else {
                                                        // Для вкладки "Все задачи" всегда показываем время
                                                        ldt.format(DateTimeFormatter.ofPattern("HH:mm"))
                                                    }
                                                } catch (_: DateTimeParseException) {
                                                    val timePart = dt.substringAfter('T', dt)
                                                    if (timePart.length >= 5) timePart.substring(0, 5) else "--:--"
                                                }
                                            }
                                            // Показываем время/дату только во вкладке "Сегодня"
                                            if (selectedTab == ViewTab.TODAY) {
                                                val timeText = formatTimeOrDate(task.reminderTime)
                                                Text(timeText)
                                                Spacer(modifier = Modifier.width(12.dp))
                                            }

                                            // Формат названия: "%group% %task%"
                                            val groupName = task.groupId?.let { groups[it] } ?: ""
                                            val titleWithGroup = if (groupName.isNotBlank()) {
                                                "$groupName ${task.title}"
                                            } else {
                                                task.title
                                            }
                                            Text(
                                                text = titleWithGroup,
                                                modifier = Modifier.weight(1f),
                                            )
                                            val isMarkedChanged = task.id in markedChangedTaskIds
                                            if (isMarkedChanged) {
                                                Text(
                                                    text = "●",
                                                    color = ComposeColor(0xFF2196F3),
                                                    modifier = Modifier
                                                        .padding(start = 4.dp)
                                                        .clickable {
                                                            // Confirm and remove mark
                                                            markedChangedTaskIds = markedChangedTaskIds - task.id
                                                        },
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            }

                                            TextButton(
                                                onClick = { testNotificationForTask(task) },
                                                modifier = Modifier.padding(horizontal = 4.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Notifications,
                                                    contentDescription = "Тест уведомления",
                                                    modifier = Modifier
                                                        .width(20.dp)
                                                        .height(20.dp),
                                                )
                                            }

                                            val checked = task.completed
                                            Checkbox(checked = checked, onCheckedChange = { isChecked ->
                                                Log.d(
                                                    "TasksScreen",
                                                    "Checkbox clicked: task.id=${task.id}, currentChecked=$checked, newChecked=$isChecked",
                                                )
                                                if (isChecked && !checked) {
                                                    scope.launch {
                                                        // Offline-first: LocalApi работает даже без apiBaseUrl (обновляет локальный кэш)
                                                        try {
                                                            val updated = withContext(Dispatchers.IO) {
                                                                localApi.completeTask(task.id)
                                                            }
                                                            // Явная синхронизация в фоне
                                                            if (syncService.isOnline()) {
                                                                scope.launch(Dispatchers.IO) {
                                                                    syncService.syncQueue()
                                                                    markSuccessfulRequest()
                                                                }
                                                            }
                                                            Log.d("TasksScreen", "Task completed: id=${updated.id}, completed=${updated.completed}")
                                                            BinaryLogger.getInstance()?.log(com.homeplanner.debug.LogLevel.INFO, "MainActivity", com.homeplanner.debug.LogMessageCode.TASK_COMPLETE, mapOf<String, Any>("task_id" to updated.id, "title" to updated.title))
                                                            // Обновляем allTasks только если задача действительно изменилась (предотвращаем моргание)
                                                            val currentTask = allTasks.find { it.id == updated.id }
                                                            if (currentTask == null || currentTask.completed != updated.completed) {
                                                                val newAllTasks = allTasks.map { if (it.id == updated.id) updated else it }
                                                                allTasks = newAllTasks
                                                                Log.d("TasksScreen", "allTasks updated, new size=${newAllTasks.size}")
                                                            } else {
                                                                Log.d("TasksScreen", "Task already updated, skipping allTasks update to prevent flickering")
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("TasksScreen", "Error completing task", e)
                                                            // Не вызываем markFailedRequest() - это оптимистичное обновление
                                                            // Реальный статус сети обновится при следующей синхронизации
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                } else if (!isChecked && checked) {
                                                    scope.launch {
                                                        // Offline-first: LocalApi работает даже без apiBaseUrl (обновляет локальный кэш)
                                                        try {
                                                            val updated = withContext(Dispatchers.IO) {
                                                                localApi.uncompleteTask(task.id)
                                                            }
                                                            // Явная синхронизация в фоне
                                                            if (syncService.isOnline()) {
                                                                scope.launch(Dispatchers.IO) {
                                                                    syncService.syncQueue()
                                                                    markSuccessfulRequest()
                                                                }
                                                            }
                                                            Log.d("TasksScreen", "Task uncompleted: id=${updated.id}, completed=${updated.completed}")
                                                            // Обновляем allTasks только если задача действительно изменилась (предотвращаем моргание)
                                                            val currentTask = allTasks.find { it.id == updated.id }
                                                            if (currentTask == null || currentTask.completed != updated.completed) {
                                                                val newAllTasks = allTasks.map { if (it.id == updated.id) updated else it }
                                                                allTasks = newAllTasks
                                                                Log.d("TasksScreen", "allTasks updated, new size=${newAllTasks.size}")
                                                            } else {
                                                                Log.d("TasksScreen", "Task already updated, skipping allTasks update to prevent flickering")
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("TasksScreen", "Error uncompleting task", e)
                                                            // Не вызываем markFailedRequest() - это оптимистичное обновление
                                                            // Реальный статус сети обновится при следующей синхронизации
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                }
                                            })
                                        }
                                    }
                                )
                            }

                            if (inactiveList.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Неактивные",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ComposeColor(0xFF607D8B),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                items(inactiveList, key = { it.id }) { task ->
                                    val dismissState = rememberDismissState(
                                        confirmStateChange = { newValue ->
                                            when (newValue) {
                                                DismissValue.DismissedToStart -> {
                                                    pendingDeleteTaskId = task.id
                                                    showDeleteConfirm = true
                                                    false
                                                }

                                                DismissValue.DismissedToEnd -> {
                                                    editingTask = task
                                                    editTitle = task.title
                                                    editDescription = task.description?.takeIf { it != "null" } ?: ""
                                                    editTaskType = task.taskType
                                                    // Set default to "daily" if recurring and recurrenceType is null
                                                    editRecurrenceType = if (task.taskType == "recurring") {
                                                        task.recurrenceType ?: "daily"
                                                    } else {
                                                        "daily"  // Default value, will be hidden for non-recurring
                                                    }
                                                    editRecurrenceInterval = task.recurrenceInterval?.toString() ?: ""
                                                    editIntervalDays = task.intervalDays?.toString() ?: ""
                                                    editReminderTime = task.reminderTime
                                                    editGroupId = task.groupId
                                                    editAssignedUserIds = task.assignedUserIds.ifEmpty { 
                                                        // Set current user as default if no users assigned
                                                        selectedUser?.id?.let { listOf(it) } ?: emptyList()
                                                    }
                                                    titleError = null
                                                    reminderTimeError = null
                                                    recurrenceError = null
                                                    showEditDialog = true
                                                    false
                                                }

                                                else -> true
                                            }
                                        }
                                    )

                                    SwipeToDismiss(
                                        state = dismissState,
                                        directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
                                        background = {},
                                        dismissContent = {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                // Форматирование времени/даты: для вкладки "Сегодня" - дата для просроченных, время для сегодняшних
                                                // Для вкладки "Все задачи" - всегда время
                                                fun formatTimeOrDate(dt: String?): String {
                                                    if (dt.isNullOrBlank()) return "--:--"
                                                    return try {
                                                        val ldt = LocalDateTime.parse(dt)
                                                        
                                                        // Для вкладки "Сегодня" используем специальную логику
                                                        if (selectedTab == ViewTab.TODAY) {
                                                            val now = LocalDateTime.now()
                                                            val dayStartHour = 4
                                                            
                                                            // Определяем начало сегодняшнего дня
                                                            val todayStart = if (now.hour >= dayStartHour) {
                                                                now.withHour(dayStartHour).withMinute(0).withSecond(0).withNano(0)
                                                            } else {
                                                                now.minusDays(1).withHour(dayStartHour).withMinute(0).withSecond(0).withNano(0)
                                                            }
                                                            
                                                            // Определяем начало дня задачи
                                                            val taskDayStart = if (ldt.hour >= dayStartHour) {
                                                                ldt.withHour(dayStartHour).withMinute(0).withSecond(0).withNano(0)
                                                            } else {
                                                                ldt.minusDays(1).withHour(dayStartHour).withMinute(0).withSecond(0).withNano(0)
                                                            }
                                                            
                                                            // Если задача просрочена (в прошлом), показываем дату
                                                            if (taskDayStart.isBefore(todayStart)) {
                                                                // Формат даты: "15.01"
                                                                val dayOfMonth = ldt.dayOfMonth
                                                                val month = ldt.month.value
                                                                "$dayOfMonth.${String.format("%02d", month)}"
                                                            } else {
                                                                // Если задача сегодняшняя, показываем время
                                                                ldt.format(DateTimeFormatter.ofPattern("HH:mm"))
                                                            }
                                                        } else {
                                                            // Для вкладки "Все задачи" всегда показываем время
                                                            ldt.format(DateTimeFormatter.ofPattern("HH:mm"))
                                                        }
                                                    } catch (_: DateTimeParseException) {
                                                        val timePart = dt.substringAfter('T', dt)
                                                        if (timePart.length >= 5) timePart.substring(0, 5) else "--:--"
                                                    }
                                                }
                                                // Показываем время/дату только во вкладке "Сегодня"
                                                if (selectedTab == ViewTab.TODAY) {
                                                    val timeText = formatTimeOrDate(task.reminderTime)
                                                    Text(timeText)
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                }

                                                // Формат названия: "%group% %task%"
                                                val groupName = task.groupId?.let { groups[it] } ?: ""
                                                val titleWithGroup = if (groupName.isNotBlank()) {
                                                    "$groupName ${task.title}"
                                                } else {
                                                    task.title
                                                }
                                                Text(
                                                    text = titleWithGroup,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                val isMarkedChanged = task.id in markedChangedTaskIds
                                                if (isMarkedChanged) {
                                                    Text(
                                                        text = "●",
                                                        color = ComposeColor(0xFF2196F3),
                                                        modifier = Modifier
                                                            .padding(start = 4.dp)
                                                            .clickable {
                                                                // Confirm and remove mark
                                                                markedChangedTaskIds = markedChangedTaskIds - task.id
                                                            },
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                }

                                                TextButton(
                                                    onClick = { testNotificationForTask(task) },
                                                    modifier = Modifier.padding(horizontal = 4.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Notifications,
                                                        contentDescription = "Тест уведомления",
                                                        modifier = Modifier
                                                            .width(20.dp)
                                                            .height(20.dp),
                                                    )
                                                }

                                                val checked = task.completed
                                                Checkbox(checked = checked, onCheckedChange = { isChecked ->
                                                    Log.d(
                                                        "TasksScreen",
                                                        "Checkbox clicked: task.id=${task.id}, currentChecked=$checked, newChecked=$isChecked",
                                                    )
                                                    if (isChecked && !checked) {
                                                        scope.launch {
                                                            // Offline-first: LocalApi работает даже без apiBaseUrl (обновляет локальный кэш)
                                                            try {
                                                                val updated = withContext(Dispatchers.IO) {
                                                                    localApi.completeTask(task.id)
                                                                }
                                                                // Явная синхронизация в фоне
                                                                if (syncService.isOnline()) {
                                                                    scope.launch(Dispatchers.IO) {
                                                                        syncService.syncQueue()
                                                                        markSuccessfulRequest()
                                                                    }
                                                                }
                                                                Log.d("TasksScreen", "Task completed: id=${updated.id}, completed=${updated.completed}")
                                                                BinaryLogger.getInstance()?.log(LogLevel.INFO, "MainActivity", LogMessageCode.TASK_COMPLETE, mapOf("task_id" to updated.id, "title" to updated.title))
                                                                // Обновляем allTasks, getVisibleTasks() пересчитается при следующей рекомпозиции
                                                                val newAllTasks = allTasks.map { if (it.id == updated.id) updated else it }
                                                                allTasks = newAllTasks
                                                                Log.d("TasksScreen", "allTasks updated, new size=${newAllTasks.size}")
                                                            } catch (e: Exception) {
                                                                Log.e("TasksScreen", "Error completing task", e)
                                                                // Не вызываем markFailedRequest() - это оптимистичное обновление
                                                                // Реальный статус сети обновится при следующей синхронизации
                                                                e.printStackTrace()
                                                            }
                                                        }
                                                    } else if (!isChecked && checked) {
                                                        scope.launch {
                                                            // Offline-first: LocalApi работает даже без apiBaseUrl (обновляет локальный кэш)
                                                            try {
                                                                val updated = withContext(Dispatchers.IO) {
                                                                    localApi.uncompleteTask(task.id)
                                                                }
                                                                // Явная синхронизация в фоне
                                                                if (syncService.isOnline()) {
                                                                    scope.launch(Dispatchers.IO) {
                                                                        syncService.syncQueue()
                                                                        markSuccessfulRequest()
                                                                    }
                                                                }
                                                                Log.d("TasksScreen", "Task uncompleted: id=${updated.id}, completed=${updated.completed}")
                                                                // Обновляем allTasks, getVisibleTasks() пересчитается при следующей рекомпозиции
                                                                val newAllTasks = allTasks.map { if (it.id == updated.id) updated else it }
                                                                allTasks = newAllTasks
                                                                Log.d("TasksScreen", "allTasks updated, new size=${newAllTasks.size}")
                                                            } catch (e: Exception) {
                                                                Log.e("TasksScreen", "Error uncompleting task", e)
                                                                // Не вызываем markFailedRequest() - это оптимистичное обновление
                                                                // Реальный статус сети обновится при следующей синхронизации
                                                                e.printStackTrace()
                                                            }
                                                        }
                                                    }
                                                })
                                            }
                                        }
                                    )
                                }
                            }
                            }
                        }
                    }
                }
            }

            if (showEditDialog) {
                AlertDialog(
                    onDismissRequest = { showEditDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                if (apiBaseUrl == null) {
                                    error = "Настройте подключение к серверу"
                                    return@launch
                                }
                                try {
                                    val api = localApi
                                    if (editingTask == null) {
                                        val reminderForCreate = editReminderTime.ifBlank { formatIso(LocalDateTime.now()) }
                                        val template = Task(
                                            id = 0,
                                            title = editTitle,
                                            description = if (editDescription.isBlank()) null else editDescription,
                                            taskType = editTaskType,
                                            recurrenceType = if (editTaskType == "recurring") editRecurrenceType else null,
                                            recurrenceInterval = editRecurrenceInterval.toIntOrNull(),
                                            intervalDays = editIntervalDays.toIntOrNull(),
                                            reminderTime = reminderForCreate,
                                            groupId = editGroupId,
                                            active = true,
                                            completed = false,
                                        )
                                        // Validation
                                        titleError = if (template.title.isBlank()) "Укажите название" else null
                                        reminderTimeError = if (parseIsoOrNull(template.reminderTime) == null) "Неверный формат даты-времени" else null
                                        recurrenceError = null
                                        // For recurring tasks, ensure recurrence type is set (default to daily if not set)
                                        var finalTemplate = template
                                        if (template.taskType == "recurring" && template.recurrenceType.isNullOrBlank()) {
                                            finalTemplate = template.copy(recurrenceType = "daily")
                                        }
                                        if (titleError != null || reminderTimeError != null || recurrenceError != null) return@launch
                                        // Create via API (offline-first: оптимистичное обновление, синхронизация в фоне)
                                        try {
                                            val created = withContext(Dispatchers.IO) { api.createTask(finalTemplate, editAssignedUserIds) }
                                            // Явная синхронизация в фоне
                                            if (syncService.isOnline()) {
                                                scope.launch(Dispatchers.IO) {
                                                    syncService.syncQueue()
                                                    markSuccessfulRequest()
                                                }
                                            }
                                            android.util.Log.d("TasksScreen", "Task created via API: id=${created.id}, refreshing list")
                                            BinaryLogger.getInstance()?.log(LogLevel.INFO, "MainActivity", LogMessageCode.TASK_CREATE, mapOf("task_id" to created.id, "title" to created.title))
                                            // Обновляем UI из кэша (задача уже создана и сохранена локально)
                                            // WebSocket message will also trigger update, but this ensures immediate update
                                            uiRefreshKey += 1
                                        } catch (e: Exception) {
                                            Log.e("TasksScreen", "Error creating task", e)
                                            // Не вызываем markFailedRequest() - это оптимистичное обновление
                                            // Реальный статус сети обновится при следующей синхронизации
                                        }
                                    } else {
                                        val base = editingTask!!
                                        // Для обновления также гарантируем наличие reminderTime
                                        // Используем editReminderTime, если оно не пустое, иначе старое значение
                                        val reminderForUpdate = if (editReminderTime.isNotBlank()) {
                                            editReminderTime
                                        } else {
                                            base.reminderTime
                                        }
                                        android.util.Log.d("TasksScreen", "Updating task: id=${base.id}, oldReminderTime=${base.reminderTime}, newReminderTime=$reminderForUpdate, editReminderTime=$editReminderTime")
                                        val updatedPayload = base.copy(
                                            title = editTitle,
                                            description = if (editDescription.isBlank()) null else editDescription,
                                            taskType = editTaskType,
                                            recurrenceType = if (editTaskType == "recurring") editRecurrenceType else null,
                                            recurrenceInterval = editRecurrenceInterval.toIntOrNull(),
                                            intervalDays = editIntervalDays.toIntOrNull(),
                                            reminderTime = reminderForUpdate,
                                            groupId = editGroupId,
                                            active = base.active,
                                            completed = base.completed,
                                        )
                                        titleError = if (updatedPayload.title.isBlank()) "Укажите название" else null
                                        reminderTimeError = if (parseIsoOrNull(updatedPayload.reminderTime) == null) "Неверный формат даты-времени" else null
                                        recurrenceError = null
                                        // For recurring tasks, ensure recurrence type is set (default to daily if not set)
                                        var finalPayload = updatedPayload
                                        if (updatedPayload.taskType == "recurring" && updatedPayload.recurrenceType.isNullOrBlank()) {
                                            finalPayload = updatedPayload.copy(recurrenceType = "daily")
                                        }
                                        if (titleError != null || reminderTimeError != null || recurrenceError != null) return@launch
                                        android.util.Log.d("TasksScreen", "Saving task with reminderTime: ${finalPayload.reminderTime}")
                                        // Update via API (offline-first: оптимистичное обновление, синхронизация в фоне)
                                        try {
                                            val updated = withContext(Dispatchers.IO) { localApi.updateTask(base.id, finalPayload, editAssignedUserIds) }
                                            android.util.Log.d("TasksScreen", "Task updated via API: id=${updated.id}, reminderTime=${updated.reminderTime}, refreshing list")
                                            BinaryLogger.getInstance()?.log(LogLevel.INFO, "MainActivity", LogMessageCode.TASK_UPDATE, mapOf<String, Any>("task_id" to updated.id, "title" to updated.title))
                                            // Явная синхронизация в фоне
                                            if (syncService.isOnline()) {
                                                scope.launch(Dispatchers.IO) {
                                                    syncService.syncQueue()
                                                    markSuccessfulRequest()
                                                }
                                            }
                                            // Обновляем UI из кэша (задача уже обновлена локально)
                                            // WebSocket message will also trigger update, but this ensures immediate update
                                            uiRefreshKey += 1
                                        } catch (e: Exception) {
                                            Log.e("TasksScreen", "Error updating task", e)
                                            // Не вызываем markFailedRequest() - это оптимистичное обновление
                                            // Реальный статус сети обновится при следующей синхронизации
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("TasksScreen", "Save task error", e)
                                    // Не вызываем markFailedRequest() - это может быть локальная ошибка валидации
                                } finally {
                                    showEditDialog = false
                                }
                            }
                        }) { Text("Сохранить") }
                    },
                    dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Отмена") } },
                    title = { Text(if (editingTask == null) "Новая задача" else "Редактировать задачу") },
                    text = {
                        Column {
                            OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Название") }, isError = titleError != null, supportingText = { if (titleError != null) Text(titleError!!) })
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Group dropdown after title
                            val groupEntries = groups.entries.sortedBy { it.value }
                            ExposedDropdownMenuBox(expanded = editGroupExpanded, onExpandedChange = { editGroupExpanded = !editGroupExpanded }) {
                                OutlinedTextField(
                                    value = editGroupId?.let { gid -> groups[gid] ?: gid.toString() } ?: "— Не задано —",
                                    onValueChange = {}, readOnly = true, label = { Text("Группа") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editGroupExpanded) }, modifier = Modifier.menuAnchor()
                                )
                                DropdownMenu(expanded = editGroupExpanded, onDismissRequest = { editGroupExpanded = false }) {
                                    DropdownMenuItem(text = { Text("— Не задано —") }, onClick = { editGroupId = null; editGroupExpanded = false })
                                    groupEntries.forEach { e ->
                                        DropdownMenuItem(text = { Text(e.value) }, onClick = { editGroupId = e.key; editGroupExpanded = false })
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val taskTypeOptions = listOf("one_time" to "Разовая", "recurring" to "Расписание", "interval" to "Интервальная")
                            ExposedDropdownMenuBox(expanded = editTaskTypeExpanded, onExpandedChange = { editTaskTypeExpanded = !editTaskTypeExpanded }) {
                                OutlinedTextField(
                                    value = taskTypeOptions.find { it.first == editTaskType }?.second ?: editTaskType,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Тип задачи") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editTaskTypeExpanded) },
                                    modifier = Modifier.menuAnchor()
                                )
                                DropdownMenu(expanded = editTaskTypeExpanded, onDismissRequest = { editTaskTypeExpanded = false }) {
                                    taskTypeOptions.forEach { (opt, label) ->
                                        DropdownMenuItem(text = { Text(label) }, onClick = { 
                                            editTaskType = opt
                                            // Set default recurrence type when switching to recurring
                                            if (opt == "recurring") {
                                                if (editRecurrenceType.isNullOrBlank()) {
                                                    editRecurrenceType = "daily"
                                                }
                                            }
                                            editTaskTypeExpanded = false 
                                        })
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // Show recurrence type only for recurring tasks
                            if (editTaskType == "recurring") {
                                val recurrenceOptions = listOf("daily" to "Ежедневно", "weekly" to "Еженедельно", "monthly" to "Ежемесячно", "yearly" to "Ежегодно")
                                // Ensure default value is set
                                if (editRecurrenceType.isNullOrBlank()) {
                                    editRecurrenceType = "daily"
                                }
                                ExposedDropdownMenuBox(expanded = editRecurrenceTypeExpanded, onExpandedChange = { editRecurrenceTypeExpanded = !editRecurrenceTypeExpanded }) {
                                    OutlinedTextField(
                                        value = editRecurrenceType?.let { recType -> recurrenceOptions.find { it.first == recType }?.second ?: recType } ?: "Ежедневно",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Тип повторения") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editRecurrenceTypeExpanded) },
                                        modifier = Modifier.menuAnchor(),
                                        isError = recurrenceError != null,
                                        supportingText = { if (recurrenceError != null) Text(recurrenceError!!) }
                                    )
                                    DropdownMenu(expanded = editRecurrenceTypeExpanded, onDismissRequest = { editRecurrenceTypeExpanded = false }) {
                                        recurrenceOptions.forEach { (opt, label) ->
                                            DropdownMenuItem(text = { Text(label) }, onClick = { editRecurrenceType = opt; editRecurrenceTypeExpanded = false })
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            // Show interval fields only for recurring and interval tasks
                            if (editTaskType == "recurring") {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(value = editRecurrenceInterval, onValueChange = { editRecurrenceInterval = it }, label = { Text("Интервал") })
                            }
                            if (editTaskType == "interval") {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(value = editIntervalDays, onValueChange = { editIntervalDays = it }, label = { Text("Интервал") })
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = editReminderTime,
                                    onValueChange = { editReminderTime = it },
                                    label = { Text("Дата/время напоминания (ISO 8601)") },
                                    modifier = Modifier.weight(1f),
                                    isError = reminderTimeError != null,
                                    supportingText = { if (reminderTimeError != null) Text(reminderTimeError!!) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = {
                                    pickDateTime(parseIsoOrNull(editReminderTime)) { picked ->
                                        editReminderTime = formatIso(picked)
                                    }
                                }) { Text("Выбрать") }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // User assignment
                            val activeUsers = users.filter { it.isActive }
                            ExposedDropdownMenuBox(expanded = editUsersExpanded, onExpandedChange = { editUsersExpanded = !editUsersExpanded }) {
                                OutlinedTextField(
                                    value = if (editAssignedUserIds.isEmpty()) {
                                        "— Не назначено —"
                                    } else {
                                        editAssignedUserIds.mapNotNull { userId ->
                                            users.find { it.id == userId }?.name
                                        }.joinToString(", ")
                                    },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Назначено пользователям") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editUsersExpanded) },
                                    modifier = Modifier.menuAnchor()
                                )
                                DropdownMenu(expanded = editUsersExpanded, onDismissRequest = { editUsersExpanded = false }) {
                                    activeUsers.forEach { user ->
                                        val isSelected = editAssignedUserIds.contains(user.id)
                                        DropdownMenuItem(
                                            text = { 
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (isSelected) {
                                                        Text("✓ ", color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    Text(user.name)
                                                }
                                            },
                                            onClick = { 
                                                editAssignedUserIds = if (isSelected) {
                                                    editAssignedUserIds - user.id
                                                } else {
                                                    editAssignedUserIds + user.id
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // Description at the end
                            OutlinedTextField(value = editDescription, onValueChange = { editDescription = it }, label = { Text("Описание") })
                            Spacer(modifier = Modifier.height(8.dp))
                            // Presets quick actions
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    editTaskType = "recurring"; editRecurrenceType = "daily"; editRecurrenceInterval = "1"
                                    parseIsoOrNull(editReminderTime) ?: run { editReminderTime = formatIso(LocalDateTime.now().withHour(9).withMinute(0)) }
                                }) { Text("Ежедневно 9:00") }
                                TextButton(onClick = {
                                    editTaskType = "recurring"; editRecurrenceType = "weekly"; editRecurrenceInterval = "1"
                                    parseIsoOrNull(editReminderTime) ?: run { editReminderTime = formatIso(LocalDateTime.now().withHour(10).withMinute(0)) }
                                }) { Text("Еженедельно") }
                            }
                        }
                    }
                )
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false; pendingDeleteTaskId = null },
                    confirmButton = {
                        TextButton(onClick = {
                            val toDelete = pendingDeleteTaskId
                            if (toDelete != null) {
                                scope.launch {
                                    if (apiBaseUrl == null) return@launch
                                    try {
                                        withContext(Dispatchers.IO) {
                                            localApi.deleteTask(toDelete)
                                        }
                                        // Явная синхронизация в фоне
                                        if (syncService.isOnline()) {
                                            scope.launch(Dispatchers.IO) {
                                                syncService.syncQueue()
                                                markSuccessfulRequest()
                                            }
                                        }
                                        allTasks = allTasks.filter { it.id != toDelete }
                                    } catch (e: Exception) {
                                        Log.e("TasksScreen", "Delete error", e)
                                        // Не вызываем markFailedRequest() - это оптимистичное обновление
                                        // Реальный статус сети обновится при следующей синхронизации
                                    } finally {
                                        showDeleteConfirm = false
                                        pendingDeleteTaskId = null
                                    }
                                }
                            } else {
                                showDeleteConfirm = false
                            }
                        }) { Text("Удалить") }
                    },
                    dismissButton = { TextButton(onClick = { showDeleteConfirm = false; pendingDeleteTaskId = null }) { Text("Отмена") } },
                    title = { Text("Удалить задачу?") },
                    text = { Text("Действие нельзя отменить.") }
                )
            }
        }
    }
    
    // Диалог запроса разрешения на точные будильники
    if (showExactAlarmDialog && mainActivity != null) {
        AlertDialog(
            onDismissRequest = { 
                showExactAlarmDialog = false
                // Убираем флаг, чтобы диалог больше не показывался
                val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("show_exact_alarm_dialog", false)
                    .putBoolean("exact_alarm_request_shown", true)
                    .apply()
            },
            title = { Text("Разрешение на точные будильники") },
            text = { 
                Text("Для работы напоминаний приложению необходимо разрешение на использование точных будильников.\n\n" +
                     "Нажмите \"Открыть настройки\" и включите переключатель \"Разрешить\" в разделе \"Alarms & reminders\".")
            },
            confirmButton = {
                Button(onClick = {
                    showExactAlarmDialog = false
                    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("show_exact_alarm_dialog", false)
                        .putBoolean("exact_alarm_request_shown", true)
                        .apply()
                    
                    // Открываем настройки для запроса разрешения
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            mainActivity.requestExactAlarmSettingsLauncher.launch(intent)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Failed to open exact alarm settings", e)
                        }
                    }
                }) {
                    Text("Открыть настройки")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showExactAlarmDialog = false
                    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("show_exact_alarm_dialog", false)
                        .putBoolean("exact_alarm_request_shown", true)
                        .apply()
                }) {
                    Text("Позже")
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    networkSettings: NetworkSettings,
    userSettings: UserSettings,
    networkConfig: NetworkConfig?,
    selectedUser: SelectedUser?,
    apiBaseUrl: String?,
    onConfigChanged: () -> Unit,
    onUserChanged: () -> Unit,
    statusBarCompactMode: Boolean,
    onStatusBarCompactModeChanged: (Boolean) -> Unit,
    storagePercentage: Float,
    pendingOperations: Int,
    cachedTasksCount: Int
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showEditDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showConnectionTest by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<String?>(null) }
    var isLoadingTest by remember { mutableStateOf(false) }
    var showUserPickerDialog by remember { mutableStateOf(false) }
    var users by remember { mutableStateOf<List<UserSummary>>(emptyList()) }
    var isUsersLoading by remember { mutableStateOf(false) }
    var usersError by remember { mutableStateOf<String?>(null) }
    
    // Edit dialog state
    var editHost by remember { mutableStateOf("") }
    var editPort by remember { mutableStateOf("8000") }
    var editApiVersion by remember { mutableStateOf(SupportedApiVersions.defaultVersion) }
    var editUseHttps by remember { mutableStateOf(true) }
    var editApiVersionExpanded by remember { mutableStateOf(false) }
    
    // Initialize edit dialog with current config
    LaunchedEffect(networkConfig, showEditDialog) {
        if (showEditDialog && networkConfig != null) {
            editHost = networkConfig.host
            editPort = networkConfig.port.toString()
            editApiVersion = networkConfig.apiVersion
            editUseHttps = networkConfig.useHttps
        } else if (showEditDialog && networkConfig == null) {
            editHost = ""
            editPort = "8000"
            editApiVersion = SupportedApiVersions.defaultVersion
            editUseHttps = true
        }
    }
    
    suspend fun refreshUsersList() {
        val base = apiBaseUrl?.takeIf { it.isNotBlank() } ?: run {
            users = emptyList()
            return
        }
        usersError = null
        isUsersLoading = true
        try {
            val api = UsersApi(baseUrl = base)
            val fetched = withContext(Dispatchers.IO) {
                api.getUsers()
            }
            users = fetched
        } catch (e: IllegalArgumentException) {
            // Некорректный URL - не критично, просто не загружаем пользователей
            android.util.Log.w("SettingsScreen", "Invalid API URL for users: $base", e)
            users = emptyList()
            usersError = "Некорректный адрес API"
        } catch (e: IllegalStateException) {
            // Ошибка HTTP - не критично
            android.util.Log.w("SettingsScreen", "HTTP error loading users from $base", e)
            users = emptyList()
            usersError = "Ошибка подключения: ${e.message}"
        } catch (e: Exception) {
            // Любая другая ошибка
            android.util.Log.e("SettingsScreen", "Error loading users from $base", e)
            users = emptyList()
            usersError = e.message ?: "Не удалось загрузить пользователей"
        } finally {
            isUsersLoading = false
        }
    }
    
    LaunchedEffect(apiBaseUrl, networkConfig) {
        if (apiBaseUrl != null && apiBaseUrl.isNotBlank()) {
            refreshUsersList()
        } else {
            // Очищаем список пользователей только если apiBaseUrl стал null/пустым
            // и это не первая инициализация (чтобы не сбрасывать уже загруженных пользователей)
            if (users.isNotEmpty()) {
            users = emptyList()
            }
        }
    }

    fun selectUser(user: UserSummary) {
        scope.launch {
            userSettings.saveSelectedUser(SelectedUser(user.id, user.name))
            showUserPickerDialog = false
            onUserChanged()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Настройки",
            style = MaterialTheme.typography.titleLarge
        )
        
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
                    text = "Строка состояния",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Компактный режим",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Отображать только иконки без текста",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = statusBarCompactMode,
                        onCheckedChange = onStatusBarCompactModeChanged
                    )
                }
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Заполненность хранилища: ${storagePercentage.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = (storagePercentage / 100f).coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                    
                    // Информация о хранилище
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Информация о хранилище",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Задач в хранилище: $cachedTasksCount",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Операций в очереди: $pendingOperations",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // Статистика бинарных логов (чанков)
                    val binaryStorage = remember {
                        com.homeplanner.debug.BinaryLogger.getStorage()
                    }
                    val (chunksCount, chunksSizeBytes) = remember {
                        binaryStorage?.getChunksStats() ?: (0 to 0L)
                    }
                    Text(
                        text = "Чанков логов: $chunksCount",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Размер логов: ${chunksSizeBytes / 1024} КБ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
                
                if (networkConfig == null) {
                    Text(
                        text = "⚠️ Подключение не настроено",
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Настройте подключение к серверу для работы приложения",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "API URL: ${networkConfig.toApiBaseUrl()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Хост: ${networkConfig.host}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Порт: ${networkConfig.port}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "API Версия: ${networkConfig.apiVersion}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Протокол: ${if (networkConfig.useHttps) "HTTPS" else "HTTP"}",
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
                                    onConfigChanged()
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
                                    
                                    // Test HTTP connection
                                    try {
                                        val api = TasksApi(baseUrl = config.toApiBaseUrl())
                                        withContext(Dispatchers.IO) {
                                            api.getTasks(activeOnly = true)
                                        }
                                        results.add("✓ HTTP подключение успешно")
                                    } catch (e: Exception) {
                                        results.add("✗ HTTP ошибка: ${e.message}")
                                    }
                                    
                                    // Test WebSocket connection
                                    try {
                                        val wsUrl = config.toWebSocketUrl()
                                        val client = OkHttpClient.Builder()
                                            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                            .build()
                                        
                                        var wsConnected = false
                                        var wsError: String? = null
                                        val latch = java.util.concurrent.CountDownLatch(1)
                                        
                                        val request = Request.Builder()
                                            .url(wsUrl)
                                            .build()
                                        
                                        val listener = object : WebSocketListener() {
                                            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                                                wsConnected = true
                                                webSocket.close(1000, "Test connection")
                                                latch.countDown()
                                            }
                                            
                                            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                                                wsError = t.message ?: "Unknown error"
                                                latch.countDown()
                                            }
                                            
                                            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                                                if (!wsConnected) {
                                                    wsError = "Connection closed: $code $reason"
                                                }
                                                latch.countDown()
                                            }
                                        }
                                        
                                        withContext(Dispatchers.IO) {
                                            val ws = client.newWebSocket(request, listener)
                                            // Wait for connection result (max 5 seconds)
                                            if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                                                ws.close(1000, "Timeout")
                                                wsError = "Timeout waiting for connection"
                                            }
                                        }
                                        
                                        if (wsConnected) {
                                            results.add("✓ WebSocket подключение успешно")
                                        } else {
                                            results.add("✗ WebSocket ошибка: ${wsError ?: "Connection failed"}")
                                        }
                                    } catch (e: Exception) {
                                        results.add("✗ WebSocket ошибка: ${e.message}")
                                    }
                                    
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
                    text = "Пользователь",
                    style = MaterialTheme.typography.titleMedium
                )
                val currentUserText = selectedUser?.let { "${it.name} (#${it.id})" } ?: "Пользователь не выбран"
                Text(
                    text = currentUserText,
                    color = if (selectedUser == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (apiBaseUrl == null) {
                    Text(
                        text = "Сначала настройте подключение, чтобы загрузить список пользователей.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    if (isUsersLoading) {
                        Text(
                            text = "Загрузка списка пользователей...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (users.isEmpty()) {
                        Text(
                            text = "Пользователи не найдены. Создайте пользователя через веб-интерфейс.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = "Доступно пользователей: ${users.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Button(
                        onClick = { showUserPickerDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = users.isNotEmpty() && !isUsersLoading
                    ) {
                        Text("Выбрать пользователя")
                    }
                    
                    if (selectedUser != null) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    userSettings.clearSelectedUser()
                                    onUserChanged()
                                }
                            }
                        ) {
                            Text("Сбросить выбор")
                        }
                    }
                }
            }
        }
    }
    
    // User picker dialog
    if (showUserPickerDialog) {
        AlertDialog(
            onDismissRequest = { showUserPickerDialog = false },
            title = { Text("Выберите пользователя") },
            text = {
                when {
                    apiBaseUrl == null -> Text("Сначала настройте подключение к серверу.")
                    isUsersLoading -> Text("Загрузка списка пользователей...")
                    users.isEmpty() -> Text("Список пользователей пуст. Создайте пользователя через веб-интерфейс.")
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .fillMaxWidth()
                        ) {
                            items(users, key = { it.id }) { user ->
                                val enabled = user.isActive
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = enabled) { if (enabled) selectUser(user) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = user.id == selectedUser?.id,
                                        onClick = if (enabled) ({ selectUser(user) }) else null,
                                        enabled = enabled
                                    )
                                    Column(modifier = Modifier.padding(start = 8.dp)) {
                                        Text(user.name)
                                        val subtitleParts = buildList {
                                            if (!user.email.isNullOrBlank()) add(user.email!!)
                                            add("Роль: ${user.role}")
                                            if (!user.isActive) add("Неактивен")
                                        }
                                        if (subtitleParts.isNotEmpty()) {
                                            Text(
                                                text = subtitleParts.joinToString(" • "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (user.isActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUserPickerDialog = false }) {
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
                            onConfigChanged()
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
                                    onConfigChanged()
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

private fun getStatusBarCompactMode(context: Context): Boolean {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean("status_bar_compact_mode", false)
}

private fun setStatusBarCompactMode(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("status_bar_compact_mode", enabled).apply()
}

// Кэширование групп для оффлайн режима
private fun saveGroupsToCache(context: Context, groups: Map<Int, String>) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val editor = prefs.edit()
    // Очищаем старые группы
    val oldKeys = prefs.all.keys.filter { it.startsWith("group_") && !it.startsWith("group_id_") && !it.startsWith("group_count") }
    oldKeys.forEach { editor.remove(it) }
    // Сохраняем каждую группу
    groups.forEach { (id, name) ->
        editor.putString("group_$id", name)
    }
    editor.apply()
    android.util.Log.d("TasksScreen", "Saved ${groups.size} groups to cache")
}

private fun loadGroupsFromCache(context: Context): Map<Int, String> {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val groups = mutableMapOf<Int, String>()
    
    // Загружаем все ключи, начинающиеся с "group_" (но не "group_id_" или "group_count")
    val allKeys = prefs.all.keys.filter { it.startsWith("group_") && !it.startsWith("group_id_") && !it.startsWith("group_count") }
    allKeys.forEach { key ->
        val idStr = key.removePrefix("group_")
        val id = idStr.toIntOrNull()
        if (id != null) {
            val name = prefs.getString(key, null)
            if (name != null) {
                groups[id] = name
            }
        }
    }
    
    android.util.Log.d("TasksScreen", "Loaded ${groups.size} groups from cache")
    return groups
}


