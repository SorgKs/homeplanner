package com.homeplanner.api

import com.homeplanner.BuildConfig
import com.homeplanner.model.Group
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * API для работы с группами на сервере.
 * 
 * Все методы выполняют HTTP запросы к серверу и не работают с локальным хранилищем.
 * Не используется в UI коде - только для синхронизации через SyncService.
 */
class GroupsServerApi(
    private val httpClient: OkHttpClient = createHttpClientWithTimeouts(),
    private val baseUrl: String = BuildConfig.API_BASE_URL
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
    
    /**
     * Выполняет HTTP запрос асинхронно через корутины.
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
    
    suspend fun getGroupsServer(): Result<List<Group>> = runCatching {
        val url = "$baseUrl/groups/"
        val request = Request.Builder().url(url).build()
        executeAsync(request).use { response ->
            // Бросаем исключение только для 500, остальные коды считаем успешными
            if (response.code == 500) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body?.string() ?: "[]"
            val array = JSONArray(body)
            val result = ArrayList<Group>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val group = Group(
                    id = obj.getInt("id"),
                    name = obj.getString("name"),
                    description = if (obj.isNull("description")) null else obj.getString("description"),
                    createdBy = obj.getInt("created_by"),
                    updatedAt = obj.getLong("updated_at")
                )
                result.add(group)
            }
            result
        }
    }

    suspend fun createGroupServer(group: Group): Result<Group> = runCatching {
        val url = "$baseUrl/groups/"
        val json = JSONObject().apply {
            put("name", group.name)
            put("description", group.description)
            put("created_by", group.createdBy)
        }.toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(mediaType))
            .build()
        executeAsync(request).use { response ->
            if (response.code == 500) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IllegalStateException("Empty body")
            val obj = JSONObject(body)
            Group(
                id = obj.getInt("id"),
                name = obj.getString("name"),
                description = if (obj.isNull("description")) null else obj.getString("description"),
                createdBy = obj.getInt("created_by"),
                updatedAt = obj.getLong("updated_at")
            )
        }
    }

    suspend fun updateGroupServer(groupId: Int, group: Group): Result<Group> = runCatching {
        val url = "$baseUrl/groups/$groupId"
        val json = JSONObject().apply {
            put("name", group.name)
            put("description", group.description)
        }.toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .put(json.toRequestBody(mediaType))
            .build()
        executeAsync(request).use { response ->
            if (response.code == 500) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IllegalStateException("Empty body")
            val obj = JSONObject(body)
            Group(
                id = obj.getInt("id"),
                name = obj.getString("name"),
                description = if (obj.isNull("description")) null else obj.getString("description"),
                createdBy = obj.getInt("created_by"),
                updatedAt = obj.getLong("updated_at")
            )
        }
    }

    suspend fun deleteGroupServer(groupId: Int): Result<Unit> = runCatching {
        val url = "$baseUrl/groups/$groupId"
        val request = Request.Builder()
            .url(url)
            .delete()
            .build()
        executeAsync(request).use { response ->
            if (response.code == 500) throw IllegalStateException("HTTP ${response.code}")
        }
        Unit
    }

    suspend fun getAll(): Map<Int, String> {
        return getGroupsServer().getOrDefault(emptyList()).associate { it.id to it.name }
    }
}
