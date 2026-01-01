package com.homeplanner.api

import com.homeplanner.model.User
import com.homeplanner.repository.OfflineRepository

/**
 * API для работы с локальным хранилищем пользователей.
 *
 * Предоставляет данные пользователей из локального кэша с автоматическим
 * добавлением операций в очередь синхронизации.
 */
class UsersLocalApi(private val offlineRepository: OfflineRepository) {

    suspend fun getUsersLocal(): Result<List<User>> = runCatching {
        offlineRepository.loadUsersFromCache()
    }

    suspend fun createUserLocal(user: User): Result<User> = runCatching {
        offlineRepository.saveUsersToCache(listOf(user))
        addUserToSyncQueue("create", null, user)
        user
    }

    suspend fun updateUserLocal(userId: Int, user: User): Result<User> = runCatching {
        offlineRepository.saveUsersToCache(listOf(user))
        addUserToSyncQueue("update", userId, user)
        user
    }

    suspend fun deleteUserLocal(userId: Int): Result<Unit> = runCatching {
        offlineRepository.deleteUserFromCache(userId)
        addUserToSyncQueue("delete", userId, null)
    }

    private suspend fun addUserToSyncQueue(action: String, userId: Int?, user: User?) {
        offlineRepository.addToSyncQueue(action, "user", userId, user)
        offlineRepository.requestSync = true
    }
}
