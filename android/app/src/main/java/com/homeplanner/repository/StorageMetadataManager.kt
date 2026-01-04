package com.homeplanner.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.homeplanner.database.AppDatabase

/**
 * Менеджер для управления метаданными хранилища.
 */
class StorageMetadataManager(
    private val db: AppDatabase,
    private val context: Context
) {
    private val taskCacheDao = db.taskCacheDao()
    private val syncQueueDao = db.syncQueueDao()
    private val metadataDao = db.metadataDao()

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "StorageMetadataManager"
        private const val STORAGE_LIMIT_BYTES = 25L * 1024 * 1024 // 25 МБ
    }

    suspend fun updateStorageMetadata() {
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

    suspend fun getCacheSizeBytes(): Long {
        return try {
            prefs.getLong("cache_size_bytes", 0L)
        } catch (e: Exception) {
            0L
        }
    }
}