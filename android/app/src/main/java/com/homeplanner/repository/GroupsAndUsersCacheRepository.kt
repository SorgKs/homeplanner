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
            android.util.Log.d(TAG, "loadUsersFromCache: json from prefs = '$json'")
            if (json.isNullOrEmpty()) {
                android.util.Log.d(TAG, "loadUsersFromCache: json is null or empty, returning empty list")
                return emptyList()
            }
            val jsonArray = org.json.JSONArray(json)
            android.util.Log.d(TAG, "loadUsersFromCache: parsed jsonArray length = ${jsonArray.length()}")
            val users = mutableListOf<com.homeplanner.model.User>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val user = com.homeplanner.model.User(
                    id = obj.getInt("id"),
                    name = obj.getString("name")
                )
                android.util.Log.d(TAG, "loadUsersFromCache: loaded user id=${user.id}, name=${user.name}")
                users.add(user)
            }
            android.util.Log.d(TAG, "loadUsersFromCache: total users loaded = ${users.size}")
            users
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error loading users from cache", e)
            emptyList()
        }
    }

    suspend fun saveUsersToCache(users: List<com.homeplanner.model.User>) {
        try {
            android.util.Log.d(TAG, "saveUsersToCache: saving ${users.size} users")
            val jsonArray = org.json.JSONArray()
            users.forEach { user ->
                android.util.Log.d(TAG, "saveUsersToCache: saving user id=${user.id}, name=${user.name}")
                val obj = org.json.JSONObject().apply {
                    put("id", user.id)
                    put("name", user.name)
                }
                jsonArray.put(obj)
            }
            val jsonString = jsonArray.toString()
            android.util.Log.d(TAG, "saveUsersToCache: json to save = '$jsonString'")
            prefs.edit().putString("cached_users", jsonString).apply()
            android.util.Log.d(TAG, "saveUsersToCache: saved ${users.size} users to prefs")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error saving users to cache", e)
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