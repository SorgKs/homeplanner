package com.homeplanner.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.homeplanner.api.TasksApi
import com.homeplanner.database.entity.SyncQueueItem
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository
import org.json.JSONObject
import kotlinx.coroutines.delay

/**
 * Сервис синхронизации очереди операций с сервером.
 * Обрабатывает экспоненциальную задержку, конфликты ревизий и ошибки.
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
            val tasks = tasksApi.syncQueue(queueItems)
            
            // Успешная синхронизация: очищаем очередь и обновляем кэш задач
            repository.clearAllQueue()
            if (tasks.isNotEmpty()) {
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
}

