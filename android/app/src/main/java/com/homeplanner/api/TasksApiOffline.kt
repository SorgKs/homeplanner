package com.homeplanner.api

import android.content.Context
import android.util.Log
import com.homeplanner.database.AppDatabase
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.sync.SyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Обертка над TasksApi с поддержкой offline-first стратегии.
 * Всегда работает с локальным кэшем, синхронизация с сервером происходит в фоне.
 */
class TasksApiOffline(
    private val tasksApi: TasksApi,
    private val offlineRepository: OfflineRepository,
    private val syncService: SyncService,
    private val scope: CoroutineScope,
    private val onSyncStateChanged: (Boolean) -> Unit = {},
    private val onSyncSuccess: (() -> Unit)? = null // Callback для уведомления об успешной синхронизации
) {
    companion object {
        private const val TAG = "TasksApiOffline"
    }
    
    /**
     * Offline-first стратегия: всегда сначала загружаем из кэша для мгновенного отображения,
     * затем синхронизируем с сервером в фоне.
     */
    suspend fun getTasks(activeOnly: Boolean = true): List<Task> {
        // 1. Всегда сначала загружаем из кэша для мгновенного отображения
        val cachedTasks = offlineRepository.loadTasksFromCache()
        Log.d(TAG, "Loaded ${cachedTasks.size} tasks from cache (offline-first)")
        
        // 2. Синхронизация с сервером в фоне (не блокирует UI)
        scope.launch(Dispatchers.IO) {
            try {
                if (syncService.isOnline()) {
                    Log.d(TAG, "Background sync: fetching from server")
                    onSyncStateChanged(true)
                    try {
                        // Загрузка с сервера
                        val serverTasks = tasksApi.getTasks(activeOnly)
                        offlineRepository.saveTasksToCache(serverTasks)
                        Log.d(TAG, "Background sync: saved ${serverTasks.size} tasks from server")
                        
                        // Синхронизация очереди операций
                        val syncResult = syncService.syncQueue()
                        if (syncResult.isSuccess) {
                            Log.d(TAG, "Background sync: queue synced successfully")
                            onSyncSuccess?.invoke()
                        } else {
                            Log.w(TAG, "Background sync: queue sync had failures")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Background sync failed, cache remains", e)
                    } finally {
                        onSyncStateChanged(false)
                    }
                } else {
                    Log.d(TAG, "Background sync: offline, skipping server fetch")
                    // Даже в оффлайне пытаемся синхронизировать очередь, если есть операции
                    if (offlineRepository.getPendingOperationsCount() > 0) {
                        Log.d(TAG, "Background sync: attempting queue sync despite offline status")
                        try {
                            syncService.syncQueue()
                        } catch (e: Exception) {
                            Log.d(TAG, "Queue sync failed (expected in offline)", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background sync error", e)
                onSyncStateChanged(false)
            }
        }
        
        // 3. Возвращаем данные из кэша немедленно
        return cachedTasks
    }
    
    /**
     * Offline-first: пытаемся получить из кэша, затем синхронизируем в фоне.
     * В оффлайне возвращаем пустой список (фильтрация будет по кэшированным данным).
     */
    suspend fun getTodayTaskIds(): List<Int> {
        // В оффлайн-first режиме фильтрация "сегодня" должна работать на основе кэшированных данных
        // Этот метод используется только для синхронизации списка ID с сервером
        return try {
            if (syncService.isOnline()) {
                val ids = tasksApi.getTodayTaskIds()
                // Сохраняем для использования в оффлайне (можно сохранить в SharedPreferences или кэше)
                Log.d(TAG, "Got ${ids.size} today task IDs from server")
                ids
            } else {
                // В оффлайне возвращаем пустой список - фильтрация будет по кэшированным данным
                Log.d(TAG, "Offline: returning empty today IDs list (filtering will use cache)")
                emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get today task IDs, returning empty", e)
            emptyList()
        }
    }
    
    /**
     * Offline-first: всегда сохраняем локально, затем синхронизируем в фоне.
     */
    suspend fun createTask(task: Task, assignedUserIds: List<Int> = emptyList()): Task {
        // 1. Сразу сохраняем в кэш для немедленного отображения
        offlineRepository.saveTasksToCache(listOf(task))
        Log.d(TAG, "Created task locally: id=${task.id}, title=${task.title}")
        
        // 2. Добавляем в очередь синхронизации
        offlineRepository.addToSyncQueue("create", "task", null, task)
        
        // 3. Синхронизация в фоне (не блокирует UI)
        scope.launch(Dispatchers.IO) {
            try {
                if (syncService.isOnline()) {
                    Log.d(TAG, "Background sync: creating task on server")
                    val response = tasksApi.createTask(task, assignedUserIds)
                    // Обновляем кэш с данными с сервера (может содержать ID, revision и т.д.)
                    offlineRepository.saveTasksToCache(listOf(response))
                    // Удаляем из очереди после успешной синхронизации
                    // (SyncService должен это делать, но на всякий случай)
                    Log.d(TAG, "Background sync: task created on server, id=${response.id}")
                } else {
                    Log.d(TAG, "Background sync: offline, task queued for later sync")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background sync: failed to create task on server, will retry later", e)
                // Задача остается в очереди для повторной попытки
            }
        }
        
        // 4. Возвращаем задачу немедленно (оптимистичное обновление)
        return task
    }
    
    /**
     * Offline-first: всегда обновляем локально, затем синхронизируем в фоне.
     */
    suspend fun updateTask(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Task {
        // 1. Сразу обновляем в кэше для немедленного отображения
        offlineRepository.saveTasksToCache(listOf(task))
        Log.d(TAG, "Updated task locally: id=$taskId, title=${task.title}")
        
        // 2. Добавляем в очередь синхронизации
        offlineRepository.addToSyncQueue("update", "task", taskId, task, task.revision)
        
        // 3. Синхронизация в фоне (не блокирует UI)
        scope.launch(Dispatchers.IO) {
            try {
                if (syncService.isOnline()) {
                    Log.d(TAG, "Background sync: updating task on server")
                    val response = tasksApi.updateTask(taskId, task, assignedUserIds)
                    // Обновляем кэш с данными с сервера
                    offlineRepository.saveTasksToCache(listOf(response))
                    Log.d(TAG, "Background sync: task updated on server")
                } else {
                    Log.d(TAG, "Background sync: offline, task queued for later sync")
                }
            } catch (e: IllegalStateException) {
                // Проверка на конфликт ревизий (HTTP 409)
                if (e.message?.contains("409") == true || e.message?.contains("conflict") == true) {
                    Log.w(TAG, "Revision conflict detected for task $taskId, will be resolved on server/web")
                } else {
                    Log.w(TAG, "Background sync: failed to update task, will retry later", e)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background sync: failed to update task, will retry later", e)
            }
        }
        
        // 4. Возвращаем задачу немедленно (оптимистичное обновление)
        return task
    }
    
    /**
     * Offline-first: всегда обновляем локально, затем синхронизируем в фоне.
     */
    suspend fun completeTask(taskId: Int): Task {
        // 1. Сразу обновляем в кэше для немедленного отображения
        val cachedTask = offlineRepository.getTaskFromCache(taskId)
            ?: throw Exception("Task not found in cache: id=$taskId")
        val updatedTask = cachedTask.copy(completed = true)
        offlineRepository.saveTasksToCache(listOf(updatedTask))
        Log.d(TAG, "Completed task locally: id=$taskId")
        
        // 2. Добавляем в очередь синхронизации
        offlineRepository.addToSyncQueue("complete", "task", taskId)
        
        // 3. Синхронизация в фоне (не блокирует UI)
        scope.launch(Dispatchers.IO) {
            try {
                if (syncService.isOnline()) {
                    Log.d(TAG, "Background sync: completing task on server")
                    val response = tasksApi.completeTask(taskId)
                    offlineRepository.saveTasksToCache(listOf(response))
                    Log.d(TAG, "Background sync: task completed on server")
                } else {
                    Log.d(TAG, "Background sync: offline, task queued for later sync")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background sync: failed to complete task, will retry later", e)
            }
        }
        
        // 4. Возвращаем задачу немедленно (оптимистичное обновление)
        return updatedTask
    }
    
    /**
     * Offline-first: всегда обновляем локально, затем синхронизируем в фоне.
     */
    suspend fun uncompleteTask(taskId: Int): Task {
        // 1. Сразу обновляем в кэше для немедленного отображения
        val cachedTask = offlineRepository.getTaskFromCache(taskId)
            ?: throw Exception("Task not found in cache: id=$taskId")
        val updatedTask = cachedTask.copy(completed = false)
        offlineRepository.saveTasksToCache(listOf(updatedTask))
        Log.d(TAG, "Uncompleted task locally: id=$taskId")
        
        // 2. Добавляем в очередь синхронизации
        offlineRepository.addToSyncQueue("uncomplete", "task", taskId)
        
        // 3. Синхронизация в фоне (не блокирует UI)
        scope.launch(Dispatchers.IO) {
            try {
                if (syncService.isOnline()) {
                    Log.d(TAG, "Background sync: uncompleting task on server")
                    val response = tasksApi.uncompleteTask(taskId)
                    offlineRepository.saveTasksToCache(listOf(response))
                    Log.d(TAG, "Background sync: task uncompleted on server")
                } else {
                    Log.d(TAG, "Background sync: offline, task queued for later sync")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background sync: failed to uncomplete task, will retry later", e)
            }
        }
        
        // 4. Возвращаем задачу немедленно (оптимистичное обновление)
        return updatedTask
    }
    
    /**
     * Offline-first: сразу удаляем из кэша, затем синхронизируем в фоне.
     */
    suspend fun deleteTask(taskId: Int) {
        // 1. Сразу удаляем из кэша для немедленного отображения
        try {
            offlineRepository.deleteTaskFromCache(taskId)
            Log.d(TAG, "Deleted task locally: id=$taskId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete task from cache", e)
        }
        
        // 2. Добавляем в очередь синхронизации
        offlineRepository.addToSyncQueue("delete", "task", taskId)
        
        // 3. Синхронизация в фоне (не блокирует UI)
        scope.launch(Dispatchers.IO) {
            try {
                if (syncService.isOnline()) {
                    Log.d(TAG, "Background sync: deleting task on server")
                    tasksApi.deleteTask(taskId)
                    Log.d(TAG, "Background sync: task deleted on server")
                } else {
                    Log.d(TAG, "Background sync: offline, task queued for later sync")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background sync: failed to delete task, will retry later", e)
            }
        }
    }
}

