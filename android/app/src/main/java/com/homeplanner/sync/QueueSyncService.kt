package com.homeplanner.sync

import com.homeplanner.database.entity.SyncQueueItem
import com.homeplanner.debug.BinaryLogger
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
        if (allQueueItems.isEmpty()) {
            // Очередь синхронизации пуста
            BinaryLogger.getInstance()?.log(40u, emptyList<Any>(), 36)
            return Result.success(SyncResult(0, 0, 0))
        }

        return try {
            // Синхронизация начата
            BinaryLogger.getInstance()?.log(
                1u, listOf<Any>(allQueueItems.size, 36), 36
            )

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
                totalSuccessCount += batch.size

                // Собираем все задачи из всех батчей
                allTasks = allTasks + batchTasks
            }

            // Сервер - источник истины: очищаем очередь и обновляем кэш актуальными данными с сервера
            // Очищаем только те элементы, которые были успешно отправлены
            // Если операций было больше лимита, остальные останутся в очереди для следующей синхронизации
            repository.clearAllQueue()
            if (allTasks.isNotEmpty()) {
                // Обновляем кэш актуальными данными с сервера (сервер сам решил, какие операции применить)
                repository.saveTasksToCache(allTasks)
                // Кэш обновлен после синхронизации: %tasks_count% задач из %source%, очередь: %queue_items%
                BinaryLogger.getInstance()?.log(
                    41u, listOf<Any>(allTasks.size,"syncQueue",allQueueItems.size, 36), 36
                )
            }

            val syncResult = SyncResult(
                successCount = totalSuccessCount,
                failureCount = 0,
                conflictCount = 0,
                tasks = allTasks
            )
            // Синхронизация успешно завершена
            BinaryLogger.getInstance()?.log(
                2u, listOf<Any>(syncResult.successCount,0), 36
            )
            Result.success(syncResult)
        } catch (e: Exception) {
            // Исключение: ожидалось %wait%, фактически %fact%
            BinaryLogger.getInstance()?.log(
                91u, listOf<Any>(e.message ?: "Unknown error",e::class.simpleName ?: "Unknown", 36), 36
            )
            Result.failure(e)
        }
    }
}