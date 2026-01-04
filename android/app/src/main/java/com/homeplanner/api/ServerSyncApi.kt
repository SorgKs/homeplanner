package com.homeplanner.api

import com.homeplanner.BuildConfig
import com.homeplanner.model.Task
import com.homeplanner.database.entity.SyncQueueItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * API для синхронизации с сервером.
 * Наследует базовую функциональность от ServerTaskApi.
 */
class ServerSyncApi(
    httpClient: OkHttpClient = createHttpClientWithTimeouts(),
    baseUrl: String = BuildConfig.API_BASE_URL,
    selectedUserId: Int? = null,
) : ServerTaskApi(httpClient, baseUrl, selectedUserId) {

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

        val request = okhttp3.Request.Builder()
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