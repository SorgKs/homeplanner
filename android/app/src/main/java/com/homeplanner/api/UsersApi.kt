package com.homeplanner.api

import com.homeplanner.model.User
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
 * API для работы с пользователями на сервере.
 * 
 * Все методы выполняют HTTP запросы к серверу и не работают с локальным хранилищем.
 * Не используется в UI коде - только для синхронизации через SyncService.
 */
class UsersServerApi(
    private val httpClient: OkHttpClient = createHttpClientWithTimeouts(),
    private val baseUrl: String,
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

    suspend fun getUsersServer(): Result<List<User>> = runCatching {
        val url = "$baseUrl/users/"
        val request = Request.Builder().url(url).build()

        executeAsync(request).use { response ->
            if (response.code == 500) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body?.string() ?: "[]"
            val usersJson = JSONArray(body)
            val result = ArrayList<User>(usersJson.length())
            for (i in 0 until usersJson.length()) {
                val obj = usersJson.getJSONObject(i)
                val user = User(
                    id = obj.getInt("id"),
                    name = obj.getString("name"),
                    email = obj.getString("email"),
                    role = obj.getString("role"),
                    status = obj.getString("status"),
                    updatedAt = obj.getLong("updated_at")
                )
                result.add(user)
            }
            result
        }
    }

    suspend fun createUserServer(user: User): Result<User> = runCatching {
        val url = "$baseUrl/users/"
        val json = JSONObject().apply {
            put("name", user.name)
            put("email", user.email)
            put("role", user.role)
            put("status", user.status)
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
            User(
                id = obj.getInt("id"),
                name = obj.getString("name"),
                email = obj.getString("email"),
                role = obj.getString("role"),
                status = obj.getString("status"),
                updatedAt = obj.getLong("updated_at")
            )
        }
    }

    suspend fun updateUserServer(userId: Int, user: User): Result<User> = runCatching {
        val url = "$baseUrl/users/$userId"
        val json = JSONObject().apply {
            put("name", user.name)
            put("email", user.email)
            put("role", user.role)
            put("status", user.status)
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
            User(
                id = obj.getInt("id"),
                name = obj.getString("name"),
                email = obj.getString("email"),
                role = obj.getString("role"),
                status = obj.getString("status"),
                updatedAt = obj.getLong("updated_at")
            )
        }
    }

    suspend fun deleteUserServer(userId: Int): Result<Unit> = runCatching {
        val url = "$baseUrl/users/$userId"
        val request = Request.Builder()
            .url(url)
            .delete()
            .build()
        executeAsync(request).use { response ->
            if (response.code == 500) throw IllegalStateException("HTTP ${response.code}")
        }
        Unit
    }

    suspend fun getUsers(): List<UserSummary> {
        return getUsersServer().getOrDefault(emptyList()).map {
            UserSummary(
                id = it.id,
                name = it.name,
                email = it.email,
                role = it.role,
                isActive = it.status == "active"
            )
        }
    }
}

