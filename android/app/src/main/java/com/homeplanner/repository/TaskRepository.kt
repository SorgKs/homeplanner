package com.homeplanner.repository

import android.util.Log
import com.homeplanner.database.AppDatabase
import com.homeplanner.database.entity.TaskEntity
import com.homeplanner.model.Task
import com.homeplanner.utils.HashCalculator
import kotlinx.coroutines.flow.first

/**
 * Репозиторий для работы с задачами в локальном хранилище.
 */
class TaskRepository(
    private val db: AppDatabase,
    private val context: android.content.Context
) {
    private val taskDao = db.taskDao()

    companion object {
        private const val TAG = "TaskRepository"
        private const val CACHE_RETENTION_DAYS = 7L
        private const val CACHE_LIMIT_BYTES = 20L * 1024 * 1024 // 20 МБ
    }

    suspend fun saveTasksToCache(tasks: List<Task>): Result<Unit> {
        return try {
            Log.d(TAG, "saveTasksToCache: saving ${tasks.size} tasks")
            val cacheTasks = tasks.map { task ->
                val hash = HashCalculator.calculateTaskHash(task)
                TaskEntity.fromTask(task, hash)
            }
            taskDao.insertTasks(cacheTasks)

            // Обновление lastAccessed для сохраненных задач
            tasks.forEach { task ->
                taskDao.updateLastAccessed(task.id)
            }

            cleanupOldCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadTasksFromCache(): List<Task> {
        return try {
            // Загрузка всех задач из базы
            // Используем Flow.first() для получения первого значения
            val allCacheTasks = taskDao.getAllTasks().first()
            Log.d(TAG, "loadTasksFromCache: loaded ${allCacheTasks.size} tasks from database")
            // DEBUG: Проверяем количество задач в базе
            val taskCount = taskDao.getTaskCount()
            Log.d(TAG, "loadTasksFromCache: total task count in DB: $taskCount")

            // Обновление lastAccessed для загруженных задач
            allCacheTasks.forEach { taskCache ->
                taskDao.updateLastAccessed(taskCache.id)
            }

            // Очистка старых задач
            val cutoffTime = System.currentTimeMillis() - (CACHE_RETENTION_DAYS * 24 * 60 * 60 * 1000)
            taskDao.deleteOldTasks(cutoffTime)

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
            val cacheTask = taskDao.getTaskById(id)
            cacheTask?.let {
                taskDao.updateLastAccessed(id)
                it.toTask()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting task from cache", e)
            null
        }
    }

    suspend fun deleteTaskFromCache(id: Int) {
        try {
            taskDao.deleteTaskById(id)
            Log.d(TAG, "Deleted task from cache: id=$id")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting task from cache", e)
        }
    }

    suspend fun getCachedTasksCount(): Int {
        return try {
            taskDao.getTaskCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cached tasks count", e)
            0
        }
    }

    suspend fun clearAllCache() {
        try {
            taskDao.deleteAllTasks()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }

    private suspend fun cleanupOldCache() {
        try {
            val cutoffTime = System.currentTimeMillis() - (CACHE_RETENTION_DAYS * 24 * 60 * 60 * 1000)
            taskDao.deleteOldTasks(cutoffTime)

            // Проверка лимита размера кэша
            val cacheSize = taskDao.getCacheSizeBytes() ?: 0L
            if (cacheSize > CACHE_LIMIT_BYTES) {
                // Удаление самых старых задач по LRU
                val oldestTasks = taskDao.getOldestTasks(0, 10)
                oldestTasks.forEach { task ->
                    taskDao.deleteTask(task)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old cache", e)
        }
    }
}