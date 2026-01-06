package com.homeplanner.api

import com.homeplanner.BuildConfig
import com.homeplanner.model.Task
import com.homeplanner.debug.BinaryLogger
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
        android.util.Log.i("ServerTaskApi", "getTasksServer: called with enabledOnly=$enabledOnly")
        val url = buildString {
            append(baseUrl).append("/tasks/")
            if (enabledOnly) append("?enabled_only=true")
        }
        android.util.Log.i("ServerTaskApi", "getTasksServer: url=$url")
        // getTasks: [STEP 1] Выполнение HTTP запроса к серверу
        BinaryLogger.getInstance()?.log(201u, emptyList<Any>(), 45)
        val request = okhttp3.Request.Builder()
            .url(url)
            .applyUserCookie()
            .build()
        executeAsync(request).use { response ->
            // Бросаем исключение только для 500, остальные коды считаем успешными
            // getTasks: [STEP 2] Получен HTTP ответ
            BinaryLogger.getInstance()?.log(202u, emptyList<Any>(), 45)
            if (response.code == 500) {
                val errorBody = response.body?.string() ?: "No error body"
                // Ошибка синхронизации: ошибка сервера 500
                BinaryLogger.getInstance()?.log(4u, emptyList<Any>(), 45)
                throw IllegalStateException("HTTP ${response.code}: $errorBody")
            }
            val body = response.body?.string() ?: "[]"
            // getTasks: [STEP 3] Получено тело ответа
            BinaryLogger.getInstance()?.log(203u, emptyList<Any>(), 45)
            val array = JSONArray(body)
            // getTasks: [STEP 4] Распарсен JSON массив
            BinaryLogger.getInstance()?.log(204u, emptyList<Any>(), 45)
            val result = ArrayList<Task>(array.length())
            for (i in 0 until array.length()) {
                try {
                    val obj = array.getJSONObject(i)
                    val task = obj.toTask()
                    result.add(task)
                    // getTasks: [STEP 4] Успешно распарсена задача
                    BinaryLogger.getInstance()?.log(205u, listOf<Any>(task.id, 45), 45)
                } catch (e: Exception) {
                    // Исключение: ожидалось %wait%, фактически %fact%
                    BinaryLogger.getInstance()?.log(
                        91u, listOf<Any>(e.message ?: "Unknown error",e::class.simpleName ?: "Unknown", 45), 45
                    )
                    // Пропускаем задачу с ошибкой парсинга, продолжаем обработку остальных
                }
            }
            // getTasks: [STEP 5] Успешно распарсены задачи
            BinaryLogger.getInstance()?.log(206u, emptyList<Any>(), 45)
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
        BinaryLogger.getInstance()?.log(207u, emptyList<Any>(), 45)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(json.toRequestBody(mediaType))
            .applyUserCookie()
            .build()
        // Отправка POST запроса
        BinaryLogger.getInstance()?.log(208u, emptyList<Any>(), 45)
        try {
            executeAsync(request).use { response ->
        // Получен ответ
        BinaryLogger.getInstance()?.log(209u, emptyList<Any>(), 45)
            // Бросаем исключение только для 500, остальные коды считаем успешными
            if (response.code == 500) {
                val errorBody = response.body?.string() ?: "No error body"
                // Ошибка синхронизации: ошибка сервера 500
                BinaryLogger.getInstance()?.log(4u, emptyList<Any>(), 45)
                throw IllegalStateException("HTTP ${response.code}: $errorBody")
            }
            val body = response.body?.string() ?: throw IllegalStateException("Empty body")
            // Получено тело ответа
            BinaryLogger.getInstance()?.log(210u, emptyList<Any>(), 45)
            val obj = JSONObject(body)
            obj.toTask()
        }
        } catch (e: Exception) {
                // Исключение: ожидалось %wait%, фактически %fact%
                BinaryLogger.getInstance()?.log(
                    91u, listOf<Any>(e.message ?: "Unknown error",e::class.simpleName ?: "Unknown", 45), 45
                )
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
        BinaryLogger.getInstance()?.log(211u, emptyList<Any>(), 45)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body: RequestBody = json.toRequestBody(mediaType)
        val request = okhttp3.Request.Builder()
            .url(url)
            .put(body)
            .applyUserCookie()
            .build()
        // Отправка PATCH запроса
        BinaryLogger.getInstance()?.log(212u, emptyList<Any>(), 45)
        try {
            executeAsync(request).use { response ->
                // Получен ответ
                BinaryLogger.getInstance()?.log(209u, emptyList<Any>(), 45)
                // Бросаем исключение только для 500, остальные коды считаем успешными
                if (response.code == 500) {
                    val errorBody = response.body?.string() ?: "No error body"
                    // Ошибка синхронизации: ошибка сервера 500
                    BinaryLogger.getInstance()?.log(4u, emptyList<Any>(), 45)
                    throw IllegalStateException("HTTP ${response.code}: $errorBody")
                }
                val resp = response.body?.string() ?: throw IllegalStateException("Empty body")
                // Получено тело ответа
                BinaryLogger.getInstance()?.log(210u, emptyList<Any>(), 45)
                val obj = JSONObject(resp)
                obj.toTask()
            }
        } catch (e: Exception) {
                // Исключение: ожидалось %wait%, фактически %fact%
                BinaryLogger.getInstance()?.log(
                    91u, listOf<Any>(e.message ?: "Unknown error",e::class.simpleName ?: "Unknown", 45), 45
                )
            throw e
        }
    }
}