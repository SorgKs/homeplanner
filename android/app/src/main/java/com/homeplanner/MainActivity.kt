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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
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

// View tabs for bottom navigation
enum class ViewTab { TODAY, ALL, SETTINGS, WEBSOCKET }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TasksScreen()
                }
            }
        }
    }
}

@Composable
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
            val (t, g) = withContext(Dispatchers.IO) {
                Pair(api.getTasks(activeOnly = false), groupsApi.getAll())
            }
            tasks = t
            groups = g
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
        // Normalize empty last_completed_at to null
        val lastCompletedAtValue = if (json.isNull("last_completed_at")) {
            null
        } else {
            val v = json.optString("last_completed_at", null)
            if (v.isNullOrEmpty()) null else v
        }
        return Task(
            id = json.getInt("id"),
            title = json.getString("title"),
            description = json.optString("description", null),
            taskType = json.getString("task_type"),
            recurrenceType = json.optString("recurrence_type", null),
            recurrenceInterval = if (json.isNull("recurrence_interval")) null else json.getInt("recurrence_interval"),
            intervalDays = if (json.isNull("interval_days")) null else json.getInt("interval_days"),
            nextDueDate = json.getString("next_due_date"),
            reminderTime = json.optString("reminder_time", null),
            groupId = if (json.isNull("group_id")) null else json.getInt("group_id"),
            isCompleted = lastCompletedAtValue != null,
            lastCompletedAt = lastCompletedAtValue
        )
    }

    // Update tasks list based on WebSocket event
    fun updateTasksFromEvent(action: String, taskJson: JSONObject?, taskId: Int?) {
        scope.launch {
            try {
                val currentList = tasks.toMutableList()
                when (action) {
                    "created", "updated", "completed", "uncompleted", "shown" -> {
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
                            currentList.removeAll { it.id == id }
                            tasks = currentList.toList()
                        }
                    }
                    else -> {
                        // Unknown action, do full refresh
                        refreshKey += 1
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TasksScreen", "updateTasksFromEvent error", e)
            }
        }
    }

    // WebSocket auto-refresh
    LaunchedEffect(Unit) {
        val client = OkHttpClient()
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
                scope.launch { appendLog("WS", "connection opened") }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                android.util.Log.e("TasksScreen", "WS connection failure", t)
                scope.launch { appendLog("WS", "connection failed: ${t.message}") }
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                android.util.Log.d("TasksScreen", "WS connection closed: code=$code, reason=$reason")
                scope.launch { appendLog("WS", "connection closed: code=$code") }
            }
        }
        val ws = client.newWebSocket(request, listener)
        // Keep reference until disposed by scope cancellation
        awaitCancellation()
        ws.close(1000, null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row {
            Text(text = "HomePlanner: Tasks", style = MaterialTheme.typography.titleLarge)
        }
        LaunchedEffect(refreshKey) {
            if (refreshKey > 0) {
                loadTasks()
            }
        }
        if (error != null) {
            Text(text = "Error: $error", color = MaterialTheme.colorScheme.error)
        } else {
            // content area
            val now = LocalDateTime.now()
            fun isToday(task: Task): Boolean {
                return try {
                    val due = LocalDateTime.parse(task.nextDueDate)
                    due.year == now.year && due.dayOfYear == now.dayOfYear
                } catch (_: Exception) { false }
            }

            val visibleTasks = when (selectedTab) {
                ViewTab.TODAY -> tasks.filter { isToday(it) }
                ViewTab.ALL -> tasks
                ViewTab.SETTINGS -> emptyList()
                ViewTab.WEBSOCKET -> emptyList()
            }

            if (selectedTab == ViewTab.SETTINGS) {
                Text(text = "Settings", style = MaterialTheme.typography.titleMedium)
                Text(text = "Version: ${BuildConfig.VERSION_NAME}")
            } else if (selectedTab == ViewTab.WEBSOCKET) {
                Text(text = "WebSocket Transactions", style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(wsLog) { line ->
                        Text(text = line)
                    }
                }
            } else if (visibleTasks.isEmpty()) {
                Text(text = "No tasks")
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(visibleTasks, key = { it.id }) { task ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Time on the left
                            fun formatTime(dt: String?): String {
                                if (dt == null) return "--:--"
                                return try {
                                    val ldt = LocalDateTime.parse(dt)
                                    ldt.format(DateTimeFormatter.ofPattern("HH:mm"))
                                } catch (_: DateTimeParseException) {
                                    val timePart = dt.substringAfter('T', dt)
                                    if (timePart.length >= 5) timePart.substring(0, 5) else "--:--"
                                }
                            }
                            val timeText = formatTime(task.reminderTime ?: task.nextDueDate)
                            Text(timeText)
                            Spacer(modifier = Modifier.width(12.dp))

                            // Title + group in the middle
                            val groupName = task.groupId?.let { groups[it] } ?: ""
                            val titleWithGroup = buildString {
                                append("• ").append(task.title)
                                if (groupName.isNotEmpty()) append(" (").append(groupName).append(")")
                            }
                            Text(
                                text = titleWithGroup,
                                modifier = Modifier.weight(1f)
                            )

                            // Checkbox on the right - computed based on task state
                            val checked = task.lastCompletedAt != null
                            Checkbox(checked = checked, onCheckedChange = { isChecked ->
                                Log.d("TasksScreen", "Checkbox clicked: task.id=${task.id}, currentChecked=$checked, newChecked=$isChecked")
                            if (isChecked && !checked) {
                                    scope.launch {
                                        try {
                                        appendLog("HTTP->", "POST /tasks/${task.id}/complete")
                                            val updated = withContext(Dispatchers.IO) { TasksApi().completeTask(task.id) }
                                            Log.d("TasksScreen", "Task completed: id=${updated.id}, lastCompletedAt=${updated.lastCompletedAt}")
                                            // Create completely new list instance to trigger recomposition
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
                                            Log.d("TasksScreen", "Task uncompleted: id=${updated.id}, lastCompletedAt=${updated.lastCompletedAt}")
                                            // Create completely new list instance to trigger recomposition
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
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom navigation
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { selectedTab = ViewTab.TODAY; refreshKey += 1 }, modifier = Modifier.weight(1f)) {
                val text = if (selectedTab == ViewTab.TODAY) "[Сегодня]" else "Сегодня"
                Text(text)
            }
            TextButton(onClick = { selectedTab = ViewTab.ALL; refreshKey += 1 }, modifier = Modifier.weight(1f)) {
                val text = if (selectedTab == ViewTab.ALL) "[Все задачи]" else "Все задачи"
                Text(text)
            }
            TextButton(onClick = { selectedTab = ViewTab.SETTINGS }, modifier = Modifier.weight(1f)) {
                val text = if (selectedTab == ViewTab.SETTINGS) "[Настройки]" else "Настройки"
                Text(text)
            }
            TextButton(onClick = { selectedTab = ViewTab.WEBSOCKET }, modifier = Modifier.weight(1f)) {
                val text = if (selectedTab == ViewTab.WEBSOCKET) "[WebSocket]" else "WebSocket"
                Text(text)
            }
        }
    }
}


