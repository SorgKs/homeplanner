package com.homeplanner.api

import com.homeplanner.BuildConfig
import com.homeplanner.model.Task
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * API для работы с задачами на сервере.
 * Наследует базовую функциональность от ServerApiBase.
 */
open class ServerTaskApi(
    httpClient: OkHttpClient = createHttpClientWithTimeouts(),
    baseUrl: String = BuildConfig.API_BASE_URL,
    selectedUserId: Int? = null,
) : ServerApiBase(httpClient, baseUrl, selectedUserId) {

    suspend fun getTasksServer(enabledOnly: Boolean = true): Result<List<Task>> = runCatching {
        android.util.Log.i("ServerTaskApi", "getTasksServer: called with enabledOnly=$enabledOnly, baseUrl=$baseUrl")
        val url = buildString {
            append(baseUrl).append("/tasks/")
            if (enabledOnly) append("?enabled_only=true")
        }
        android.util.Log.i("ServerTaskApi", "getTasksServer: sending request to url=$url")
        // getTasks: [STEP 1] Выполнение HTTP запроса к серверу
        val request = okhttp3.Request.Builder()
            .url(url)
            .applyUserCookie()
            .build()
        executeAsync(request).use { response ->
            android.util.Log.i("ServerTaskApi", "getTasksServer: received response code=${response.code}, message=${response.message}")
            // Бросаем исключение только для 500, остальные коды считаем успешными
            // getTasks: [STEP 2] Получен HTTP ответ
            if (response.code == 500) {
                val errorBody = response.body?.string() ?: "No error body"
                android.util.Log.e("ServerTaskApi", "getTasksServer: server error 500: $errorBody")
                // Ошибка синхронизации: ошибка сервера 500
                throw IllegalStateException("HTTP ${response.code}: $errorBody")
            }
            val body = response.body?.string() ?: "[]"
            android.util.Log.i("ServerTaskApi", "getTasksServer: response body length=${body.length}")
            // getTasks: [STEP 3] Получено тело ответа
            val array = JSONArray(body)
            // getTasks: [STEP 4] Распарсен JSON массив
            val result = ArrayList<Task>(array.length())
            for (i in 0 until array.length()) {
                try {
                    val obj = array.getJSONObject(i)
                    val task = obj.toTask()
                    result.add(task)
                    // getTasks: [STEP 4] Успешно распарсена задача
                } catch (e: Exception) {
                    // Пропускаем задачу с ошибкой парсинга, продолжаем обработку остальных
                }
            }
            android.util.Log.i("ServerTaskApi", "getTasksServer: parsed ${result.size} tasks from server")
            // getTasks: [STEP 5] Успешно распарсены задачи
            result
        }
    }

    suspend fun getTodayTaskIds(): List<Int> {
        val url = "$baseUrl/tasks/today/ids"
        val request = okhttp3.Request.Builder()
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
            put("enabled", task.enabled)
            put("completed", task.completed)
            if (assignedUserIds.isNotEmpty()) {
                put("assigned_user_ids", JSONArray(assignedUserIds))
            }
        }.toString()
        // Создание задачи
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(json.toRequestBody(mediaType))
            .applyUserCookie()
            .build()
        // Отправка POST запроса
        try {
            executeAsync(request).use { response ->
        // Получен ответ
            // Бросаем исключение только для 500, остальные коды считаем успешными
            if (response.code == 500) {
                val errorBody = response.body?.string() ?: "No error body"
                // Ошибка синхронизации: ошибка сервера 500
                throw IllegalStateException("HTTP ${response.code}: $errorBody")
            }
            val body = response.body?.string() ?: throw IllegalStateException("Empty body")
            // Получено тело ответа
            val obj = JSONObject(body)
            obj.toTask()
        }
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun completeTaskServer(taskId: Int): Result<Task> = runCatching {
        val url = "$baseUrl/tasks/$taskId/complete"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = okhttp3.Request.Builder()
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
        val request = okhttp3.Request.Builder()
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
        val request = okhttp3.Request.Builder()
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
            put("enabled", task.enabled)
            put("completed", task.completed)
            if (assignedUserIds.isNotEmpty()) {
                put("assigned_user_ids", JSONArray(assignedUserIds))
            } else {
                put("assigned_user_ids", JSONArray())
            }
        }.toString()
        // Обновление задачи
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body: RequestBody = json.toRequestBody(mediaType)
        val request = okhttp3.Request.Builder()
            .url(url)
            .put(body)
            .applyUserCookie()
            .build()
        // Отправка PATCH запроса
        try {
            executeAsync(request).use { response ->
                // Получен ответ
                // Бросаем исключение только для 500, остальные коды считаем успешными
                if (response.code == 500) {
                    val errorBody = response.body?.string() ?: "No error body"
                    // Ошибка синхронизации: ошибка сервера 500
                    throw IllegalStateException("HTTP ${response.code}: $errorBody")
                }
                val resp = response.body?.string() ?: throw IllegalStateException("Empty body")
                // Получено тело ответа
                val obj = JSONObject(resp)
                obj.toTask()
            }
        } catch (e: Exception) {
            throw e
        }
    }
}