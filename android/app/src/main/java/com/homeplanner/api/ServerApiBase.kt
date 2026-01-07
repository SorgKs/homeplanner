package com.homeplanner.api

import com.homeplanner.BuildConfig
import com.homeplanner.model.Task
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Базовый класс для API работы с сервером.
 * Содержит общие настройки HTTP клиента и методы выполнения запросов.
 *
 * ВАЖНО: baseUrl изменять запрещено - он фиксирован и наследуется от единого источника (BuildConfig.API_BASE_URL).
 */
open class ServerApiBase(
    protected val httpClient: OkHttpClient = createHttpClientWithTimeouts(),
    val baseUrl: String = BuildConfig.API_BASE_URL,
    protected val selectedUserId: Int? = null,
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
        internal fun createHttpClientWithTimeouts(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }

    protected fun Request.Builder.applyUserCookie(): Request.Builder {
        android.util.Log.i("ServerApiBase", "applyUserCookie: selectedUserId=$selectedUserId")
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
    protected suspend fun executeAsync(request: Request): Response {
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
}

private fun parseIsoDateTime(dateTimeStr: String?): Long? {
    if (dateTimeStr.isNullOrEmpty()) return null
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = sdf.parse(dateTimeStr.substring(0, 19)) // Take only date and time part, ignore microseconds and timezone
        date?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        android.util.Log.w("ServerApiBase", "Failed to parse datetime: $dateTimeStr", e)
        System.currentTimeMillis()
    }
}

internal fun JSONObject.toTask(): Task {
    // Извлекаем id задачи для логирования
    val taskId = if (has("id") && !isNull("id")) getInt("id") else -1

    // Проверяем reminder_time: может быть строкой или null в JSON
    val reminderValue = if (isNull("reminder_time")) {
        // Задача имеет null reminder_time
        throw IllegalStateException("Missing reminder_time in task payload: $this")
    } else {
        val reminderStr = optString("reminder_time", null)
        if (reminderStr.isNullOrEmpty()) {
            // Задача имеет пустой reminder_time
            throw IllegalStateException("Empty reminder_time in task payload: $this")
        } else {
            reminderStr
        }
    }
    val enabledValue = if (isNull("enabled")) true else getBoolean("enabled")
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
        enabled = enabledValue,
        completed = completedValue,
        assignedUserIds = assignedUserIds,
        updatedAt = parseIsoDateTime(optString("updated_at", null)) ?: System.currentTimeMillis(),
        lastAccessed = System.currentTimeMillis(),
        lastShownAt = parseIsoDateTime(optString("last_shown_at", null)),
        createdAt = parseIsoDateTime(optString("created_at", null)) ?: System.currentTimeMillis()
    )
}