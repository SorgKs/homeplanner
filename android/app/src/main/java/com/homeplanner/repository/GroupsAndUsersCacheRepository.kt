package com.homeplanner.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Репозиторий для кэша групп и пользователей.
 */
class GroupsAndUsersCacheRepository(
    private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "GroupsAndUsersCacheRepository"
    }

    suspend fun loadGroupsFromCache(): List<com.homeplanner.model.Group> = emptyList()

    suspend fun saveGroupsToCache(groups: List<com.homeplanner.model.Group>) {}

    suspend fun deleteGroupFromCache(groupId: Int) {}

    suspend fun loadUsersFromCache(): List<com.homeplanner.model.User> {
        return try {
            val json = prefs.getString("cached_users", null)
            if (json.isNullOrEmpty()) return emptyList()
            val jsonArray = org.json.JSONArray(json)
            val users = mutableListOf<com.homeplanner.model.User>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                users.add(com.homeplanner.model.User(
                    id = obj.getInt("id"),
                    name = obj.getString("name")
                ))
            }
            users
        } catch (e: Exception) {
            Log.e(TAG, "Error loading users from cache", e)
            emptyList()
        }
    }

    suspend fun saveUsersToCache(users: List<com.homeplanner.model.User>) {
        try {
            val jsonArray = org.json.JSONArray()
            users.forEach { user ->
                val obj = org.json.JSONObject().apply {
                    put("id", user.id)
                    put("name", user.name)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString("cached_users", jsonArray.toString()).apply()
            // BinaryLogger.getInstance()?.log(408u, listOf<Any>(users.size, 20), 20) // Можно добавить если нужно
        } catch (e: Exception) {
            Log.e(TAG, "Error saving users to cache", e)
        }
    }

    suspend fun deleteUserFromCache(userId: Int) {
        try {
            val users = loadUsersFromCache().toMutableList()
            users.removeIf { it.id == userId }
            saveUsersToCache(users)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user from cache", e)
        }
    }

    suspend fun getAll(): List<com.homeplanner.model.UserSummary> = emptyList()

    suspend fun getUsers(): List<com.homeplanner.model.UserSummary> = emptyList()
}