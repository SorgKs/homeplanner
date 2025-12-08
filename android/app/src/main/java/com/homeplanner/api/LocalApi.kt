package com.homeplanner.api

import android.util.Log
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository

/**
 * API для работы с локальным хранилищем для UI.
 * 
 * Предоставляет данные из локального кэша (Room database) с автоматическим 
 * добавлением операций в очередь синхронизации. Все методы работают только 
 * с локальным хранилищем и не выполняют запросы к серверу.
 * 
 * Особенности:
 * - Все методы работают только с локальным хранилищем
 * - Не выполняет запросы к серверу
 * - Все изменения автоматически добавляются в очередь синхронизации
 * - Возвращает данные немедленно (оптимистичные обновления)
 * 
 * Синхронизация с сервером должна выполняться явно через SyncService.
 */
class LocalApi(
    private val offlineRepository: OfflineRepository
) {
    companion object {
        private const val TAG = "LocalApi"
    }
    
    /**
     * Загружает задачи из локального кэша.
     * 
     * @param activeOnly Если true, возвращает только активные задачи (используется для фильтрации на стороне UI)
     * @return Список задач из кэша
     */
    suspend fun getTasks(activeOnly: Boolean = true): List<Task> {
        val cachedTasks = offlineRepository.loadTasksFromCache()
        Log.d(TAG, "Loaded ${cachedTasks.size} tasks from cache")
        return cachedTasks
    }
    
    /**
     * Создаёт задачу локально и добавляет операцию в очередь синхронизации.
     * 
     * Задача сохраняется в кэш немедленно для мгновенного отображения в UI.
     * Операция создания добавляется в очередь синхронизации для отправки на сервер.
     * 
     * @param task Задача для создания
     * @param assignedUserIds Список ID пользователей, которым назначена задача
     * @return Созданная задача (оптимистичное обновление)
     */
    suspend fun createTask(task: Task, assignedUserIds: List<Int> = emptyList()): Task {
        // 1. Сразу сохраняем в кэш для немедленного отображения
        offlineRepository.saveTasksToCache(listOf(task))
        Log.d(TAG, "Created task locally: id=${task.id}, title=${task.title}")
        
        // 2. Добавляем в очередь синхронизации
        offlineRepository.addToSyncQueue("create", "task", null, task)
        
        // 3. Возвращаем задачу немедленно (оптимистичное обновление)
        return task
    }
    
    /**
     * Обновляет задачу локально и добавляет операцию в очередь синхронизации.
     * 
     * Задача обновляется в кэше немедленно для мгновенного отображения в UI.
     * Операция обновления добавляется в очередь синхронизации для отправки на сервер.
     * 
     * @param taskId ID задачи для обновления
     * @param task Обновлённая задача
     * @param assignedUserIds Список ID пользователей, которым назначена задача
     * @return Обновлённая задача (оптимистичное обновление)
     */
    suspend fun updateTask(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Task {
        // 1. Сразу обновляем в кэше для немедленного отображения
        offlineRepository.saveTasksToCache(listOf(task))
        Log.d(TAG, "Updated task locally: id=$taskId, title=${task.title}, reminderTime=${task.reminderTime}")
        
        // 2. Добавляем в очередь синхронизации
        offlineRepository.addToSyncQueue("update", "task", taskId, task)
        
        // 3. Возвращаем задачу немедленно (оптимистичное обновление)
        return task
    }
    
    /**
     * Отмечает задачу как выполненную локально и добавляет операцию в очередь синхронизации.
     * 
     * Статус задачи обновляется в кэше немедленно для мгновенного отображения в UI.
     * Операция выполнения добавляется в очередь синхронизации для отправки на сервер.
     * 
     * @param taskId ID задачи для выполнения
     * @return Обновлённая задача с completed=true (оптимистичное обновление)
     * @throws Exception Если задача не найдена в кэше
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
        
        // 3. Возвращаем задачу немедленно (оптимистичное обновление)
        return updatedTask
    }
    
    /**
     * Отменяет выполнение задачи локально и добавляет операцию в очередь синхронизации.
     * 
     * Статус задачи обновляется в кэше немедленно для мгновенного отображения в UI.
     * Операция отмены выполнения добавляется в очередь синхронизации для отправки на сервер.
     * 
     * @param taskId ID задачи для отмены выполнения
     * @return Обновлённая задача с completed=false (оптимистичное обновление)
     * @throws Exception Если задача не найдена в кэше
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
        
        // 3. Возвращаем задачу немедленно (оптимистичное обновление)
        return updatedTask
    }
    
    /**
     * Удаляет задачу локально и добавляет операцию в очередь синхронизации.
     * 
     * Задача удаляется из кэша немедленно для мгновенного отображения в UI.
     * Операция удаления добавляется в очередь синхронизации для отправки на сервер.
     * 
     * @param taskId ID задачи для удаления
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
    }
}
