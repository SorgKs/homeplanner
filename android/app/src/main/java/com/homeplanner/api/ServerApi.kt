package com.homeplanner.api

import com.homeplanner.BuildConfig
import com.homeplanner.model.Task
import com.homeplanner.database.entity.SyncQueueItem
import com.homeplanner.debug.BinaryLogger
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * API для работы с сервером.
 * 
 * Все методы выполняют HTTP запросы к серверу и не работают с локальным хранилищем.
 * Не используется в UI коде - только для синхронизации через SyncService.
 */
class ServerApi(
    private val httpClient: OkHttpClient = createHttpClientWithTimeouts(),
    val baseUrl: String = BuildConfig.API_BASE_URL,
    private val selectedUserId: Int? = null,
) {
    
    companion object {
        /**
         * Создает OkHttpClient с настроенными таймаутами для предотвращения зависаний.
         * 
         * Таймауты:
         * - connectTimeout: 10 секунд - время на установку соединения
         * - readTimeout: 30 секунд - время на чтение ответа от сервера
         * - writeTimeout: 30 секунд - время на отправку запроса
         */
        private fun createHttpClientWithTimeouts(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }

    private fun Request.Builder.applyUserCookie(): Request.Builder {
        if (selectedUserId != null) {
            header("Cookie", "hp.selectedUserId=$selectedUserId")
        }
        return this
    }
    
    /**
     * Выполняет HTTP запрос асинхронно через корутины.
     * 
     * Использует suspendCancellableCoroutine для преобразования callback-based API OkHttp
     * в suspend функцию, что позволяет выполнять запросы неблокирующим образом.
     * 
     * @param request HTTP запрос для выполнения
     * @return Response от сервера
     * @throws IOException при ошибках сети или таймаутах
     */
    private suspend fun executeAsync(request: Request): Response {
        return suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            
            continuation.invokeOnCancellation {
                call.cancel()
            }
            
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
                
                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            })
        }
    }

    suspend fun getTasksServer(activeOnly: Boolean = true): Result<List<Task>> = runCatching {
        val url = buildString {
            append(baseUrl).append("/tasks/")
            if (activeOnly) append("?active_only=true")
        }
        // getTasks: [STEP 1] Выполнение HTTP запроса к серверу
        BinaryLogger.getInstance()?.log(201u, emptyList())
        val request = Request.Builder()
            .url(url)
            .applyUserCookie()
            .build()
        executeAsync(request).use { response ->
            // Бросаем исключение только для 500, остальные коды считаем успешными
            // getTasks: [STEP 2] Получен HTTP ответ
            BinaryLogger.getInstance()?.log(202u, emptyList())
            if (response.code == 500) {
                val errorBody = response.body?.string() ?: "No error body"
                // Ошибка синхронизации: ошибка сервера 500
                BinaryLogger.getInstance()?.log(4u, emptyList())
                throw IllegalStateException("HTTP ${response.code}: $errorBody")
            }
            val body = response.body?.string() ?: "[]"
            // getTasks: [STEP 3] Получено тело ответа
            BinaryLogger.getInstance()?.log(203u, emptyList())
            val array = JSONArray(body)
            // getTasks: [STEP 4] Распарсен JSON массив
            BinaryLogger.getInstance()?.log(204u, emptyList())
            val result = ArrayList<Task>(array.length())
            for (i in 0 until array.length()) {
                try {
                    val obj = array.getJSONObject(i)
                    val task = obj.toTask()
                    result.add(task)
                    // getTasks: [STEP 4] Успешно распарсена задача
                    BinaryLogger.getInstance()?.log(205u, listOf(task.id))
                } catch (e: Exception) {
                    // Исключение: ожидалось %wait%, фактически %fact%
                    BinaryLogger.getInstance()?.log(
                        91u,
                        listOf(e.message ?: "Unknown error", e::class.simpleName ?: "Unknown")
                    )
                    // Пропускаем задачу с ошибкой парсинга, продолжаем обработку остальных
                }
            }
            // getTasks: [STEP 5] Успешно распарсены задачи
            BinaryLogger.getInstance()?.log(206u, emptyList())
            result
        }
    }

    suspend fun getTodayTaskIds(): List<Int> {
        val url = "$baseUrl/tasks/today/ids"
        val request = Request.Builder()
            .url(url)
            .applyUserCookie()
            .build()
        executeAsync(request).use { response ->
            // Бросаем исключение только для 500, остальные коды считаем успешными
            if (response.code == 500) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body?.string() ?: "[]"
            val array = JSONArray(body)
            val result = ArrayList<Int>(array.length())
            for (i in 0 until array.length()) {
                result.add(array.getInt(i))
            }
            return result
        }
    }

    suspend fun createTaskServer(task: Task, assignedUserIds: List<Int> = emptyList()): Result<Task> = runCatching {
        val url = "$baseUrl/tasks/"
        val json = JSONObject().apply {
            put("title", task.title)
            put("description", task.description)
            put("task_type", task.taskType)
            put("recurrence_type", task.recurrenceType)
            put("recurrence_interval", task.recurrenceInterval)
            put("interval_days", task.intervalDays)
            put("reminder_time", task.reminderTime)
            put("group_id", task.groupId)
            put("active", task.active)
            put("completed", task.completed)
            if (assignedUserIds.isNotEmpty()) {
                put("assigned_user_ids", JSONArray(assignedUserIds))
            }
        }.toString()
        // Создание задачи
        BinaryLogger.getInstance()?.log(207u, emptyList())
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(mediaType))
            .applyUserCookie()
            .build()
        // Отправка POST запроса
        BinaryLogger.getInstance()?.log(208u, emptyList())
        try {
            executeAsync(request).use { response ->
        // Получен ответ
        BinaryLogger.getInstance()?.log(209u, emptyList())
            // Бросаем исключение только для 500, остальные коды считаем успешными
            if (response.code == 500) {
                val errorBody = response.body?.string() ?: "No error body"
                // Ошибка синхронизации: ошибка сервера 500
                BinaryLogger.getInstance()?.log(4u, emptyList())
                throw IllegalStateException("HTTP ${response.code}: $errorBody")
            }
            val body = response.body?.string() ?: throw IllegalStateException("Empty body")
            // Получено тело ответа
            BinaryLogger.getInstance()?.log(210u, emptyList())
            val obj = JSONObject(body)
            obj.toTask()
        }
        } catch (e: Exception) {
                // Исключение: ожидалось %wait%, фактически %fact%
                BinaryLogger.getInstance()?.log(
                    91u,
                    listOf(e.message ?: "Unknown error", e::class.simpleName ?: "Unknown")
                )
            throw e
        }
    }

    suspend fun completeTaskServer(taskId: Int): Result<Task> = runCatching {
        val url = "$baseUrl/tasks/$taskId/complete"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post("{}".toRequestBody(mediaType))
            .applyUserCookie()
            .build()
        executeAsync(request).use { response ->
            // Бросаем исключение только для 500, остальные коды считаем успешными
            if (response.code == 500) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IllegalStateException("Empty body")
            val obj = JSONObject(body)
            obj.toTask()
        }
    }

    suspend fun uncompleteTaskServer(taskId: Int): Result<Task> = runCatching {
        val url = "$baseUrl/tasks/$taskId/uncomplete"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post("{}".toRequestBody(mediaType))
            .applyUserCookie()
            .build()
        executeAsync(request).use { response ->
            // Бросаем исключение только для 500, остальные коды считаем успешными
            if (response.code == 500) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IllegalStateException("Empty body")
            val obj = JSONObject(body)
            obj.toTask()
        }
    }

    suspend fun deleteTaskServer(taskId: Int): Result<Unit> = runCatching {
        val url = "$baseUrl/tasks/$taskId"
        val request = Request.Builder()
            .url(url)
            .delete()
            .applyUserCookie()
            .build()
        executeAsync(request).use { response ->
            // Бросаем исключение только для 500, остальные коды считаем успешными
            if (response.code == 500) throw IllegalStateException("HTTP ${response.code}")
        }
        Unit
    }

    suspend fun updateTaskServer(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Result<Task> = runCatching {
        val url = "$baseUrl/tasks/$taskId"
        val json = JSONObject().apply {
            put("title", task.title)
            put("description", task.description)
            put("task_type", task.taskType)
            put("recurrence_type", task.recurrenceType)
            put("recurrence_interval", task.recurrenceInterval)
            put("interval_days", task.intervalDays)
            put("reminder_time", task.reminderTime)
            put("group_id", task.groupId)
            put("active", task.active)
            put("completed", task.completed)
            if (assignedUserIds.isNotEmpty()) {
                put("assigned_user_ids", JSONArray(assignedUserIds))
            } else {
                put("assigned_user_ids", JSONArray())
            }
        }.toString()
        // Обновление задачи
        BinaryLogger.getInstance()?.log(211u, emptyList())
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body: RequestBody = json.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .put(body)
            .applyUserCookie()
            .build()
        // Отправка PATCH запроса
        BinaryLogger.getInstance()?.log(212u, emptyList())
        try {
            executeAsync(request).use { response ->
                // Получен ответ
                BinaryLogger.getInstance()?.log(209u, emptyList())
                // Бросаем исключение только для 500, остальные коды считаем успешными
                if (response.code == 500) {
                    val errorBody = response.body?.string() ?: "No error body"
                    // Ошибка синхронизации: ошибка сервера 500
                    BinaryLogger.getInstance()?.log(4u, emptyList())
                    throw IllegalStateException("HTTP ${response.code}: $errorBody")
                }
                val resp = response.body?.string() ?: throw IllegalStateException("Empty body")
                // Получено тело ответа
                BinaryLogger.getInstance()?.log(210u, emptyList())
                val obj = JSONObject(resp)
                obj.toTask()
            }
        } catch (e: Exception) {
                // Исключение: ожидалось %wait%, фактически %fact%
                BinaryLogger.getInstance()?.log(
                    91u,
                    listOf(e.message ?: "Unknown error", e::class.simpleName ?: "Unknown")
                )
            throw e
        }
    }

    /**
     * Отправка батча операций очереди синхронизации на сервер.
     *
     * Серверный эндпоинт: POST /tasks/sync-queue
     * Тело: { "operations": [ { "operation": "...", "timestamp": "...", "task_id": ..., "payload": { ... } }, ... ] }
     * Ответ: массив задач в актуальном состоянии.
     */
    suspend fun syncQueueServer(queueItems: List<SyncQueueItem>): Result<List<Task>> = runCatching {
        if (queueItems.isEmpty()) return@runCatching emptyList()

        val url = "$baseUrl/tasks/sync-queue"
        val opsArray = JSONArray()

        // Сортируем по timestamp на клиенте для предсказуемости
        val sorted = queueItems.sortedBy { it.timestamp }
        for (item in sorted) {
            val obj = JSONObject().apply {
                put("operation", item.operation)
                put("timestamp", java.time.Instant.ofEpochMilli(item.timestamp).toString())
                if (item.entityId != null) {
                    put("task_id", item.entityId)
                }
                if (item.payload != null) {
                    // payload уже хранится как JSON-строка с полями Task
                    put("payload", JSONObject(item.payload))
                }
            }
            opsArray.put(obj)
        }

        val root = JSONObject().apply {
            put("operations", opsArray)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = root.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .applyUserCookie()
            .build()

        executeAsync(request).use { response ->
            // Бросаем исключение только для 500, остальные коды считаем успешными
            // Сервер обрабатывает конфликты самостоятельно
            if (response.code == 500) {
                val errorBody = response.body?.string() ?: "No error body"
                val errorCode = response.code
                // Если сервер вернул 500, это реальная ошибка
                throw IllegalStateException("HTTP $errorCode: $errorBody")
            }
            val respBody = response.body?.string() ?: "[]"
            val array = JSONArray(respBody)
            val result = ArrayList<Task>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(obj.toTask())
            }
            result
        }
    }
}

private fun JSONObject.toTask(): Task {
    // Извлекаем id задачи для логирования
    val taskId = if (has("id") && !isNull("id")) getInt("id") else -1
    
    // Проверяем reminder_time: может быть строкой или null в JSON
    val reminderValue = if (isNull("reminder_time")) {
        // Задача имеет null reminder_time
        BinaryLogger.getInstance()?.log(213u, listOf(taskId))
        throw IllegalStateException("Missing reminder_time in task payload: $this")
    } else {
        val reminderStr = optString("reminder_time", null)
        if (reminderStr.isNullOrEmpty()) {
            // Задача имеет пустой reminder_time
            BinaryLogger.getInstance()?.log(214u, listOf(taskId))
            throw IllegalStateException("Empty reminder_time in task payload: $this")
        } else {
            reminderStr
        }
    }
    val activeValue = if (isNull("active")) true else getBoolean("active")
    val completedValue = if (isNull("completed")) false else getBoolean("completed")
    
    // Extract assigned_user_ids from JSON array
    val assignedUserIds = mutableListOf<Int>()
    if (has("assigned_user_ids") && !isNull("assigned_user_ids")) {
        val idsArray = getJSONArray("assigned_user_ids")
        for (i in 0 until idsArray.length()) {
            assignedUserIds.add(idsArray.getInt(i))
        }
    }
    
    return Task(
        id = getInt("id"),
        title = getString("title"),
        description = optString("description", null),
        taskType = getString("task_type"),
        recurrenceType = optString("recurrence_type", null),
        recurrenceInterval = if (isNull("recurrence_interval")) null else getInt("recurrence_interval"),
        intervalDays = if (isNull("interval_days")) null else getInt("interval_days"),
        reminderTime = reminderValue,
        groupId = if (isNull("group_id")) null else getInt("group_id"),
        active = activeValue,
        completed = completedValue,
        assignedUserIds = assignedUserIds,
        updatedAt = if (isNull("updated_at")) System.currentTimeMillis() else getLong("updated_at"),
        lastAccessed = System.currentTimeMillis(),
        lastShownAt = if (isNull("last_shown_at")) null else getLong("last_shown_at"),
        createdAt = if (isNull("created_at")) System.currentTimeMillis() else getLong("created_at")
    )
}
