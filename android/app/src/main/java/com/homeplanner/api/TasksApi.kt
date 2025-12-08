package com.homeplanner.api

import android.util.Log
import com.homeplanner.BuildConfig
import com.homeplanner.model.Task
import com.homeplanner.database.entity.SyncQueueItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Simple API client for tasks. */
class TasksApi(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val selectedUserId: Int? = null,
) {

    private fun Request.Builder.applyUserCookie(): Request.Builder {
        if (selectedUserId != null) {
            header("Cookie", "hp.selectedUserId=$selectedUserId")
        }
        return this
    }

    fun getTasks(activeOnly: Boolean = true): List<Task> {
        val url = buildString {
            append(baseUrl).append("/tasks/")
            if (activeOnly) append("?active_only=true")
        }
        val request = Request.Builder()
            .url(url)
            .applyUserCookie()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body?.string() ?: "[]"
            val array = JSONArray(body)
            val result = ArrayList<Task>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(obj.toTask())
            }
            return result
        }
    }

    fun getTodayTaskIds(): List<Int> {
        val url = "$baseUrl/tasks/today/ids"
        val request = Request.Builder()
            .url(url)
            .applyUserCookie()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body?.string() ?: "[]"
            val array = JSONArray(body)
            val result = ArrayList<Int>(array.length())
            for (i in 0 until array.length()) {
                result.add(array.getInt(i))
            }
            return result
        }
    }

    fun createTask(task: Task, assignedUserIds: List<Int> = emptyList()): Task {
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
        Log.d("TasksApi", "Creating task: url=$url, json=$json")
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(mediaType))
            .applyUserCookie()
            .build()
        Log.d("TasksApi", "Sending POST request to: $url")
        try {
            httpClient.newCall(request).execute().use { response ->
                Log.d("TasksApi", "Response received: code=${response.code}, success=${response.isSuccessful}")
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No error body"
                    Log.e("TasksApi", "HTTP error: code=${response.code}, body=$errorBody")
                    throw IllegalStateException("HTTP ${response.code}: $errorBody")
                }
                val body = response.body?.string() ?: throw IllegalStateException("Empty body")
                Log.d("TasksApi", "Response body: $body")
                val obj = JSONObject(body)
                return obj.toTask()
            }
        } catch (e: Exception) {
            Log.e("TasksApi", "Error creating task", e)
            throw e
        }
    }

    fun completeTask(taskId: Int): Task {
        val url = "$baseUrl/tasks/$taskId/complete"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post("{}".toRequestBody(mediaType))
            .applyUserCookie()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IllegalStateException("Empty body")
            val obj = JSONObject(body)
            return obj.toTask()
        }
    }

    fun uncompleteTask(taskId: Int): Task {
        val url = "$baseUrl/tasks/$taskId/uncomplete"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post("{}".toRequestBody(mediaType))
            .applyUserCookie()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IllegalStateException("Empty body")
            val obj = JSONObject(body)
            return obj.toTask()
        }
    }

    fun deleteTask(taskId: Int) {
        val url = "$baseUrl/tasks/$taskId"
        val request = Request.Builder()
            .url(url)
            .delete()
            .applyUserCookie()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
        }
    }

    fun updateTask(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Task {
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
        Log.d("TasksApi", "Updating task: id=$taskId, url=$url, json=$json")
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body: RequestBody = json.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .put(body)
            .applyUserCookie()
            .build()
        Log.d("TasksApi", "Sending PATCH request to: $url")
        try {
            httpClient.newCall(request).execute().use { response ->
                Log.d("TasksApi", "Response received: code=${response.code}, success=${response.isSuccessful}")
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No error body"
                    Log.e("TasksApi", "HTTP error: code=${response.code}, body=$errorBody")
                    throw IllegalStateException("HTTP ${response.code}: $errorBody")
                }
                val resp = response.body?.string() ?: throw IllegalStateException("Empty body")
                Log.d("TasksApi", "Response body: $resp")
                val obj = JSONObject(resp)
                return obj.toTask()
            }
        } catch (e: Exception) {
            Log.e("TasksApi", "Error updating task", e)
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
    fun syncQueue(items: List<SyncQueueItem>): List<Task> {
        if (items.isEmpty()) return emptyList()

        val url = "$baseUrl/tasks/sync-queue"
        val opsArray = JSONArray()

        // Сортируем по timestamp на клиенте для предсказуемости
        val sorted = items.sortedBy { it.timestamp }
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

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                val errorCode = response.code
                // Сервер обрабатывает конфликты самостоятельно
                // Если сервер вернул ошибку, это не конфликт, а реальная ошибка
                throw IllegalStateException("HTTP $errorCode: $errorBody")
            }
            val respBody = response.body?.string() ?: "[]"
            val array = JSONArray(respBody)
            val result = ArrayList<Task>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(obj.toTask())
            }
            return result
        }
    }
}

private fun JSONObject.toTask(): Task {
    val reminderValue = optString("reminder_time", null)?.takeIf { it.isNotEmpty() }
        ?: throw IllegalStateException("Missing reminder_time in task payload: $this")
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
    )
}

/** Simple API client for groups. */
class GroupsApi(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = BuildConfig.API_BASE_URL
) {
    fun getAll(): Map<Int, String> {
        val url = "$baseUrl/groups/"
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body?.string() ?: "[]"
            val array = JSONArray(body)
            val map = HashMap<Int, String>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getInt("id")
                val name = obj.getString("name")
                map[id] = name
            }
            return map
        }
    }
}


