package com.homeplanner.repository

import android.content.Context
import com.homeplanner.database.AppDatabase
import com.homeplanner.database.entity.SyncQueueItem
import com.homeplanner.model.Task
import com.homeplanner.utils.AlarmManagerUtil
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Репозиторий для работы с оффлайн-хранилищем.
 * Управляет кэшем задач и очередью синхронизации.
 */
class OfflineRepository(
    private val db: AppDatabase,
    private val context: Context
) {
    companion object {
        private const val DAY_START_HOUR = 4 // TODO: Получать из настроек
    }
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
            // Управление алармами для сохранённых задач
            manageAlarmsForTasks(tasks)
        }
        return result
    }

    suspend fun loadTasksFromCache(): List<Task> {
        checkAndRecalculateIfNewDay(DAY_START_HOUR)
        return taskCacheRepository.loadTasksFromCache()
    }

    suspend fun getTaskFromCache(id: Int): Task? {
        checkAndRecalculateIfNewDay(DAY_START_HOUR)
        return taskCacheRepository.getTaskFromCache(id)
    }

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

    /**
     * Проверяет новый день и пересчитывает задачи если необходимо.
     * Вызывается при любых обращениях к локальному хранилищу.
     */
    suspend fun checkAndRecalculateIfNewDay(dayStartHour: Int) {
        val updated = updateRecurringTasksForNewDay(dayStartHour)
        if (updated) {
            android.util.Log.i("OfflineRepository", "Tasks recalculated for new day")
        }
    }

    // ========== Группы и пользователи ==========

    suspend fun loadGroupsFromCache(): List<com.homeplanner.model.Group> {
        checkAndRecalculateIfNewDay(DAY_START_HOUR)
        return groupsAndUsersCacheRepository.loadGroupsFromCache()
    }

    suspend fun saveGroupsToCache(groups: List<com.homeplanner.model.Group>) =
        groupsAndUsersCacheRepository.saveGroupsToCache(groups)

    suspend fun deleteGroupFromCache(groupId: Int) =
        groupsAndUsersCacheRepository.deleteGroupFromCache(groupId)

    suspend fun loadUsersFromCache(): List<com.homeplanner.model.User> {
        checkAndRecalculateIfNewDay(DAY_START_HOUR)
        return groupsAndUsersCacheRepository.loadUsersFromCache()
    }

    suspend fun saveUsersToCache(users: List<com.homeplanner.model.User>) =
        groupsAndUsersCacheRepository.saveUsersToCache(users)

    suspend fun deleteUserFromCache(userId: Int) =
        groupsAndUsersCacheRepository.deleteUserFromCache(userId)

    suspend fun getAll(): List<com.homeplanner.model.UserSummary> =
        groupsAndUsersCacheRepository.getAll()

    suspend fun getUsers(): List<com.homeplanner.model.UserSummary> =
        groupsAndUsersCacheRepository.getUsers()

    // ========== Управление алармами ==========

    /**
     * Управляет алармами для списка задач.
     * Для каждой задачи с alarm=true и alarmTime в будущем - ставит аларм,
     * для остальных - отменяет аларм.
     */
    private fun manageAlarmsForTasks(tasks: List<Task>) {
        val currentTimeMillis = System.currentTimeMillis()
        tasks.forEach { task ->
            try {
                if (task.alarm) {
                    val alarmTimeMillis = parseIsoDateTimeToMillis(task.reminderTime)
                    if (alarmTimeMillis > currentTimeMillis) {
                        AlarmManagerUtil.scheduleAlarm(context, task.id.toLong(), alarmTimeMillis)
                        android.util.Log.d("OfflineRepository", "Scheduled alarm for task ${task.id} at ${task.reminderTime}")
                    } else {
                        AlarmManagerUtil.cancelAlarm(context, task.id.toLong())
                        android.util.Log.d("OfflineRepository", "Cancelled alarm for task ${task.id} (past time: ${task.reminderTime})")
                    }
                } else {
                    AlarmManagerUtil.cancelAlarm(context, task.id.toLong())
                    android.util.Log.d("OfflineRepository", "Cancelled alarm for task ${task.id} (alarm disabled)")
                }
            } catch (e: Exception) {
                android.util.Log.e("OfflineRepository", "Error managing alarm for task ${task.id}", e)
            }
        }
    }

    /**
     * Конвертирует ISO datetime строку в milliseconds since epoch.
     */
    private fun parseIsoDateTimeToMillis(isoDateTime: String): Long {
        return try {
            // Try parsing with microseconds first (assume UTC if no timezone)
            val withMicroseconds = if (isoDateTime.contains(".")) {
                "${isoDateTime}Z"
            } else {
                "${isoDateTime}.000000Z"
            }
            Instant.parse(withMicroseconds).toEpochMilli()
        } catch (e: Exception) {
            android.util.Log.e("OfflineRepository", "Failed to parse alarm time: $isoDateTime", e)
            0L
        }
    }
}

