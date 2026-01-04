package com.homeplanner.repository

import android.util.Log
import com.homeplanner.database.AppDatabase
import com.homeplanner.database.entity.TaskCache
import com.homeplanner.model.Task
import com.homeplanner.debug.BinaryLogger
import com.homeplanner.debug.LogLevel
import kotlinx.coroutines.flow.first

/**
 * Репозиторий для работы с кэшем задач.
 */
class TaskCacheRepository(
    private val db: AppDatabase
) {
    private val taskCacheDao = db.taskCacheDao()

    companion object {
        private const val TAG = "TaskCacheRepository"
        private const val CACHE_RETENTION_DAYS = 7L
        private const val CACHE_LIMIT_BYTES = 20L * 1024 * 1024 // 20 МБ
    }

    suspend fun saveTasksToCache(tasks: List<Task>): Result<Unit> {
        return try {
            // saveTasksToCache: [STEP 1] Начало сохранения задач в кэш
            BinaryLogger.getInstance()?.log(314u, listOf(tasks.size))
            val cacheTasks = tasks.map { TaskCache.fromTask(it) }
            // saveTasksToCache: [STEP 2] Задачи преобразованы в TaskCache
            BinaryLogger.getInstance()?.log(315u, listOf(cacheTasks.size))
            taskCacheDao.insertTasks(cacheTasks)
            // saveTasksToCache: [STEP 3] Задачи вставлены в базу данных
            BinaryLogger.getInstance()?.log(316u, listOf(cacheTasks.size))

            // Обновление lastAccessed для сохраненных задач
            tasks.forEach { task ->
                taskCacheDao.updateLastAccessed(task.id)
            }
            // saveTasksToCache: [STEP 4] Обновлен lastAccessed для всех задач
            BinaryLogger.getInstance()?.log(317u, listOf(tasks.size))

            cleanupOldCache()
            // saveTasksToCache: [STEP 5] Успешно сохранено задач в кэш
            BinaryLogger.getInstance()?.log(318u, listOf(tasks.size))
            Result.success(Unit)
        } catch (e: Exception) {
            // saveTasksToCache: [ERROR] Ошибка сохранения задач в кэш: %wait%, %fact%
            BinaryLogger.getInstance()?.log(
                91u,
                listOf(e.message ?: "Unknown error", e::class.simpleName ?: "Unknown")
            )
            Result.failure(e)
        }
    }

    suspend fun loadTasksFromCache(): List<Task> {
        return try {
            // Загрузка всех задач из кэша
            // Используем Flow.first() для получения первого значения
            val allCacheTasks = taskCacheDao.getAllTasks().first()
            Log.d(TAG, "loadTasksFromCache: loaded ${allCacheTasks.size} tasks from database")

            // Обновление lastAccessed для загруженных задач
            allCacheTasks.forEach { taskCache ->
                taskCacheDao.updateLastAccessed(taskCache.id)
            }

            // Очистка старых задач
            val cutoffTime = System.currentTimeMillis() - (CACHE_RETENTION_DAYS * 24 * 60 * 60 * 1000)
            taskCacheDao.deleteOldTasks(cutoffTime)

            val result = allCacheTasks.map { it.toTask() }
            Log.d(TAG, "loadTasksFromCache: converted to ${result.size} Task objects")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error loading tasks from cache", e)
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getTaskFromCache(id: Int): Task? {
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

    suspend fun deleteTaskFromCache(id: Int) {
        try {
            taskCacheDao.deleteTaskById(id)
            Log.d(TAG, "Deleted task from cache: id=$id")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting task from cache", e)
        }
    }

    suspend fun getCachedTasksCount(): Int {
        return try {
            taskCacheDao.getTaskCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cached tasks count", e)
            0
        }
    }

    suspend fun clearAllCache() {
        try {
            taskCacheDao.deleteAllTasks()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }

    private suspend fun cleanupOldCache() {
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
}