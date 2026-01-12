// В android приложении для логирования используется ИСКЛЮЧИТЕЛЬНО бинарный лог
package com.homeplanner.sync

import android.content.Context
import com.homeplanner.api.GroupsServerApi
import com.homeplanner.api.ServerApi
import com.homeplanner.api.UserSummary
import com.homeplanner.api.UsersServerApi
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Сервис для синхронизации кэша с сервером.
 */
class CacheSyncService(
    private val repository: OfflineRepository,
    private val serverApi: ServerApi
) {
    private val queueSyncService = QueueSyncService(repository, serverApi)

    companion object {
        private const val TAG = "CacheSyncService"
    }

    /**
     * Синхронизация кэша с сервером.
     *
     * Алгоритм:
     * 1. Проверка наличия интернета
     * 2. Запрос данных с сервера через ServerApi.getTasks()
     *    - Если запрос не удался → возврат ошибки (не тратим ресурсы на вычисление хеша кэша)
     * 3. Загрузка данных из кэша для сравнения
     * 4. Вычисление хеша кэшированных данных
     * 5. Вычисление хеша данных с сервера
     * 6. Сравнение хешей и обновление кэша при различиях
     * 7. Синхронизация очереди операций
     * 8. Опционально: загрузка групп и пользователей (если переданы API)
     * 9. Возврат результата
     *
     * Оптимизация: Сначала запрашиваем данные с сервера, и только если запрос успешен,
     * вычисляем хеш кэша. Это позволяет избежать лишних вычислений при ошибках сети.
     *
     * @param groupsApi Опциональный API для загрузки групп
     * @param usersApi Опциональный API для загрузки пользователей
     * @return Результат синхронизации с флагом обновления кэша и опциональными данными
     */
    suspend fun syncCacheWithServer(
        groupsApi: GroupsServerApi? = null,
        usersApi: UsersServerApi? = null
    ): Result<SyncCacheResult> {
        android.util.Log.d(TAG, "syncCacheWithServer: started with groupsApi=$groupsApi, usersApi=$usersApi")
        // Реальный статус соединения контролируется в MainActivity через connectionStatus
        // на основе реальных попыток соединения с сервером (ONLINE/DEGRADED/OFFLINE).
        // Здесь просто пытаемся синхронизироваться, ошибки будут обработаны в MainActivity.

        return try {
            android.util.Log.d(TAG, "syncCacheWithServer: calling syncQueue()")
            // syncCacheWithServer: вызов syncQueue()
            // Единый код синхронизации: просто вызываем syncQueue()
            // В онлайн режиме он работает сразу, в оффлайн - вернет ошибку, но при восстановлении сети сработает так же
            // syncQueue() сам обновит кэш актуальными данными с сервера после применения операций
            val queueSyncResult = syncQueue()
            // syncCacheWithServer: результат syncQueue()

            android.util.Log.d(TAG, "syncCacheWithServer: syncQueue result: success=${queueSyncResult.isSuccess}")
            val syncResult = queueSyncResult.getOrNull()
            android.util.Log.d(TAG, "syncCacheWithServer: syncResult: $syncResult")
            if (syncResult != null) {
                android.util.Log.d(TAG, "syncCacheWithServer: successCount=${syncResult.successCount}, tasks.size=${syncResult.tasks.size}")
            }

            // Если syncQueue() успешно выполнился и были операции, кэш уже обновлен актуальными данными с сервера
            // Если очередь была пуста или вернул ошибку, загружаем задачи с сервера для полной синхронизации
            val cacheUpdated = if (queueSyncResult.isSuccess) {
                val queueHasTasks = syncResult != null && syncResult.successCount > 0 && syncResult.tasks.isNotEmpty()
                android.util.Log.d(TAG, "syncCacheWithServer: queueHasTasks=$queueHasTasks, successCount=${syncResult?.successCount}, tasks.size=${syncResult?.tasks?.size}")
                if (queueHasTasks) {
                    // Кэш уже обновлен syncQueue()
                    true
                } else {
                    // Очередь была пуста или syncQueue не вернул задачи - загружаем задачи с сервера для полной синхронизации
                    try {
                        android.util.Log.d(TAG, "syncCacheWithServer: loading tasks from server for full sync")
                        android.util.Log.d(TAG, "syncCacheWithServer: calling getTasksServer(enabledOnly = false)")
                        val serverTasks = serverApi.getTasksServer(enabledOnly = true).getOrThrow()
                        android.util.Log.d(TAG, "syncCacheWithServer: loaded ${serverTasks.size} tasks from server")
                        val cachedTasks = repository.loadTasksFromCache()
                        android.util.Log.d(TAG, "Cached tasks: ${cachedTasks.size}, server tasks: ${serverTasks.size}")
                        val cachedHash = calculateTasksHash(cachedTasks)
                        val serverHash = calculateTasksHash(serverTasks)
                        android.util.Log.d(TAG, "Cached hash: $cachedHash, server hash: $serverHash")

                        if (cachedHash != serverHash) {
                            repository.saveTasksToCache(serverTasks)
                            android.util.Log.d(TAG, "Full sync: hashes differ, cache updated with ${serverTasks.size} tasks")
                            true
                        } else {
                            // syncCacheWithServer: хеши совпадают, кэш не обновлен
                            android.util.Log.d(TAG, "Full sync: hashes match, no cache update needed")
                            false
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Full sync failed", e)
                        false
                    }
                }
            } else {
                // syncCacheWithServer: синхронизация очереди завершилась с ошибкой, но продолжаем для загрузки групп/пользователей
                false
            }

            // Опционально: загрузка групп и пользователей
            var groups: Map<Int, String>? = null
            var users: List<UserSummary>? = null

            if (groupsApi != null) {
                try {
                    groups = groupsApi.getAll()
                } catch (e: Exception) {
                }
            }

            if (usersApi != null) {
                // syncCacheWithServer: вызов загрузки пользователей с сервера
                try {
                    users = usersApi.getUsers()
                    // syncCacheWithServer: получен ответ от сервера с пользователями
                    // syncCacheWithServer: загружены пользователи

                    // Save users to cache
                    if (users != null && users.isNotEmpty()) {
                        val userModels = users.map { summary ->
                            com.homeplanner.model.User(
                                id = summary.id,
                                name = summary.name
                            )
                        }
                        repository.saveUsersToCache(userModels)
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to sync users from server", e)
                }
            }

            // Возврат результата
            val result = SyncCacheResult(
                cacheUpdated = cacheUpdated,
                users = users,
                groups = groups
            )
            // syncCacheWithServer: успешно завершено
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Синхронизация очереди операций.
     */
    private suspend fun syncQueue(): Result<SyncResult> = queueSyncService.syncQueue()

    /**
     * Вычисляет SHA-256 хеш списка задач для сравнения версий.
     *
     * Хеш вычисляется на основе ключевых полей задач: id, title,
     * reminderTime, completed, active. Задачи сортируются по ID для
     * консистентного хеширования.
     *
     * @param tasks Список задач для хеширования
     * @return SHA-256 хеш в виде hex-строки
     */
    private fun calculateTasksHash(tasks: List<Task>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        // Сортировка задач по ID для консистентного хеширования
        val sortedTasks = tasks.sortedBy { it.id }
        val data = sortedTasks.joinToString("|") { task ->
            "${task.id}:${task.title}:${task.reminderTime}:${task.completed}:${task.enabled}"
        }
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}