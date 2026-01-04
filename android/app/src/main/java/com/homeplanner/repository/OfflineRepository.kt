package com.homeplanner.repository

import android.content.Context
import com.homeplanner.database.AppDatabase
import com.homeplanner.database.entity.SyncQueueItem
import com.homeplanner.model.Task
import org.json.JSONObject

/**
 * Репозиторий для работы с оффлайн-хранилищем.
 * Управляет кэшем задач и очередью синхронизации.
 */
class OfflineRepository(
    private val db: AppDatabase,
    private val context: Context
) {
    private val taskCacheRepository = TaskCacheRepository(db)
    private val syncQueueRepository = SyncQueueRepository(db)
    private val storageMetadataManager = StorageMetadataManager(db, context)
    private val recurringTaskUpdater = RecurringTaskUpdater(context, taskCacheRepository)
    private val groupsAndUsersCacheRepository = GroupsAndUsersCacheRepository(context)

    var requestSync: Boolean = false

    // ========== Работа с кэшем задач ==========

    suspend fun saveTasksToCache(tasks: List<Task>): Result<Unit> {
        val result = taskCacheRepository.saveTasksToCache(tasks)
        if (result.isSuccess) {
            storageMetadataManager.updateStorageMetadata()
        }
        return result
    }

    suspend fun loadTasksFromCache(): List<Task> = taskCacheRepository.loadTasksFromCache()

    suspend fun getTaskFromCache(id: Int): Task? = taskCacheRepository.getTaskFromCache(id)

    suspend fun deleteTaskFromCache(id: Int) {
        taskCacheRepository.deleteTaskFromCache(id)
        storageMetadataManager.updateStorageMetadata()
    }

    // ========== Работа с очередью синхронизации ==========

    suspend fun addToSyncQueue(
        operation: String,
        entityType: String,
        entityId: Int?,
        payload: Any? = null
    ): Result<Long> {
        val result = syncQueueRepository.addToSyncQueue(operation, entityType, entityId, payload)
        if (result.isSuccess) {
            storageMetadataManager.updateStorageMetadata()
        }
        return result
    }

    suspend fun getSyncQueue(status: String = "pending", limit: Int = 100): List<SyncQueueItem> =
        syncQueueRepository.getSyncQueue(status, limit)

    suspend fun getPendingQueueItems(limit: Int = 10000): List<SyncQueueItem> =
        syncQueueRepository.getPendingQueueItems(limit)

    suspend fun getPendingOperationsCount(): Int = syncQueueRepository.getPendingOperationsCount()

    suspend fun updateQueueItem(item: SyncQueueItem) {
        syncQueueRepository.updateQueueItem(item)
        storageMetadataManager.updateStorageMetadata()
    }

    suspend fun removeFromQueue(id: Long) {
        syncQueueRepository.removeFromQueue(id)
        storageMetadataManager.updateStorageMetadata()
    }

    suspend fun getQueueItemById(id: Long): SyncQueueItem? = syncQueueRepository.getQueueItemById(id)

    // ========== Метаданные хранилища ==========

    suspend fun getStoragePercentage(): Float = storageMetadataManager.getStoragePercentage()

    suspend fun getQueueSizeBytes(): Long = storageMetadataManager.getQueueSizeBytes()

    suspend fun getCacheSizeBytesLocal(): Long = storageMetadataManager.getCacheSizeBytes()

    suspend fun getCachedTasksCountLocal(): Int = taskCacheRepository.getCachedTasksCount()

    // ========== Вспомогательные функции ==========

    suspend fun clearAllCacheLocal() {
        taskCacheRepository.clearAllCache()
        storageMetadataManager.updateStorageMetadata()
    }

    suspend fun clearAllQueue() {
        syncQueueRepository.clearAllQueue()
        storageMetadataManager.updateStorageMetadata()
    }

    // ========== Пересчёт задач по новому дню ==========

    suspend fun getLastUpdateTimestamp(): Long? = recurringTaskUpdater.getLastUpdateTimestamp()

    suspend fun getLastDayStartHour(): Int? = recurringTaskUpdater.getLastDayStartHour()

    suspend fun setLastUpdateTimestamp(timestamp: Long, dayStartHour: Int) =
        recurringTaskUpdater.setLastUpdateTimestamp(timestamp, dayStartHour)

    suspend fun updateRecurringTasksForNewDay(dayStartHour: Int): Boolean =
        recurringTaskUpdater.updateRecurringTasksForNewDay(dayStartHour)

    // ========== Группы и пользователи ==========

    suspend fun loadGroupsFromCache(): List<com.homeplanner.model.Group> =
        groupsAndUsersCacheRepository.loadGroupsFromCache()

    suspend fun saveGroupsToCache(groups: List<com.homeplanner.model.Group>) =
        groupsAndUsersCacheRepository.saveGroupsToCache(groups)

    suspend fun deleteGroupFromCache(groupId: Int) =
        groupsAndUsersCacheRepository.deleteGroupFromCache(groupId)

    suspend fun loadUsersFromCache(): List<com.homeplanner.model.User> =
        groupsAndUsersCacheRepository.loadUsersFromCache()

    suspend fun saveUsersToCache(users: List<com.homeplanner.model.User>) =
        groupsAndUsersCacheRepository.saveUsersToCache(users)

    suspend fun deleteUserFromCache(userId: Int) =
        groupsAndUsersCacheRepository.deleteUserFromCache(userId)

    suspend fun getAll(): List<com.homeplanner.model.UserSummary> =
        groupsAndUsersCacheRepository.getAll()

    suspend fun getUsers(): List<com.homeplanner.model.UserSummary> =
        groupsAndUsersCacheRepository.getUsers()
}

