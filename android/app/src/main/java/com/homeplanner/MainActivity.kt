package com.homeplanner

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
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
import com.homeplanner.api.TasksApi
import com.homeplanner.api.GroupsApi
import com.homeplanner.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
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
import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts

// View tabs for bottom navigation
enum class ViewTab { TODAY, ALL, SETTINGS, WEBSOCKET }

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
    private val requestExactAlarmSettingsLauncher = registerForActivityResult(
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (am != null && !am.canScheduleExactAlarms()) {
                Log.d("MainActivity", "Requesting SCHEDULE_EXACT_ALARM permission")
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    requestExactAlarmSettingsLauncher.launch(intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to open exact alarm settings", e)
                }
            } else {
                Log.d("MainActivity", "SCHEDULE_EXACT_ALARM permission already granted")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
fun TasksScreen() {
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    // Key used to trigger refresh via LaunchedEffect
    var refreshKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(ViewTab.TODAY) }
    var wsLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var wsConnected by remember { mutableStateOf(false) }
    var wsConnecting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val reminderScheduler = remember(context) { ReminderScheduler(context) }
    
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
    var editTaskTypeExpanded by remember { mutableStateOf(false) }
    var editRecurrenceTypeExpanded by remember { mutableStateOf(false) }
    var editGroupExpanded by remember { mutableStateOf(false) }

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

    fun appendLog(direction: String, payload: String) {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val entry = "[$ts][$direction] $payload"
        wsLog = (wsLog + entry).takeLast(500)
    }

    suspend fun loadTasks() {
        isLoading = true
        error = null
        try {
            val api = TasksApi()
            val groupsApi = GroupsApi()
            // Load tasks based on current view: use /today endpoint for today view
            val t = withContext(Dispatchers.IO) {
                if (selectedTab == ViewTab.TODAY) {
                    api.getTodayTasks()
                } else {
                    api.getTasks(activeOnly = false)
                }
            }
            val g = withContext(Dispatchers.IO) {
                groupsApi.getAll()
            }
            tasks = t
            groups = g
            // Reschedule reminders after data refresh
            try {
                reminderScheduler.cancelAll(t)
                reminderScheduler.scheduleForTasks(t)
            } catch (e: Exception) {
                Log.e("TasksScreen", "Scheduling error", e)
            }
        } catch (e: Exception) {
            error = e.message ?: "Unknown error"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadTasks()
    }

    // Helper to parse Task from JSON
    fun parseTaskFromJson(json: JSONObject): Task {
        val reminderValue = json.optString("reminder_time", null)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Отсутствует reminder_time в ответе сервера: $json")
        val activeValue = if (json.isNull("active")) true else json.getBoolean("active")
        val completedValue = if (json.isNull("completed")) false else json.getBoolean("completed")
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
        )
    }

    // Update tasks list based on WebSocket event
    fun updateTasksFromEvent(action: String, taskJson: JSONObject?, taskId: Int?) {
        scope.launch {
            try {
                android.util.Log.d("TasksScreen", "updateTasksFromEvent: action=$action, selectedTab=$selectedTab")
                
                // For today view, reload from backend to ensure correct filtering
                // (backend filters tasks based on unified logic)
                if (selectedTab == ViewTab.TODAY) {
                    // Reload tasks from /today endpoint to ensure correct filtering
                    android.util.Log.d("TasksScreen", "Today tab: reloading from backend")
                    refreshKey += 1
                    // Also reschedule reminders after reload
                    try {
                        reminderScheduler.cancelAll(tasks)
                        reminderScheduler.scheduleForTasks(tasks)
                    } catch (e: Exception) {
                        Log.e("TasksScreen", "Reschedule error after WebSocket update", e)
                    }
                    return@launch
                }
                
                // For other views, update locally
                val currentList = tasks.toMutableList()
                var needsReschedule = false
                when (action) {
                    "created", "updated" -> {
                        if (taskJson != null) {
                            val updatedTask = parseTaskFromJson(taskJson)
                            android.util.Log.d("TasksScreen", "Updating task locally: id=${updatedTask.id}, title=${updatedTask.title}")
                            val index = currentList.indexOfFirst { it.id == updatedTask.id }
                            if (index >= 0) {
                                currentList[index] = updatedTask
                                android.util.Log.d("TasksScreen", "Task replaced at index $index")
                            } else {
                                currentList.add(updatedTask)
                                android.util.Log.d("TasksScreen", "Task added to list")
                            }
                            tasks = currentList.toList()
                            needsReschedule = true
                        } else {
                            android.util.Log.w("TasksScreen", "No taskJson for action=$action")
                        }
                    }
                    "completed", "uncompleted", "shown" -> {
                        if (taskJson != null) {
                            val updatedTask = parseTaskFromJson(taskJson)
                            val index = currentList.indexOfFirst { it.id == updatedTask.id }
                            if (index >= 0) {
                                currentList[index] = updatedTask
                            } else {
                                currentList.add(updatedTask)
                            }
                            tasks = currentList.toList()
                        }
                    }
                    "deleted" -> {
                        taskId?.let { id ->
                            val removed = currentList.removeAll { it.id == id }
                            android.util.Log.d("TasksScreen", "Task deleted: id=$id, removed=$removed")
                            tasks = currentList.toList()
                            needsReschedule = true
                        }
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
                        reminderScheduler.cancelAll(tasks)
                        reminderScheduler.scheduleForTasks(tasks)
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

    // WebSocket auto-refresh with reconnection
    LaunchedEffect(Unit) {
        val client = OkHttpClient()
        var currentWs: WebSocket? = null
        var reconnectDelay = 2000L // Start with 2 seconds
        
        suspend fun connectWebSocket() {
            try {
                wsConnecting = true
                val request = Request.Builder()
                    .url("ws://192.168.1.2:8000/ws")
                    .build()
                val listener = object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        // Log ALL incoming messages first
                        android.util.Log.d("TasksScreen", "WS raw message: $text")
                        scope.launch { appendLog("WS<-", text) }
                        
                        try {
                            val msg = JSONObject(text)
                            if (msg.getString("type") == "task_update") {
                                val action = msg.getString("action")
                                val taskId = if (msg.has("task_id")) msg.getInt("task_id") else null
                                val taskJson = if (msg.has("task")) msg.getJSONObject("task") else null
                                android.util.Log.d("TasksScreen", "WS message: action=$action, taskId=$taskId")
                                updateTasksFromEvent(action, taskJson, taskId)
                            } else {
                                android.util.Log.d("TasksScreen", "WS message type ignored: ${msg.optString("type", "unknown")}")
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
                            appendLog("WS", "connection opened")
                            wsConnected = true
                            wsConnecting = false
                        }
                        reconnectDelay = 2000L // Reset delay on successful connection
                    }
                    
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                        android.util.Log.e("TasksScreen", "WS connection failure", t)
                        scope.launch {
                            appendLog("WS", "connection failed: ${t.message}")
                            wsConnected = false
                            wsConnecting = true
                            // Trigger reconnection after delay
                            kotlinx.coroutines.delay(reconnectDelay)
                            connectWebSocket()
                        }
                    }
                    
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        android.util.Log.d("TasksScreen", "WS connection closed: code=$code, reason=$reason")
                        scope.launch {
                            appendLog("WS", "connection closed: code=$code, retry in ${reconnectDelay}ms")
                            wsConnected = false
                            wsConnecting = true
                            // Reconnect after delay
                            kotlinx.coroutines.delay(reconnectDelay)
                            connectWebSocket()
                        }
                    }
                }
                currentWs = client.newWebSocket(request, listener)
            } catch (e: Exception) {
                android.util.Log.e("TasksScreen", "Failed to create WebSocket", e)
                // Retry after delay with exponential backoff (max 30 seconds)
                reconnectDelay = minOf(reconnectDelay * 2, 30000L)
                kotlinx.coroutines.delay(reconnectDelay)
                connectWebSocket()
            }
        }
        
        // Initial connection
        connectWebSocket()
        
        // Keep alive until disposed
        awaitCancellation()
        currentWs?.close(1000, null)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == ViewTab.TODAY,
                    onClick = { selectedTab = ViewTab.TODAY; refreshKey += 1 },
                    icon = { Icon(Icons.Outlined.Today, contentDescription = "Сегодня") },
                    label = { Text("Сегодня") }
                )
                NavigationBarItem(
                    selected = selectedTab == ViewTab.ALL,
                    onClick = { selectedTab = ViewTab.ALL; refreshKey += 1 },
                    icon = { Icon(Icons.Outlined.List, contentDescription = "Все задачи") },
                    label = { Text("Все задачи") }
                )
                NavigationBarItem(
                    selected = selectedTab == ViewTab.SETTINGS,
                    onClick = { selectedTab = ViewTab.SETTINGS },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "Настройки") },
                    label = { Text("Настройки") }
                )
                NavigationBarItem(
                    selected = selectedTab == ViewTab.WEBSOCKET,
                    onClick = { selectedTab = ViewTab.WEBSOCKET },
                    icon = { Icon(Icons.Outlined.Terminal, contentDescription = "WebSocket") },
                    label = { Text("WebSocket") }
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
                    editRecurrenceType = null
                    editRecurrenceInterval = ""
                    editIntervalDays = ""
                    editReminderTime = formatIso(LocalDateTime.now())
                    editGroupId = null
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
            // Connection status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusColor = when {
                    wsConnected -> ComposeColor(0xFF4CAF50) // Green
                    wsConnecting -> ComposeColor(0xFFFFC107) // Yellow/Orange
                    else -> ComposeColor(0xFFF44336) // Red
                }
                val statusText = when {
                    wsConnected -> "Подключено"
                    wsConnecting -> "Подключение..."
                    else -> "Отключено"
                }
                Canvas(
                    modifier = Modifier
                        .width(12.dp)
                        .height(12.dp)
                ) {
                    drawCircle(
                        color = statusColor,
                        radius = size.minDimension / 2
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }
            
            LaunchedEffect(refreshKey) {
                if (refreshKey > 0) {
                    loadTasks()
                }
            }
            if (error != null) {
                Text(text = "Ошибка: $error", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
            }

            val visibleTasks = when (selectedTab) {
                ViewTab.TODAY -> tasks
                ViewTab.ALL -> tasks
                ViewTab.SETTINGS -> emptyList()
                ViewTab.WEBSOCKET -> emptyList()
            }

            when (selectedTab) {
                ViewTab.SETTINGS -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Настройки", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Версия: ${BuildConfig.VERSION_NAME}")
                    }
                }

                ViewTab.WEBSOCKET -> {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(wsLog) { line ->
                            Text(text = line)
                        }
                    }
                }

                ViewTab.TODAY, ViewTab.ALL -> {
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
                                                editDescription = task.description ?: ""
                                                editTaskType = task.taskType
                                                editRecurrenceType = task.recurrenceType
                                                editRecurrenceInterval = task.recurrenceInterval?.toString() ?: ""
                                                editIntervalDays = task.intervalDays?.toString() ?: ""
                                                editReminderTime = task.reminderTime
                                                editGroupId = task.groupId
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
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            fun formatTime(dt: String?): String {
                                                if (dt.isNullOrBlank()) return "--:--"
                                                return try {
                                                    val ldt = LocalDateTime.parse(dt)
                                                    ldt.format(DateTimeFormatter.ofPattern("HH:mm"))
                                                } catch (_: DateTimeParseException) {
                                                    val timePart = dt.substringAfter('T', dt)
                                                    if (timePart.length >= 5) timePart.substring(0, 5) else "--:--"
                                                }
                                            }
                                            val timeText = formatTime(task.reminderTime)
                                            Text(timeText)
                                            Spacer(modifier = Modifier.width(12.dp))

                                            val groupName = task.groupId?.let { groups[it] } ?: ""
                                            val titleWithGroup = buildString {
                                                append("• ").append(task.title)
                                                if (groupName.isNotEmpty()) append(" (").append(groupName).append(")")
                                            }
                                            Text(
                                                text = titleWithGroup,
                                                modifier = Modifier.weight(1f),
                                            )

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
                                                        try {
                                                            appendLog("HTTP->", "POST /tasks/${task.id}/complete")
                                                            val updated = withContext(Dispatchers.IO) { TasksApi().completeTask(task.id) }
                                                            Log.d("TasksScreen", "Task completed: id=${updated.id}, completed=${updated.completed}")
                                                            val newList = tasks.map { if (it.id == updated.id) updated else it }
                                                            tasks = newList
                                                            Log.d("TasksScreen", "Tasks list updated, new size=${newList.size}")
                                                        } catch (e: Exception) {
                                                            Log.e("TasksScreen", "Error completing task", e)
                                                            appendLog("HTTP<-", "error: ${e.message}")
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                } else if (!isChecked && checked) {
                                                    scope.launch {
                                                        try {
                                                            appendLog("HTTP->", "POST /tasks/${task.id}/uncomplete")
                                                            val updated = withContext(Dispatchers.IO) { TasksApi().uncompleteTask(task.id) }
                                                            Log.d("TasksScreen", "Task uncompleted: id=${updated.id}, completed=${updated.completed}")
                                                            val newList = tasks.map { if (it.id == updated.id) updated else it }
                                                            tasks = newList
                                                            Log.d("TasksScreen", "Tasks list updated, new size=${newList.size}")
                                                        } catch (e: Exception) {
                                                            Log.e("TasksScreen", "Error uncompleting task", e)
                                                            appendLog("HTTP<-", "error: ${e.message}")
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
                                                    editDescription = task.description ?: ""
                                                    editTaskType = task.taskType
                                                    editRecurrenceType = task.recurrenceType
                                                    editRecurrenceInterval = task.recurrenceInterval?.toString() ?: ""
                                                    editIntervalDays = task.intervalDays?.toString() ?: ""
                                                    editReminderTime = task.reminderTime
                                                    editGroupId = task.groupId
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
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                fun formatTime(dt: String?): String {
                                                    if (dt.isNullOrBlank()) return "--:--"
                                                    return try {
                                                        val ldt = LocalDateTime.parse(dt)
                                                        ldt.format(DateTimeFormatter.ofPattern("HH:mm"))
                                                    } catch (_: DateTimeParseException) {
                                                        val timePart = dt.substringAfter('T', dt)
                                                        if (timePart.length >= 5) timePart.substring(0, 5) else "--:--"
                                                    }
                                                }
                                                val timeText = formatTime(task.reminderTime)
                                                Text(timeText)
                                                Spacer(modifier = Modifier.width(12.dp))

                                                val groupName = task.groupId?.let { groups[it] } ?: ""
                                                val titleWithGroup = buildString {
                                                    append("• ").append(task.title)
                                                    if (groupName.isNotEmpty()) append(" (").append(groupName).append(")")
                                                }
                                                Text(
                                                    text = titleWithGroup,
                                                    modifier = Modifier.weight(1f),
                                                )

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
                                                            try {
                                                                appendLog("HTTP->", "POST /tasks/${task.id}/complete")
                                                                val updated = withContext(Dispatchers.IO) { TasksApi().completeTask(task.id) }
                                                                Log.d("TasksScreen", "Task completed: id=${updated.id}, completed=${updated.completed}")
                                                                val newList = tasks.map { if (it.id == updated.id) updated else it }
                                                                tasks = newList
                                                                Log.d("TasksScreen", "Tasks list updated, new size=${newList.size}")
                                                            } catch (e: Exception) {
                                                                Log.e("TasksScreen", "Error completing task", e)
                                                                appendLog("HTTP<-", "error: ${e.message}")
                                                                e.printStackTrace()
                                                            }
                                                        }
                                                    } else if (!isChecked && checked) {
                                                        scope.launch {
                                                            try {
                                                                appendLog("HTTP->", "POST /tasks/${task.id}/uncomplete")
                                                                val updated = withContext(Dispatchers.IO) { TasksApi().uncompleteTask(task.id) }
                                                                Log.d("TasksScreen", "Task uncompleted: id=${updated.id}, completed=${updated.completed}")
                                                                val newList = tasks.map { if (it.id == updated.id) updated else it }
                                                                tasks = newList
                                                                Log.d("TasksScreen", "Tasks list updated, new size=${newList.size}")
                                                            } catch (e: Exception) {
                                                                Log.e("TasksScreen", "Error uncompleting task", e)
                                                                appendLog("HTTP<-", "error: ${e.message}")
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

            if (showEditDialog) {
                AlertDialog(
                    onDismissRequest = { showEditDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    val api = TasksApi()
                                    if (editingTask == null) {
                                        val reminderForCreate = editReminderTime.ifBlank { formatIso(LocalDateTime.now()) }
                                        val template = Task(
                                            id = 0,
                                            title = editTitle,
                                            description = if (editDescription.isBlank()) null else editDescription,
                                            taskType = editTaskType,
                                            recurrenceType = editRecurrenceType,
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
                                        if (template.taskType == "recurring" && template.recurrenceType.isNullOrBlank()) {
                                            recurrenceError = "Укажите тип повторения"
                                        }
                                        if (titleError != null || reminderTimeError != null || recurrenceError != null) return@launch
                                        // Create via API - WebSocket will handle UI update, but also refresh to ensure consistency
                                        val created = withContext(Dispatchers.IO) { api.createTask(template) }
                                        android.util.Log.d("TasksScreen", "Task created via API: id=${created.id}, refreshing list")
                                        // Refresh tasks list to ensure UI is updated
                                        // WebSocket message will also trigger update, but this ensures immediate update
                                        refreshKey += 1
                                    } else {
                                        val base = editingTask!!
                                        // Для обновления также гарантируем наличие reminderTime
                                        val reminderForUpdate = editReminderTime.ifBlank { base.reminderTime }
                                        val updatedPayload = base.copy(
                                            title = editTitle,
                                            description = if (editDescription.isBlank()) null else editDescription,
                                            taskType = editTaskType,
                                            recurrenceType = editRecurrenceType,
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
                                        if (updatedPayload.taskType == "recurring" && updatedPayload.recurrenceType.isNullOrBlank()) {
                                            recurrenceError = "Укажите тип повторения"
                                        }
                                        if (titleError != null || reminderTimeError != null || recurrenceError != null) return@launch
                                        // Update via API - WebSocket will handle UI update, but also refresh to ensure consistency
                                        val updated = withContext(Dispatchers.IO) { api.updateTask(base.id, updatedPayload) }
                                        android.util.Log.d("TasksScreen", "Task updated via API: id=${updated.id}, refreshing list")
                                        // Refresh tasks list to ensure UI is updated
                                        // WebSocket message will also trigger update, but this ensures immediate update
                                        refreshKey += 1
                                    }
                                } catch (e: Exception) {
                                    Log.e("TasksScreen", "Save task error", e)
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
                            OutlinedTextField(value = editDescription, onValueChange = { editDescription = it }, label = { Text("Описание") })
                            Spacer(modifier = Modifier.height(8.dp))
                            val taskTypeOptions = listOf("one_time","recurring","interval")
                            ExposedDropdownMenuBox(expanded = editTaskTypeExpanded, onExpandedChange = { editTaskTypeExpanded = !editTaskTypeExpanded }) {
                                OutlinedTextField(
                                    value = editTaskType,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Тип задачи") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editTaskTypeExpanded) },
                                    modifier = Modifier.menuAnchor()
                                )
                                DropdownMenu(expanded = editTaskTypeExpanded, onDismissRequest = { editTaskTypeExpanded = false }) {
                                    taskTypeOptions.forEach { opt ->
                                        DropdownMenuItem(text = { Text(opt) }, onClick = { editTaskType = opt; editTaskTypeExpanded = false })
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val recurrenceOptions = listOf("daily","weekly","monthly","yearly")
                            ExposedDropdownMenuBox(expanded = editRecurrenceTypeExpanded, onExpandedChange = { editRecurrenceTypeExpanded = !editRecurrenceTypeExpanded }) {
                                OutlinedTextField(
                                    value = editRecurrenceType ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Тип повторения") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editRecurrenceTypeExpanded) },
                                    modifier = Modifier.menuAnchor(),
                                    isError = recurrenceError != null,
                                    supportingText = { if (recurrenceError != null) Text(recurrenceError!!) }
                                )
                                DropdownMenu(expanded = editRecurrenceTypeExpanded, onDismissRequest = { editRecurrenceTypeExpanded = false }) {
                                    DropdownMenuItem(text = { Text("— Не задано —") }, onClick = { editRecurrenceType = null; editRecurrenceTypeExpanded = false })
                                    recurrenceOptions.forEach { opt ->
                                        DropdownMenuItem(text = { Text(opt) }, onClick = { editRecurrenceType = opt; editRecurrenceTypeExpanded = false })
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = editRecurrenceInterval, onValueChange = { editRecurrenceInterval = it }, label = { Text("Интервал повторения (число)") })
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = editIntervalDays, onValueChange = { editIntervalDays = it }, label = { Text("Дней интервала (число)") })
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
                            // Group dropdown from loaded groups
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
                                    try {
                                        withContext(Dispatchers.IO) { TasksApi().deleteTask(toDelete) }
                                        tasks = tasks.filter { it.id != toDelete }
                                    } catch (e: Exception) {
                                        Log.e("TasksScreen", "Delete error", e)
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
}


