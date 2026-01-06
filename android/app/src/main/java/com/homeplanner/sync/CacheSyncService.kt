// В android приложении для логирования используется ИСКЛЮЧИТЕЛЬНО бинарный лог
package com.homeplanner.sync

import android.content.Context
import com.homeplanner.api.GroupsServerApi
import com.homeplanner.api.ServerApi
import com.homeplanner.api.UserSummary
import com.homeplanner.api.UsersServerApi
import com.homeplanner.debug.BinaryLogger
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
        // Реальный статус соединения контролируется в MainActivity через connectionStatus
        // на основе реальных попыток соединения с сервером (ONLINE/DEGRADED/OFFLINE).
        // Здесь просто пытаемся синхронизироваться, ошибки будут обработаны в MainActivity.

        // syncCacheWithServer: [STEP 1] Начало синхронизации
        BinaryLogger.getInstance()?.log(303u, emptyList<Any>(), 10)

        return try {
            // syncCacheWithServer: вызов syncQueue()
            BinaryLogger.getInstance()?.log(321u, emptyList<Any>(), 10)
            // Единый код синхронизации: просто вызываем syncQueue()
            // В онлайн режиме он работает сразу, в оффлайн - вернет ошибку, но при восстановлении сети сработает так же
            // syncQueue() сам обновит кэш актуальными данными с сервера после применения операций
            val queueSyncResult = syncQueue()
            // syncCacheWithServer: результат syncQueue()
            BinaryLogger.getInstance()?.log(322u, listOf<Any>(queueSyncResult.isSuccess.toString()), 10)

            android.util.Log.d(TAG, "syncQueue result: success=${queueSyncResult.isSuccess}")
            val syncResult = queueSyncResult.getOrNull()
            android.util.Log.d(TAG, "syncResult: $syncResult")
            if (syncResult != null) {
                android.util.Log.d(TAG, "successCount=${syncResult.successCount}, tasks.size=${syncResult.tasks.size}")
            }

            // Если syncQueue() успешно выполнился и были операции, кэш уже обновлен актуальными данными с сервера
            // Если очередь была пуста или вернул ошибку, загружаем задачи с сервера для полной синхронизации
            val cacheUpdated = if (queueSyncResult.isSuccess) {
                val queueHasTasks = syncResult != null && syncResult.successCount > 0 && syncResult.tasks.isNotEmpty()
                android.util.Log.d(TAG, "syncCacheWithServer: queueHasTasks=$queueHasTasks, successCount=${syncResult?.successCount}, tasks.size=${syncResult?.tasks?.size}")
                BinaryLogger.getInstance()?.log(500u, listOf<Any>(queueHasTasks.toString(), syncResult?.successCount ?: 0, syncResult?.tasks?.size ?: 0), 10)
                if (queueHasTasks) {
                    // Кэш уже обновлен syncQueue()
                    true
                } else {
                    // Очередь была пуста или syncQueue не вернул задачи - загружаем задачи с сервера для полной синхронизации
                    try {
                        // syncCacheWithServer: загрузка задач с сервера для полной синхронизации
                        BinaryLogger.getInstance()?.log(319u, emptyList<Any>(), 10)
                        android.util.Log.d(TAG, "Loading tasks from server for full sync")
                        android.util.Log.d(TAG, "Calling getTasksServer(enabledOnly = false)")
                        val serverTasks = serverApi.getTasksServer(enabledOnly = false).getOrThrow()
                        android.util.Log.d(TAG, "Loaded ${serverTasks.size} tasks from server")
                        val cachedTasks = repository.loadTasksFromCache()
                        android.util.Log.d(TAG, "Cached tasks: ${cachedTasks.size}, server tasks: ${serverTasks.size}")
                        val cachedHash = calculateTasksHash(cachedTasks)
                        val serverHash = calculateTasksHash(serverTasks)
                        android.util.Log.d(TAG, "Cached hash: $cachedHash, server hash: $serverHash")

                        if (cachedHash != serverHash) {
                            repository.saveTasksToCache(serverTasks)
                            // Кэш обновлен после синхронизации: %tasks_count% задач из %source%, очередь: %queue_items%
                            BinaryLogger.getInstance()?.log(
                                41u, listOf<Any>(serverTasks.size,"syncCacheWithServer_full","syncCacheWithServer_full"), 10
                            )
                            android.util.Log.d(TAG, "Full sync: hashes differ, cache updated with ${serverTasks.size} tasks")
                            true
                        } else {
                            // syncCacheWithServer: хеши совпадают, кэш не обновлен
                            BinaryLogger.getInstance()?.log(320u, emptyList<Any>(), 10)
                            android.util.Log.d(TAG, "Full sync: hashes match, no cache update needed")
                            false
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Full sync failed", e)
                        BinaryLogger.getInstance()?.log(
                            91u, listOf<Any>("full_sync_exception",e.message ?: "unknown", 10), 10
                        )
                        false
                    }
                }
            } else {
                // syncCacheWithServer: синхронизация очереди завершилась с ошибкой, но продолжаем для загрузки групп/пользователей
                BinaryLogger.getInstance()?.log(310u, emptyList<Any>(), 10)
                false
            }

            // Опционально: загрузка групп и пользователей
            var groups: Map<Int, String>? = null
            var users: List<UserSummary>? = null

            if (groupsApi != null) {
                try {
                    groups = groupsApi.getAll()
                    // syncCacheWithServer: загружены группы
                    BinaryLogger.getInstance()?.log(311u, emptyList<Any>(), 10)
                } catch (e: Exception) {
                    // Исключение: ожидалось %wait%, фактически %fact%
                    BinaryLogger.getInstance()?.log(
                        91u, listOf<Any>(e.message ?: "Unknown error",e::class.simpleName ?: "Unknown", 10), 10
                    )
                }
            }

            if (usersApi != null) {
                // syncCacheWithServer: вызов загрузки пользователей с сервера
                BinaryLogger.getInstance()?.log(400u, emptyList<Any>(), 10)
                try {
                    users = usersApi.getUsers()
                    // syncCacheWithServer: получен ответ от сервера с пользователями
                    BinaryLogger.getInstance()?.log(401u, listOf<Any>(users?.size ?: 0, 10), 10)
                    // syncCacheWithServer: загружены пользователи
                    BinaryLogger.getInstance()?.log(312u, emptyList<Any>(), 10)

                    // Save users to cache
                    if (users != null && users.isNotEmpty()) {
                        val userModels = users.map { summary ->
                            com.homeplanner.model.User(
                                id = summary.id,
                                name = summary.name
                            )
                        }
                        repository.saveUsersToCache(userModels)
                        // syncCacheWithServer: пользователи сохранены в локальный кеш
                        BinaryLogger.getInstance()?.log(402u, listOf<Any>(userModels.size, 10), 10)
                    }
                } catch (e: Exception) {
                    // Исключение: ожидалось %wait%, фактически %fact%
                    BinaryLogger.getInstance()?.log(
                        91u, listOf<Any>("users_load_error",e.message ?: "unknown", 10), 10
                    )
                }
            }

            // Возврат результата
            val result = SyncCacheResult(
                cacheUpdated = cacheUpdated,
                users = users,
                groups = groups
            )
            // syncCacheWithServer: успешно завершено
            BinaryLogger.getInstance()?.log(313u, emptyList<Any>(), 10)
            Result.success(result)
        } catch (e: Exception) {
            // Исключение: ожидалось %wait%, фактически %fact%
            BinaryLogger.getInstance()?.log(
                91u, listOf<Any>(e.message ?: "unknown_error",e::class.simpleName ?: "unknown_class", 10), 10
            )
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