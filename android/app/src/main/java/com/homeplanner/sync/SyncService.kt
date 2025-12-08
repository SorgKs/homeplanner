package com.homeplanner.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.homeplanner.api.GroupsApi
import com.homeplanner.api.TasksApi
import com.homeplanner.api.UserSummary
import com.homeplanner.api.UsersApi
import com.homeplanner.database.entity.SyncQueueItem
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository
import org.json.JSONObject
import kotlinx.coroutines.delay
import java.security.MessageDigest

/**
 * Сервис синхронизации очереди операций с сервером.
 * Обрабатывает экспоненциальную задержку, конфликты по времени обновления и ошибки.
 */
class SyncService(
    private val repository: OfflineRepository,
    private val tasksApi: TasksApi,
    private val context: Context
) {
    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    companion object {
        private const val TAG = "SyncService"
    }
    
    fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Синхронизация состояния перед пересчётом задач по новому дню.
     *
     * Алгоритм:
     * 1. Если оффлайн — вернуть false (пересчёт будет только локальным).
     * 2. Если в очереди есть операции — выполнить syncQueue().
     * 3. Загрузить актуальные задачи с сервера и сохранить их в кэш.
     */
    suspend fun syncStateBeforeRecalculation(): Boolean {
        if (!isOnline()) {
            Log.d(TAG, "syncStateBeforeRecalculation: offline, skipping server sync")
            return false
        }

        return try {
            val pending = repository.getPendingOperationsCount()
            if (pending > 0) {
                Log.d(TAG, "syncStateBeforeRecalculation: syncing $pending pending operations before recalculation")
                val result = syncQueue()
                if (result.isFailure) {
                    Log.w(TAG, "syncStateBeforeRecalculation: syncQueue failed, but continuing with server reload")
                }
            }

            Log.d(TAG, "syncStateBeforeRecalculation: loading tasks from server")
            val serverTasks = tasksApi.getTasks(activeOnly = false)
            repository.saveTasksToCache(serverTasks)
            true
        } catch (e: Exception) {
            Log.e(TAG, "syncStateBeforeRecalculation: error during pre-recalculation sync", e)
            false
        }
    }
    
    suspend fun syncQueue(): Result<SyncResult> {
        if (!isOnline()) {
            Log.d(TAG, "No internet connection, skipping sync")
            return Result.failure(Exception("No internet connection"))
        }
        
        val queueItems = repository.getPendingQueueItems()
        if (queueItems.isEmpty()) {
            Log.d(TAG, "No pending items in queue")
            return Result.success(SyncResult(0, 0, 0))
        }
        
        return try {
            Log.d(TAG, "Starting batched sync of ${queueItems.size} items")
            // Сервер обрабатывает конфликты самостоятельно и возвращает актуальное состояние задач
            val tasks = tasksApi.syncQueue(queueItems)
            
            // Сервер - источник истины: очищаем очередь и обновляем кэш актуальными данными с сервера
            repository.clearAllQueue()
            if (tasks.isNotEmpty()) {
                // Логируем reminder_time для всех задач после синхронизации
                tasks.forEach { task ->
                    Log.d(TAG, "Task after sync: id=${task.id}, reminderTime=${task.reminderTime}")
                }
                // Обновляем кэш актуальными данными с сервера (сервер сам решил, какие операции применить)
                repository.saveTasksToCache(tasks)
            }

            val syncResult = SyncResult(
                successCount = queueItems.size,
                failureCount = 0,
                conflictCount = 0
            )
            Log.d(TAG, "Batched sync completed successfully: $syncResult")
            Result.success(syncResult)
        } catch (e: Exception) {
            Log.e(TAG, "Batched sync failed", e)
            Result.failure(e)
            }
    }
    
    data class SyncResult(
        val successCount: Int,
        val failureCount: Int,
        val conflictCount: Int
    )
    
    /**
     * Результат синхронизации кэша с сервером.
     */
    data class SyncCacheResult(
        val cacheUpdated: Boolean,
        val users: List<UserSummary>? = null,
        val groups: Map<Int, String>? = null
    )
    
    /**
     * Синхронизация кэша с сервером.
     * 
     * Алгоритм:
     * 1. Проверка наличия интернета
     * 2. Запрос данных с сервера через TasksApi.getTasks()
     *    - Если запрос не удался → возврат ошибки (не тратим ресурсы на вычисление хеша кэша)
     * 3. Загрузка данных из кэша для сравнения
     * 4. Вычисление хеша кэшированных данных
     * 5. Вычисление хеша данных с сервера
     * 6. Сравнение хешей и обновление кэша при различиях
     * 7. Синхронизация очереди операций
     * 8. Опционально: загрузка групп и пользователей (если переданы API)
     * 9. Возврат результата
     * 
     * Оптимизация: Сначала запрашиваем данные с сервера, и только если запрос успешен, 
     * вычисляем хеш кэша. Это позволяет избежать лишних вычислений при ошибках сети.
     * 
     * @param groupsApi Опциональный API для загрузки групп
     * @param usersApi Опциональный API для загрузки пользователей
     * @return Результат синхронизации с флагом обновления кэша и опциональными данными
     */
    suspend fun syncCacheWithServer(
        groupsApi: GroupsApi? = null,
        usersApi: UsersApi? = null
    ): Result<SyncCacheResult> {
        // 1. Проверка наличия интернета
        if (!isOnline()) {
            Log.d(TAG, "syncCacheWithServer: offline, skipping")
            return Result.failure(Exception("No internet connection"))
        }
        
        return try {
            // 2. Запрос данных с сервера (сначала, чтобы не тратить ресурсы на вычисление хеша кэша при ошибке)
            Log.d(TAG, "syncCacheWithServer: fetching tasks from server")
            val serverTasks = tasksApi.getTasks(activeOnly = false)
            Log.d(TAG, "syncCacheWithServer: received ${serverTasks.size} tasks from server")
            
            // 3. Загрузка данных из кэша для сравнения
            val cachedTasks = repository.loadTasksFromCache()
            Log.d(TAG, "syncCacheWithServer: loaded ${cachedTasks.size} tasks from cache")
            
            // 4. Вычисление хеша кэшированных данных
            val cachedHash = calculateTasksHash(cachedTasks)
            
            // 5. Вычисление хеша данных с сервера
            val serverHash = calculateTasksHash(serverTasks)
            
            // 6. Сравнение хешей и обновление кэша при различиях
            val cacheUpdated = if (cachedHash != serverHash) {
                Log.d(TAG, "syncCacheWithServer: hash mismatch, updating cache (cached: ${cachedHash.take(16)}..., server: ${serverHash.take(16)}...)")
                repository.saveTasksToCache(serverTasks)
                true
            } else {
                Log.d(TAG, "syncCacheWithServer: hashes match, cache is up to date")
                false
            }
            
            // 7. Синхронизация очереди операций
            val queueSyncResult = syncQueue()
            if (queueSyncResult.isFailure) {
                Log.w(TAG, "syncCacheWithServer: queue sync failed, but continuing")
            }
            
            // 8. Опционально: загрузка групп и пользователей
            var groups: Map<Int, String>? = null
            var users: List<UserSummary>? = null
            
            if (groupsApi != null) {
                try {
                    groups = groupsApi.getAll()
                    Log.d(TAG, "syncCacheWithServer: loaded ${groups.size} groups")
                } catch (e: Exception) {
                    Log.w(TAG, "syncCacheWithServer: failed to load groups", e)
                }
            }
            
            if (usersApi != null) {
                try {
                    users = usersApi.getUsers()
                    Log.d(TAG, "syncCacheWithServer: loaded ${users.size} users")
                } catch (e: Exception) {
                    Log.w(TAG, "syncCacheWithServer: failed to load users", e)
                }
            }
            
            // 9. Возврат результата
            val result = SyncCacheResult(
                cacheUpdated = cacheUpdated,
                users = users,
                groups = groups
            )
            Log.d(TAG, "syncCacheWithServer: completed successfully, cacheUpdated=$cacheUpdated")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "syncCacheWithServer: error during sync", e)
            Result.failure(e)
        }
    }
    
    /**
     * Вычисляет SHA-256 хеш списка задач для сравнения версий.
     * 
     * Хеш вычисляется на основе ключевых полей задач: id, title, 
     * reminderTime, completed, active. Задачи сортируются по ID для 
     * консистентного хеширования.
     * 
     * @param tasks Список задач для хеширования
     * @return SHA-256 хеш в виде hex-строки
     */
    private fun calculateTasksHash(tasks: List<Task>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        // Сортировка задач по ID для консистентного хеширования
        val sortedTasks = tasks.sortedBy { it.id }
        val data = sortedTasks.joinToString("|") { task ->
            "${task.id}:${task.title}:${task.reminderTime}:${task.completed}:${task.active}"
        }
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

