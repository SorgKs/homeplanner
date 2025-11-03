package com.homeplanner.api

import com.homeplanner.BuildConfig
import com.homeplanner.model.Task
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Simple API client for tasks. */
class TasksApi(private val httpClient: OkHttpClient = OkHttpClient()) {

    private val baseUrl: String = BuildConfig.API_BASE_URL

    fun getTasks(activeOnly: Boolean = true): List<Task> {
        val url = buildString {
            append(baseUrl).append("/tasks/")
            if (activeOnly) append("?active_only=true")
        }
        val request = Request.Builder().url(url).build()
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

    fun createTask(task: Task): Task {
        val url = "$baseUrl/tasks/"
        val json = JSONObject().apply {
            put("title", task.title)
            put("description", task.description)
            put("task_type", task.taskType)
            put("recurrence_type", task.recurrenceType)
            put("recurrence_interval", task.recurrenceInterval)
            put("interval_days", task.intervalDays)
            put("next_due_date", task.nextDueDate)
            put("reminder_time", task.reminderTime)
            put("group_id", task.groupId)
        }.toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(mediaType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IllegalStateException("Empty body")
            val obj = JSONObject(body)
            return obj.toTask()
        }
    }

    fun completeTask(taskId: Int): Task {
        val url = "$baseUrl/tasks/$taskId/complete"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post("{}".toRequestBody(mediaType))
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
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IllegalStateException("Empty body")
            val obj = JSONObject(body)
            return obj.toTask()
        }
    }
}

private fun JSONObject.toTask(): Task {
    // Handle last_completed_at: if null in JSON, return null; if present, return the string value
    val lastCompletedAtValue: String? = if (isNull("last_completed_at")) {
        null
    } else {
        val value = optString("last_completed_at", null)
        if (value.isNullOrEmpty()) null else value
    }
    return Task(
        id = getInt("id"),
        title = getString("title"),
        description = optString("description", null),
        taskType = getString("task_type"),
        recurrenceType = optString("recurrence_type", null),
        recurrenceInterval = if (isNull("recurrence_interval")) null else getInt("recurrence_interval"),
        intervalDays = if (isNull("interval_days")) null else getInt("interval_days"),
        nextDueDate = getString("next_due_date"),
        reminderTime = optString("reminder_time", null),
        groupId = if (isNull("group_id")) null else getInt("group_id"),
        isCompleted = lastCompletedAtValue != null,
        lastCompletedAt = lastCompletedAtValue
    )
}

/** Simple API client for groups. */
class GroupsApi(private val httpClient: OkHttpClient = OkHttpClient()) {
    private val baseUrl: String = BuildConfig.API_BASE_URL
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


