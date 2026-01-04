package com.homeplanner.sync

import android.content.Context
import android.util.Log
import com.homeplanner.api.LocalApi
import com.homeplanner.api.ServerApi
import com.homeplanner.database.AppDatabase
import com.homeplanner.database.dao.TaskCacheDao
import com.homeplanner.database.entity.TaskCache
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Сервис для управления WebSocket-соединением и обработки сообщений в реальном времени.
 * 
 * Особенности:
 * - Автоматическое переподключение с экспоненциальной задержкой
 * - Обработка сообщений task_update (created, updated, deleted, completed, uncompleted, shown)
 * - Обновление локального кэша через OfflineRepository
 * - Вызов ReminderScheduler после обновлений
 * - Интеграция с LocalApi для оптимистичных обновлений
 */
class WebSocketService(
    private val context: Context,
    private val localApi: LocalApi,
    private val serverApi: ServerApi
) {
    companion object {
        private const val TAG = "WebSocketService"
        private const val WEBSOCKET_PATH = "/api/v0.2/tasks/stream"
        private const val MAX_RECONNECT_DELAY_MS = 60_000L
        private const val INITIAL_RECONNECT_DELAY_MS = 2_000L
    }
    
    private var webSocket: WebSocket? = null
    private var isConnected = AtomicBoolean(false)
    private var isConnecting = AtomicBoolean(false)
    private var reconnectDelay = INITIAL_RECONNECT_DELAY_MS
    private var reconnectJob: Job? = null
    private var client: OkHttpClient? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    /**
     * Запускает WebSocket-соединение.
     * Если соединение уже активно, ничего не делает.
     */
    fun start() {
        if (isConnected.get() || isConnecting.get()) {
            Log.d(TAG, "WebSocket already active or connecting")
            return
        }
        
        scope.launch {
            connectWebSocket()
        }
    }
    
    /**
     * Останавливает WebSocket-соединение.
     * Закрывает соединение и отменяет попытки переподключения.
     */
    fun stop() {
        scope.launch {
            isConnecting.set(false)
            isConnected.set(false)
            reconnectJob?.cancel()
            reconnectJob = null
            webSocket?.close(1000, "Service stopped")
            webSocket = null
            client?.dispatcher?.executorService?.shutdown()
            client = null
            Log.d(TAG, "WebSocket stopped")
        }
    }
    
    /**
     * Проверяет, активно ли WebSocket-соединение.
     */
    fun isConnected(): Boolean = isConnected.get()
    
    /**
     * Проверяет, выполняется ли попытка подключения.
     */
    fun isConnecting(): Boolean = isConnecting.get()
    
    /**
     * Выполняет подключение к WebSocket.
     * При неудаче запускает переподключение с экспоненциальной задержкой.
     */
    private suspend fun connectWebSocket() {
        if (isConnecting.getAndSet(true)) {
            Log.d(TAG, "Connection already in progress")
            return
        }
        
        try {
            val baseUrl = serverApi.baseUrl ?: throw IllegalArgumentException("Server API base URL is null")
            val wsUrl = baseUrl.replace("http", "ws") + WEBSOCKET_PATH

            Log.d(TAG, "Connecting to WebSocket: $wsUrl")
            Log.d(TAG, "Base URL: $baseUrl, WebSocket path: $WEBSOCKET_PATH")
            
            val okHttpClient = OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build()
            client = okHttpClient
            
            val request = Request.Builder()
                .url(wsUrl)
                .build()
            
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connection opened")
                    isConnected.set(true)
                    isConnecting.set(false)
                    reconnectDelay = INITIAL_RECONNECT_DELAY_MS
                    this@WebSocketService.webSocket = webSocket
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "WebSocket message received: $text")
                    scope.launch {
                        handleMessage(text)
                    }
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: code=$code, reason=$reason")
                    isConnected.set(false)
                    isConnecting.set(false)
                    reconnectIfNeeded()
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: code=$code, reason=$reason")
                    isConnected.set(false)
                    isConnecting.set(false)
                    reconnectIfNeeded()
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket connection failed", t)
                    isConnected.set(false)
                    isConnecting.set(false)
                    reconnectIfNeeded()
                }
            }
            
            okHttpClient.newWebSocket(request, listener)
            okHttpClient.dispatcher.executorService.shutdown()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create WebSocket connection", e)
            isConnecting.set(false)
            reconnectIfNeeded()
        }
    }
    
    /**
     * Обрабатывает входящее сообщение от WebSocket.
     * Поддерживает типы: task_update с действиями created, updated, deleted, completed, uncompleted, shown.
     */
    private suspend fun handleMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.optString("type", "")
            
            when (type) {
                "task_update" -> {
                    val action = json.getString("action")
                    val taskId = if (json.has("task_id")) json.getInt("task_id") else null
                    val taskJson = if (json.has("task")) json.getJSONObject("task") else null
                    
                    Log.d(TAG, "Processing task_update: action=$action, taskId=$taskId")
                    
                    when (action) {
                        "created", "updated" -> {
                            if (taskJson != null) {
                                val task = parseTaskFromJson(taskJson)
                                handleTaskUpdate(task)
                            }
                        }
                        "completed", "uncompleted", "shown" -> {
                            if (taskJson != null) {
                                val task = parseTaskFromJson(taskJson)
                                handleTaskStatusUpdate(task)
                            }
                        }
                        "deleted" -> {
                            taskId?.let { id ->
                                handleTaskDelete(id)
                            }
                        }
                        else -> {
                            Log.w(TAG, "Unknown task_update action: $action")
                        }
                    }
                }
                "hash_check" -> {
                    if (json.has("data_hash")) {
                        val serverHash = json.getString("data_hash")
                        handleHashCheck(serverHash)
                    }
                }
                else -> {
                    Log.d(TAG, "Ignoring message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing WebSocket message", e)
        }
    }
    
    /**
     * Обрабатывает обновление задачи (created, updated).
     * Сохраняет задачу в локальный кэш через LocalApi.
     */
    private suspend fun handleTaskUpdate(task: Task) {
        try {
            // Используем LocalApi для оптимистичного обновления
            localApi.updateTaskLocal(task.id, task, task.assignedUserIds)
            Log.d(TAG, "Task updated via LocalApi: id=${task.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update task via LocalApi", e)
        }
    }
    
    /**
     * Обрабатывает изменение статуса задачи (completed, uncompleted, shown).
     * Обновляет локальный кэш через OfflineRepository.
     */
    private suspend fun handleTaskStatusUpdate(task: Task) {
        try {
            val offlineRepository = OfflineRepository(AppDatabase.getDatabase(context), context)
            val cachedTask = offlineRepository.getTaskFromCache(task.id)

            if (cachedTask != null) {
                val updatedTask = cachedTask.copy(
                    completed = task.completed,
                    active = task.active,
                    reminderTime = task.reminderTime,
                    title = task.title
                )
                offlineRepository.saveTasksToCache(listOf(updatedTask))
                Log.d(TAG, "Task status updated in cache: id=${task.id}, completed=${task.completed}")
            } else {
                // Задача не найдена в кэше - сохраняем как новую
                offlineRepository.saveTasksToCache(listOf(task))
                Log.d(TAG, "Task not found in cache, saved as new: id=${task.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update task status in cache", e)
        }
    }
    
    /**
     * Обрабатывает удаление задачи.
     * Удаляет задачу из локального кэша через OfflineRepository.
     */
    private suspend fun handleTaskDelete(taskId: Int) {
        try {
            val offlineRepository = OfflineRepository(AppDatabase.getDatabase(context), context)
            offlineRepository.deleteTaskFromCache(taskId)
            Log.d(TAG, "Task deleted from cache: id=$taskId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete task from cache", e)
        }
    }
    
    /**
     * Обрабатывает проверку хэша данных.
     * При несовпадении хэшей выполняет полную перезагрузку задач.
     */
    private suspend fun handleHashCheck(serverHash: String) {
        try {
            val offlineRepository = OfflineRepository(AppDatabase.getDatabase(context), context)
            val cachedTasks = offlineRepository.loadTasksFromCache()
            val localHash = calculateTasksHash(cachedTasks)

            if (localHash != serverHash) {
                Log.w(TAG, "Hash mismatch detected. Local: ${localHash.take(16)}..., Server: ${serverHash.take(16)}...")

                // Загружаем задачи с сервера для синхронизации
                val serverTasks = serverApi.getTasksServer().getOrThrow()
                offlineRepository.saveTasksToCache(serverTasks)

                Log.d(TAG, "Tasks synchronized from server: ${serverTasks.size} tasks")
            } else {
                Log.d(TAG, "Hash check passed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle hash check", e)
        }
    }
    
    /**
     * Запускает переподключение с экспоненциальной задержкой.
     */
    private fun reconnectIfNeeded() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(reconnectDelay)
            if (reconnectDelay < MAX_RECONNECT_DELAY_MS) {
                reconnectDelay *= 2
            }
            connectWebSocket()
        }
    }
    
    /**
     * Парсит задачу из JSON-объекта.
     * Использует ту же логику, что и MainActivity.
     */
    private fun parseTaskFromJson(json: JSONObject): Task {
        val reminderValue = json.optString("reminder_time", null)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Отсутствует reminder_time в ответе сервера: $json")
        val activeValue = if (json.isNull("active")) true else json.getBoolean("active")
        val completedValue = if (json.isNull("completed")) false else json.getBoolean("completed")
        
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
            updatedAt = if (json.isNull("updated_at")) System.currentTimeMillis() else json.getLong("updated_at"),
            lastAccessed = System.currentTimeMillis(),
            lastShownAt = if (json.isNull("last_shown_at")) null else json.getLong("last_shown_at"),
            createdAt = if (json.isNull("created_at")) System.currentTimeMillis() else json.getLong("created_at")
        )
    }
    
    /**
     * Вычисляет SHA-256 хэш списка задач для проверки целостности данных.
     */
    private fun calculateTasksHash(tasks: List<Task>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sortedTasks = tasks.sortedBy { it.id }
        val data = sortedTasks.joinToString("|") { task ->
            "${task.id}:${task.title}:${task.reminderTime}:${task.completed}:${task.active}"
        }
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
