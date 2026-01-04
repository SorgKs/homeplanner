package com.homeplanner.repository

import android.util.Log
import com.homeplanner.database.AppDatabase
import com.homeplanner.database.entity.SyncQueueItem
import com.homeplanner.model.Task
import com.homeplanner.utils.TaskUtils
import org.json.JSONObject

/**
 * Репозиторий для работы с очередью синхронизации.
 */
class SyncQueueRepository(
    private val db: AppDatabase
) {
    private val syncQueueDao = db.syncQueueDao()

    companion object {
        private const val TAG = "SyncQueueRepository"
        private const val QUEUE_LIMIT_BYTES = 5L * 1024 * 1024 // 5 МБ
    }

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
                    is Task -> TaskUtils.taskToJson(payload)
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

            // Проверка лимита очереди по объему
            val currentSize = syncQueueDao.getTotalSizeBytes() ?: 0L

            if (currentSize + sizeBytes > QUEUE_LIMIT_BYTES) {
                // Освобождение места в очереди
                freeQueueSpace(sizeBytes, QUEUE_LIMIT_BYTES)
            }

            val id = syncQueueDao.insertQueueItem(item)

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
        } catch (e: Exception) {
            Log.e(TAG, "Error updating queue item", e)
        }
    }

    suspend fun removeFromQueue(id: Long) {
        try {
            syncQueueDao.deleteQueueItemById(id)
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

    suspend fun clearAllQueue() {
        try {
            syncQueueDao.deleteAllQueueItems()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing queue", e)
        }
    }
}