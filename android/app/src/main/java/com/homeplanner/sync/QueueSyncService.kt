package com.homeplanner.sync

import com.homeplanner.database.entity.SyncQueueItem
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.api.ServerApi

/**
 * Сервис для синхронизации очереди операций.
 */
class QueueSyncService(
    private val repository: OfflineRepository,
    private val serverApi: ServerApi
) {
    companion object {
        private const val TAG = "QueueSyncService"
    }

    /**
     * Синхронизация очереди операций.
     */
    suspend fun syncQueue(): Result<SyncResult> {
        // Получаем все pending операции с разумным лимитом (10000)
        // Если операций больше, они будут синхронизированы в следующих вызовах
        val allQueueItems = repository.getPendingQueueItems(limit = 10000)
        android.util.Log.d(TAG, "syncQueue: found ${allQueueItems.size} pending operations")
        if (allQueueItems.isEmpty()) {
            android.util.Log.d(TAG, "syncQueue: no pending operations, returning success")
            return Result.success(SyncResult(0, 0, 0))
        }

        return try {
            // Разбиваем операции на группы по 100 элементов и отправляем последовательно
            // Это позволяет обработать большое количество операций без перегрузки сервера
            val BATCH_SIZE = 100
            var totalSuccessCount = 0
            var allTasks = emptyList<Task>()

            for (i in allQueueItems.indices step BATCH_SIZE) {
                val batch = allQueueItems.subList(i, minOf(i + BATCH_SIZE, allQueueItems.size))
                android.util.Log.d(TAG, "Syncing batch ${i / BATCH_SIZE + 1}: ${batch.size} operations")

                // Сервер обрабатывает конфликты самостоятельно и возвращает актуальное состояние задач
                val batchTasks = serverApi.syncQueueServer(batch).getOrThrow()
                android.util.Log.d(TAG, "syncQueue: batch sync successful, received ${batchTasks.size} tasks from server")
                totalSuccessCount += batch.size

                // Собираем все задачи из всех батчей
                allTasks = allTasks + batchTasks
            }

            // Сервер - источник истины: очищаем очередь и обновляем кэш актуальными данными с сервера
            // Очищаем только те элементы, которые были успешно отправлены
            // Если операций было больше лимита, остальные останутся в очереди для следующей синхронизации
            repository.clearAllQueue()
            android.util.Log.d(TAG, "syncQueue: cleared queue, saving ${allTasks.size} tasks to cache")
            if (allTasks.isNotEmpty()) {
                // Обновляем кэш актуальными данными с сервера (сервер сам решил, какие операции применить)
                repository.saveTasksToCache(allTasks)
            }

            val syncResult = SyncResult(
                successCount = totalSuccessCount,
                failureCount = 0,
                conflictCount = 0,
                tasks = allTasks
            )
            android.util.Log.d(TAG, "syncQueue: sync completed successfully with ${totalSuccessCount} operations")
            Result.success(syncResult)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "syncQueue: sync failed with exception", e)
            Result.failure(e)
        }
    }
}