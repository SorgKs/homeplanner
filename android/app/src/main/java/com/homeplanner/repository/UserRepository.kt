package com.homeplanner.repository

import android.content.SharedPreferences
import android.util.Log
import com.homeplanner.database.AppDatabase
import com.homeplanner.database.entity.UserEntity
import com.homeplanner.model.User
import com.homeplanner.utils.HashCalculator
import kotlinx.coroutines.flow.first

/**
 * Репозиторий для работы с пользователями в локальном хранилище.
 */
class UserRepository(
    private val db: AppDatabase,
    private val context: android.content.Context
) {
    private val userDao = db.userDao()

    companion object {
        private const val TAG = "UserRepository"
    }

    suspend fun saveUsersToCache(users: List<User>): Result<Unit> {
        return try {
            Log.d(TAG, "saveUsersToCache: saving ${users.size} users")
            val userEntities = users.map { user ->
                val hash = HashCalculator.calculateUserHash(user)
                UserEntity.fromUser(user, hash)
            }
            userDao.insertUsers(userEntities)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving users to cache", e)
            Result.failure(e)
        }
    }

    suspend fun loadUsersFromCache(): List<User> {
        return try {
            var allUserEntities = userDao.getAllUsers().first()
            Log.d(TAG, "loadUsersFromCache: loaded ${allUserEntities.size} users from database")

            // Миграция данных из SharedPreferences, если Room пустой
            if (allUserEntities.isEmpty()) {
                Log.d(TAG, "loadUsersFromCache: Room is empty, attempting migration from SharedPreferences")
                migrateUsersFromSharedPreferences()
                allUserEntities = userDao.getAllUsers().first()
                Log.d(TAG, "loadUsersFromCache: after migration, loaded ${allUserEntities.size} users from database")
            }

            allUserEntities.map { it.toUser() }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading users from cache", e)
            emptyList()
        }
    }

    suspend fun getUserFromCache(id: Int): User? {
        return try {
            val userEntity = userDao.getUserById(id)
            userEntity?.toUser()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user from cache", e)
            null
        }
    }

    suspend fun searchUsersByName(query: String): List<User> {
        return try {
            val userEntities = userDao.searchUsersByName(query)
            userEntities.map { it.toUser() }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching users", e)
            emptyList()
        }
    }

    suspend fun deleteUserFromCache(id: Int) {
        try {
            userDao.deleteUserById(id)
            Log.d(TAG, "Deleted user from cache: id=$id")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user from cache", e)
        }
    }

    suspend fun getCachedUsersCount(): Int {
        return try {
            userDao.getUserCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cached users count", e)
            0
        }
    }

    suspend fun clearAllUsers() {
        try {
            userDao.deleteAllUsers()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing users", e)
        }
    }

    private suspend fun migrateUsersFromSharedPreferences() {
        try {
            val prefs: SharedPreferences = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
            val json = prefs.getString("cached_users", null)
            if (!json.isNullOrEmpty()) {
                val jsonArray = org.json.JSONArray(json)
                val users = mutableListOf<User>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val user = User(
                        id = obj.getInt("id"),
                        name = obj.getString("name")
                    )
                    users.add(user)
                }
                if (users.isNotEmpty()) {
                    Log.d(TAG, "Migrating ${users.size} users from SharedPreferences to Room")
                    saveUsersToCache(users)
                    // Очистить SharedPreferences после миграции
                    prefs.edit().remove("cached_users").apply()
                    Log.d(TAG, "Migration completed, SharedPreferences cleared")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating users from SharedPreferences", e)
        }
    }
}