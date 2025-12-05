package com.homeplanner.api

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class UserSummary(
    val id: Int,
    val name: String,
    val email: String?,
    val role: String,
    val isActive: Boolean,
)

class UsersApi(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String,
) {

    fun getUsers(): List<UserSummary> {
        val url = "$baseUrl/users/"
        val request = Request.Builder()
            .url(url)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body?.string() ?: "[]"
            val usersJson = JSONArray(body)
            val result = ArrayList<UserSummary>(usersJson.length())
            for (i in 0 until usersJson.length()) {
                val obj = usersJson.getJSONObject(i)
                result.add(obj.toUserSummary())
            }
            return result
        }
    }
}

private fun JSONObject.toUserSummary(): UserSummary {
    return UserSummary(
        id = getInt("id"),
        name = optString("name", "").ifEmpty { "#${getInt("id")}" },
        email = optString("email", null),
        role = optString("role", "regular"),
        isActive = optBoolean("is_active", true),
    )
}

