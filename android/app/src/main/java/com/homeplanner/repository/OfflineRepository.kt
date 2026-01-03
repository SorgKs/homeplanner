package com.homeplanner.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.homeplanner.database.AppDatabase
import com.homeplanner.database.entity.SyncQueueItem
import com.homeplanner.database.entity.TaskCache
import com.homeplanner.model.Task
import com.homeplanner.utils.TaskDateCalculator
import com.homeplanner.debug.BinaryLogger
import com.homeplanner.debug.LogLevel
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Репозиторий для работы с оффлайн-хранилищем.
 * Управляет кэшем задач и очередью синхронизации.
 */
class OfflineRepository(
    private val db: AppDatabase,
    private val context: Context
) {
    private val taskCacheDao = db.taskCacheDao()
    private val syncQueueDao = db.syncQueueDao()
    private val metadataDao = db.metadataDao()
    
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var requestSync: Boolean = false
    
    companion object {
        private const val TAG = "OfflineRepository"
        private const val CACHE_LIMIT_BYTES = 20L * 1024 * 1024 // 20 МБ
        private const val QUEUE_LIMIT_BYTES = 5L * 1024 * 1024 // 5 МБ
        private const val STORAGE_LIMIT_BYTES = 25L * 1024 * 1024 // 25 МБ
        private const val CACHE_RETENTION_DAYS = 7L

        // Ключи для хранения информации о последнем пересчёте задач
        private const val KEY_LAST_UPDATE = "tasks_last_update"
        private const val KEY_LAST_DAY_START_HOUR = "tasks_last_day_start_hour"
    }
    
    // ========== Работа с кэшем задач ==========
    
    suspend fun saveTasksToCache(tasks: List<Task>): Result<Unit> = saveTasksToCacheLocal(tasks)

    suspend fun saveTasksToCacheLocal(tasks: List<Task>): Result<Unit> {
        return try {
            // saveTasksToCacheLocal: [STEP 1] Начало сохранения задач в кэш
            BinaryLogger.getInstance()?.log(314u, listOf(tasks.size))
            val cacheTasks = tasks.map { TaskCache.fromTask(it) }
            // saveTasksToCacheLocal: [STEP 2] Задачи преобразованы в TaskCache
            BinaryLogger.getInstance()?.log(315u, listOf(cacheTasks.size))
            taskCacheDao.insertTasks(cacheTasks)
            // saveTasksToCacheLocal: [STEP 3] Задачи вставлены в базу данных
            BinaryLogger.getInstance()?.log(316u, listOf(cacheTasks.size))

            // Обновление lastAccessed для сохраненных задач
            tasks.forEach { task ->
                taskCacheDao.updateLastAccessed(task.id)
            }
            // saveTasksToCacheLocal: [STEP 4] Обновлен lastAccessed для всех задач
            BinaryLogger.getInstance()?.log(317u, listOf(tasks.size))

            cleanupOldCacheLocal()
            updateStorageMetadata()

            // saveTasksToCacheLocal: [STEP 5] Успешно сохранено задач в кэш
            BinaryLogger.getInstance()?.log(318u, listOf(tasks.size))
            Result.success(Unit)
        } catch (e: Exception) {
            // saveTasksToCacheLocal: [ERROR] Ошибка сохранения задач в кэш: ожидалось %wait%, фактически %fact%
            BinaryLogger.getInstance()?.log(
                91u,
                listOf(e.message ?: "Unknown error", e::class.simpleName ?: "Unknown")
            )
            Result.failure(e)
        }
    }
    
    suspend fun loadTasksFromCache(): List<Task> = loadTasksFromCacheLocal()

    suspend fun loadTasksFromCacheLocal(): List<Task> {
        return try {
            // Загрузка всех задач из кэша
            // Используем Flow.first() для получения первого значения
            val allCacheTasks = taskCacheDao.getAllTasks().first()
            Log.d(TAG, "loadTasksFromCacheLocal: loaded ${allCacheTasks.size} tasks from database")

            // Обновление lastAccessed для загруженных задач
            allCacheTasks.forEach { taskCache ->
                taskCacheDao.updateLastAccessed(taskCache.id)
            }

            // Очистка старых задач
            val cutoffTime = System.currentTimeMillis() - (CACHE_RETENTION_DAYS * 24 * 60 * 60 * 1000)
            taskCacheDao.deleteOldTasks(cutoffTime)

            val result = allCacheTasks.map { it.toTask() }
            Log.d(TAG, "loadTasksFromCacheLocal: converted to ${result.size} Task objects")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error loading tasks from cache", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getTaskFromCache(id: Int): Task? = getTaskFromCacheLocal(id)

    suspend fun getTaskFromCacheLocal(id: Int): Task? {
        return try {
            val cacheTask = taskCacheDao.getTaskById(id)
            cacheTask?.let {
                taskCacheDao.updateLastAccessed(id)
                it.toTask()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting task from cache", e)
            null
        }
    }
    
    suspend fun deleteTaskFromCache(id: Int) = deleteTaskFromCacheLocal(id)

    suspend fun deleteTaskFromCacheLocal(id: Int) {
        try {
            taskCacheDao.deleteTaskById(id)
            updateStorageMetadata()
            Log.d(TAG, "Deleted task from cache: id=$id")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting task from cache", e)
        }
    }
    
    private suspend fun cleanupOldCacheLocal() {
        try {
            val cutoffTime = System.currentTimeMillis() - (CACHE_RETENTION_DAYS * 24 * 60 * 60 * 1000)
            taskCacheDao.deleteOldTasks(cutoffTime)
            
            // Проверка лимита размера кэша
            val cacheSize = taskCacheDao.getCacheSizeBytes() ?: 0L
            if (cacheSize > CACHE_LIMIT_BYTES) {
                // Удаление самых старых задач по LRU
                val oldestTasks = taskCacheDao.getOldestTasks(0, 10)
                oldestTasks.forEach { task ->
                    taskCacheDao.deleteTask(task)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old cache", e)
        }
    }
    
    // ========== Работа с очередью синхронизации ==========
    
    suspend fun addToSyncQueue(
        operation: String,
        entityType: String,
        entityId: Int?,
        payload: Any? = null
    ): Result<Long> {
        return try {
            val isLightOperation = operation in listOf("complete", "uncomplete", "delete")
            
            // Для легких операций payload не сохраняется
            val jsonPayload = if (isLightOperation || payload == null) {
                null
            } else {
                when (payload) {
                    is Task -> taskToJson(payload)
                    is JSONObject -> payload.toString()
                    else -> JSONObject().apply {
                        put("data", payload.toString())
                    }.toString()
                }
            }
            
            val sizeBytes = calculateOperationSize(operation, jsonPayload)
            
            val item = SyncQueueItem(
                operation = operation,
                entityType = entityType,
                entityId = entityId,
                payload = jsonPayload,
                sizeBytes = sizeBytes
            )
            
            // Проверка лимита очереди по объему (5 МБ)
            val currentSize = syncQueueDao.getTotalSizeBytes() ?: 0L
            
            if (currentSize + sizeBytes > QUEUE_LIMIT_BYTES) {
                // Освобождение места в очереди
                freeQueueSpace(sizeBytes, QUEUE_LIMIT_BYTES)
            }
            
            val id = syncQueueDao.insertQueueItem(item)
            updateStorageMetadata()
            
            Log.d(TAG, "Added to sync queue: operation=$operation, entityId=$entityId, id=$id")
            Result.success(id)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to sync queue", e)
            Result.failure(e)
        }
    }
    
    private fun calculateOperationSize(operation: String, payload: String?): Long {
        // Базовый размер структуры (примерно 100 байт)
        var size = 100L
        
        // Размер payload для полных операций
        if (payload != null) {
            size += payload.toByteArray(Charsets.UTF_8).size.toLong()
        }
        
        return size
    }
    
    private suspend fun freeQueueSpace(requiredBytes: Long, queueLimitBytes: Long) {
        var currentSize = syncQueueDao.getTotalSizeBytes() ?: 0L
        
        // Сначала удаляем полные операции (create/update) - самые большие
        val allItems = syncQueueDao.getQueueItemsByStatus("pending", Int.MAX_VALUE)
        val fullOps = allItems.filter { it.isFullOperation() }
            .sortedBy { it.timestamp } // FIFO
        
        for (item in fullOps) {
            if (currentSize + requiredBytes <= queueLimitBytes) break
            syncQueueDao.deleteQueueItemById(item.id)
            currentSize -= item.sizeBytes
            Log.d(TAG, "Freed space: removed full operation ${item.operation} (${item.sizeBytes} bytes)")
        }
        
        // Затем легкие операции, если места все еще не хватает
        if (currentSize + requiredBytes > queueLimitBytes) {
            val lightOps = allItems.filter { it.isLightOperation() }
                .sortedBy { it.timestamp } // FIFO
            
            for (item in lightOps) {
                if (currentSize + requiredBytes <= queueLimitBytes) break
                syncQueueDao.deleteQueueItemById(item.id)
                currentSize -= item.sizeBytes
                Log.d(TAG, "Freed space: removed light operation ${item.operation} (${item.sizeBytes} bytes)")
            }
        }
    }
    
    suspend fun getSyncQueue(status: String = "pending", limit: Int = 100): List<SyncQueueItem> {
        return try {
            syncQueueDao.getQueueItemsByStatus(status, limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sync queue", e)
            emptyList()
        }
    }
    
    suspend fun getPendingQueueItems(limit: Int = 10000): List<SyncQueueItem> {
        return try {
            // Получаем pending операции с разумным лимитом (по умолчанию 10000)
            // Разбиение на группы будет в syncQueue() если операций много
            // Используем лимит, чтобы избежать проблем с памятью при очень большом количестве операций
            syncQueueDao.getPendingItems(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending queue items", e)
            emptyList()
        }
    }
    
    suspend fun getPendingOperationsCount(): Int {
        return try {
            syncQueueDao.getCountByStatus("pending")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending operations count", e)
            0
        }
    }
    
    suspend fun updateQueueItem(item: SyncQueueItem) {
        try {
            syncQueueDao.updateQueueItem(item)
            updateStorageMetadata()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating queue item", e)
        }
    }
    
    suspend fun removeFromQueue(id: Long) {
        try {
            syncQueueDao.deleteQueueItemById(id)
            updateStorageMetadata()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing from queue", e)
        }
    }
    
    suspend fun getQueueItemById(id: Long): SyncQueueItem? {
        return try {
            syncQueueDao.getQueueItemById(id)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting queue item by id", e)
            null
        }
    }
    
    // ========== Метаданные хранилища ==========
    
    private suspend fun updateStorageMetadata() {
        try {
            val queueSize = syncQueueDao.getTotalSizeBytes() ?: 0L
            val cacheSize = taskCacheDao.getCacheSizeBytes() ?: 0L
            val totalSize = queueSize + cacheSize
            val percentage = (totalSize.toFloat() / STORAGE_LIMIT_BYTES * 100).coerceAtMost(100f)
            
            // Сохранение метаданных
            metadataDao.insertMetadata(
                com.homeplanner.database.entity.Metadata(
                    key = "queue_size_bytes",
                    value = queueSize.toString()
                )
            )
            metadataDao.insertMetadata(
                com.homeplanner.database.entity.Metadata(
                    key = "cache_size_bytes",
                    value = cacheSize.toString()
                )
            )
            metadataDao.insertMetadata(
                com.homeplanner.database.entity.Metadata(
                    key = "total_storage_bytes",
                    value = totalSize.toString()
                )
            )
            metadataDao.insertMetadata(
                com.homeplanner.database.entity.Metadata(
                    key = "storage_percentage",
                    value = percentage.toString()
                )
            )
            
            // Сохранение в SharedPreferences для быстрого доступа
            prefs.edit()
                .putLong("queue_size_bytes", queueSize)
                .putLong("cache_size_bytes", cacheSize)
                .putLong("total_storage_bytes", totalSize)
                .putFloat("storage_percentage", percentage)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating storage metadata", e)
        }
    }
    
    suspend fun getStoragePercentage(): Float {
        return try {
            prefs.getFloat("storage_percentage", 0f)
        } catch (e: Exception) {
            0f
        }
    }
    
    suspend fun getQueueSizeBytes(): Long {
        return try {
            prefs.getLong("queue_size_bytes", 0L)
        } catch (e: Exception) {
            0L
        }
    }
    
    suspend fun getCacheSizeBytesLocal(): Long {
        return try {
            prefs.getLong("cache_size_bytes", 0L)
        } catch (e: Exception) {
            0L
        }
    }
    
    suspend fun getCachedTasksCountLocal(): Int {
        return try {
            taskCacheDao.getTaskCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cached tasks count", e)
            0
        }
    }
    
    // ========== Вспомогательные функции ==========
    
    private fun taskToJson(task: Task): String {
        return JSONObject().apply {
            // Не включаем id в payload для обновления через очередь синхронизации
            // id передается отдельно как task_id
            put("title", task.title)
            put("description", task.description)
            put("task_type", task.taskType)
            put("recurrence_type", task.recurrenceType)
            put("recurrence_interval", task.recurrenceInterval)
            put("interval_days", task.intervalDays)
            put("reminder_time", task.reminderTime)
            put("group_id", task.groupId)
            put("active", task.active)
            put("completed", task.completed)
            put("assigned_user_ids", org.json.JSONArray(task.assignedUserIds))
        }.toString()
    }
    
    suspend fun clearAllCacheLocal() {
        try {
            taskCacheDao.deleteAllTasks()
            updateStorageMetadata()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }
    
    suspend fun clearAllQueue() {
        try {
            syncQueueDao.deleteAllQueueItems()
            updateStorageMetadata()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing queue", e)
        }
    }

    // ========== Пересчёт задач по новому дню ==========

    /**
     * Получить timestamp последнего пересчёта задач или null, если ещё не пересчитывали.
     */
    suspend fun getLastUpdateTimestamp(): Long? {
        return try {
            if (!prefs.contains(KEY_LAST_UPDATE)) {
                null
            } else {
                prefs.getLong(KEY_LAST_UPDATE, 0L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading last update timestamp", e)
            null
        }
    }

    /**
     * Получить hour начала дня, который использовался при последнем пересчёте.
     */
    suspend fun getLastDayStartHour(): Int? {
        return try {
            if (!prefs.contains(KEY_LAST_DAY_START_HOUR)) {
                null
            } else {
                prefs.getInt(KEY_LAST_DAY_START_HOUR, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading last dayStartHour", e)
            null
        }
    }

    /**
     * Сохранить данные о последнем пересчёте задач.
     */
    suspend fun setLastUpdateTimestamp(timestamp: Long, dayStartHour: Int) {
        try {
            prefs.edit()
                .putLong(KEY_LAST_UPDATE, timestamp)
                .putInt(KEY_LAST_DAY_START_HOUR, dayStartHour)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving last update timestamp", e)
        }
    }

    /**
     * Пересчёт задач при наступлении нового логического дня.
     *
     * Возвращает true, если пересчёт был выполнен (и кэш обновлён),
     * и false, если новый день ещё не наступил или пересчитывать нечего.
     *
     * ВАЖНО:
     * - При пустом кэше `last_update` не проверяется, пересчёт не выполняется.
     * - Значение `last_update` считается источником истины на стороне сервера и
     *   должно устанавливаться при получении данных от сервера.
     */
    suspend fun updateRecurringTasksForNewDay(dayStartHour: Int): Boolean {
        return try {
            val now = System.currentTimeMillis()

            // Сначала проверяем содержимое кэша: если нечего пересчитывать, сразу выходим.
            val cachedTasks = loadTasksFromCacheLocal()
            if (cachedTasks.isEmpty()) {
                Log.d(TAG, "updateRecurringTasksForNewDay: cache is empty, nothing to recalculate")
                return false
            }

            val lastUpdate = getLastUpdateTimestamp()
            val lastDayStartHour = getLastDayStartHour()

            val isNewDay = TaskDateCalculator.isNewDay(
                lastUpdateMillis = lastUpdate,
                nowMillis = now,
                lastDayStartHour = lastDayStartHour,
                currentDayStartHour = dayStartHour
            )

            if (!isNewDay) {
                Log.d(TAG, "updateRecurringTasksForNewDay: same logical day, skipping")
                return false
            }

            val updatedTasks = mutableListOf<Task>()
            for (task in cachedTasks) {
                if (!task.completed) {
                    // Ничего не меняем
                    updatedTasks.add(task)
                    continue
                }

                when (task.taskType) {
                    "one_time" -> {
                        // Завершённые one_time становятся неактивными, дата не меняется
                        updatedTasks.add(
                            task.copy(
                                active = false,
                                // completed остаётся true — как и на сервере перед "уходом" задачи
                            )
                        )
                    }
                    "recurring", "interval" -> {
                        val nextReminder = TaskDateCalculator.calculateNextReminderTime(
                            task = task,
                            nowMillis = now,
                            dayStartHour = dayStartHour
                        )
                        updatedTasks.add(
                            task.copy(
                                reminderTime = nextReminder,
                                completed = false
                            )
                        )
                    }
                    else -> {
                        // Неизвестный тип — оставляем без изменений
                        updatedTasks.add(task)
                    }
                }
            }

            // Перезаписываем кэш пересчитанными задачами
            saveTasksToCacheLocal(updatedTasks)
            setLastUpdateTimestamp(now, dayStartHour)

            Log.d(TAG, "updateRecurringTasksForNewDay: recalculated ${updatedTasks.size} tasks")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating recurring tasks for new day", e)
            false
        }
    }

    // Стubs for groups and users caching (not implemented yet)
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
            BinaryLogger.getInstance()?.log(408u, listOf(users.size))
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

