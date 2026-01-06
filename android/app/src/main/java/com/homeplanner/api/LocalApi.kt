package com.homeplanner.api

import com.homeplanner.model.Task
import com.homeplanner.model.Group
import com.homeplanner.model.User
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.utils.TaskDateCalculator
import com.homeplanner.debug.BinaryLogger

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
    private val offlineRepository: OfflineRepository,
    private val taskDateCalculator: TaskDateCalculator
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
    suspend fun getTasksLocal(activeOnly: Boolean = true): Result<List<Task>> = runCatching {
        val cachedTasks = offlineRepository.loadTasksFromCache()
        // Загружены задачи из кэша
        BinaryLogger.getInstance()?.log(200u, listOf<Any>(cachedTasks.size, 23), 23)
        cachedTasks
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
    suspend fun createTaskLocal(task: Task, assignedUserIds: List<Int> = emptyList()): Result<Task> = runCatching {
        // 1. Сразу сохраняем в кэш для немедленного отображения
        offlineRepository.saveTasksToCache(listOf(task))
        // Создана задача
        BinaryLogger.getInstance()?.log(
            20u, listOf<Any>(task.id,task.title, 23), 23
        )

        // 2. Добавляем в очередь синхронизации
        addToSyncQueue("create", "task", null, task)

        // 3. Возвращаем задачу немедленно (оптимистичное обновление)
        task
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
    suspend fun updateTaskLocal(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Result<Task> = runCatching {
        // 1. Сразу обновляем в кэше для немедленного отображения
        offlineRepository.saveTasksToCache(listOf(task))
        // Задача обновлена
        BinaryLogger.getInstance()?.log(
            21u, listOf<Any>(taskId,task.title, 23), 23
        )

        // 2. Добавляем в очередь синхронизации
        addToSyncQueue("update", "task", taskId, task)

        // 3. Возвращаем задачу немедленно (оптимистичное обновление)
        task
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
    suspend fun completeTaskLocal(taskId: Int): Result<Task> = runCatching {
        // 1. Сразу обновляем в кэше для немедленного отображения
        val cachedTask = offlineRepository.getTaskFromCache(taskId)
            ?: throw Exception("Task not found in cache: id=$taskId")
        val updatedTask = cachedTask.copy(completed = true)
        offlineRepository.saveTasksToCache(listOf(updatedTask))
        // Задача выполнена
        BinaryLogger.getInstance()?.log(
            22u, listOf<Any>(taskId,updatedTask.title, 23), 23
        )

        // 2. Добавляем в очередь синхронизации
        addToSyncQueue("complete", "task", taskId, null)

        // 3. Возвращаем задачу немедленно (оптимистичное обновление)
        updatedTask
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
    suspend fun uncompleteTaskLocal(taskId: Int): Result<Task> = runCatching {
        // 1. Сразу обновляем в кэше для немедленного отображения
        val cachedTask = offlineRepository.getTaskFromCache(taskId)
            ?: throw Exception("Task not found in cache: id=$taskId")
        val updatedTask = cachedTask.copy(completed = false)
        offlineRepository.saveTasksToCache(listOf(updatedTask))
        // Выполнение задачи отменено
        BinaryLogger.getInstance()?.log(
            24u, listOf<Any>(taskId, 23), 23
        )

        // 2. Добавляем в очередь синхронизации
        addToSyncQueue("uncomplete", "task", taskId, null)

        // 3. Возвращаем задачу немедленно (оптимистичное обновление)
        updatedTask
    }
    
    /**
     * Удаляет задачу локально и добавляет операцию в очередь синхронизации.
     * 
     * Задача удаляется из кэша немедленно для мгновенного отображения в UI.
     * Операция удаления добавляется в очередь синхронизации для отправки на сервер.
     * 
     * @param taskId ID задачи для удаления
     */
    suspend fun deleteTaskLocal(taskId: Int): Result<Unit> = runCatching {
        // 1. Сразу удаляем из кэша для немедленного отображения
        try {
            offlineRepository.deleteTaskFromCache(taskId)
            // Задача удалена
            BinaryLogger.getInstance()?.log(
                23u, listOf<Any>(taskId, 23), 23
            )
        } catch (e: Exception) {
            // Исключение: ожидалось %wait%, фактически %fact%
            BinaryLogger.getInstance()?.log(
                91u, listOf<Any>(e.message ?: "Unknown error",e::class.simpleName ?: "Unknown", 23), 23
            )
            throw e
        }

        // 2. Добавляем в очередь синхронизации
        addToSyncQueue("delete", "task", taskId, null)
    }

    // === Группы ===
    suspend fun getGroupsLocal(): Result<List<Group>> = runCatching {
        offlineRepository.loadGroupsFromCache()
    }

    suspend fun createGroupLocal(group: Group): Result<Group> = runCatching {
        offlineRepository.saveGroupsToCache(listOf(group))
        addToSyncQueue("create", "group", null, group)
        group
    }

    suspend fun updateGroupLocal(groupId: Int, group: Group): Result<Group> = runCatching {
        offlineRepository.saveGroupsToCache(listOf(group))
        addToSyncQueue("update", "group", groupId, group)
        group
    }

    suspend fun deleteGroupLocal(groupId: Int): Result<Unit> = runCatching {
        offlineRepository.deleteGroupFromCache(groupId)
        addToSyncQueue("delete", "group", groupId, null)
    }

    // === Пользователи ===
    suspend fun getUsersLocal(): Result<List<User>> = runCatching {
        offlineRepository.loadUsersFromCache()
    }

    suspend fun createUserLocal(user: User): Result<User> = runCatching {
        offlineRepository.saveUsersToCache(listOf(user))
        addToSyncQueue("create", "user", null, user)
        user
    }

    suspend fun updateUserLocal(userId: Int, user: User): Result<User> = runCatching {
        offlineRepository.saveUsersToCache(listOf(user))
        addToSyncQueue("update", "user", userId, user)
        user
    }

    suspend fun deleteUserLocal(userId: Int): Result<Unit> = runCatching {
        offlineRepository.deleteUserFromCache(userId)
        addToSyncQueue("delete", "user", userId, null)
    }

    // === Внутренние методы ===
    private suspend fun updateRecurringTasksIfNeeded(dayStartHour: Int) {
        val updated = offlineRepository.updateRecurringTasksForNewDay(dayStartHour)
        if (updated) {
            // TODO: Обновить UI или уведомить о изменениях
        }
    }

    private suspend fun addToSyncQueue(action: String, entityType: String, entityId: Int?, entity: Any?) {
        offlineRepository.addToSyncQueue(action, entityType, entityId, entity)
        offlineRepository.requestSync = true
    }
}
