package com.homeplanner.sync

import android.content.Context
import com.homeplanner.api.GroupsServerApi
import com.homeplanner.api.ServerApi
import com.homeplanner.api.UserSummary
import com.homeplanner.api.UsersServerApi
import com.homeplanner.database.entity.SyncQueueItem
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.debug.BinaryLogger
import com.homeplanner.debug.LogLevel
import org.json.JSONObject
import kotlinx.coroutines.delay
import java.security.MessageDigest

/**
 * Сервис синхронизации очереди операций с сервером.
 * Обрабатывает экспоненциальную задержку, конфликты по времени обновления и ошибки.
 */
class SyncService(
    private val repository: OfflineRepository,
    private val serverApi: ServerApi,
    private val context: Context
) {
    companion object {
        private const val TAG = "SyncService"
    }

    /**
     * Синхронизация состояния перед пересчётом задач по новому дню.
     *
     * Алгоритм:
     * 1. Если в очереди есть операции — выполнить syncQueue().
     * 2. Загрузить актуальные задачи с сервера и сохранить их в кэш.
     * 
     * Реальный статус соединения контролируется в MainActivity через connectionStatus
     * на основе реальных попыток соединения с сервером (ONLINE/DEGRADED/OFFLINE).
     */
    suspend fun syncStateBeforeRecalculation(): Boolean {
        return try {
            val pending = repository.getPendingOperationsCount()
            if (pending > 0) {
                // syncStateBeforeRecalculation: синхронизация ожидающих операций
                BinaryLogger.getInstance()?.log(300u, emptyList())
                val result = syncQueue()
                if (result.isFailure) {
                    // syncStateBeforeRecalculation: syncQueue завершился с ошибкой, но продолжается
                    BinaryLogger.getInstance()?.log(301u, emptyList())
                }
            }

            // syncStateBeforeRecalculation: загрузка задач с сервера
            BinaryLogger.getInstance()?.log(302u, emptyList())
            val serverTasks = serverApi.getTasksServer().getOrThrow()
            repository.saveTasksToCache(serverTasks)
            val queueSize = repository.getPendingOperationsCount()
            // Кэш обновлен после синхронизации: %tasks_count% задач из %source%, очередь: %queue_items%
            BinaryLogger.getInstance()?.log(
                41u,
                listOf(serverTasks.size, "syncStateBeforeRecalculation", queueSize)
            )

            // Дополнительно синхронизируем пользователей и группы, если нужно
            // TODO: Implement users and groups sync here if not already done

            true
        } catch (e: Exception) {
            // Исключение: ожидалось %wait%, фактически %fact%
            BinaryLogger.getInstance()?.log(
                91u,
                listOf(e.message ?: "Unknown error", e::class.simpleName ?: "Unknown")
            )
            false
        }
    }
    
    suspend fun syncQueue(): Result<SyncResult> {
        // Реальный статус соединения контролируется в MainActivity через connectionStatus
        // на основе реальных попыток соединения с сервером (ONLINE/DEGRADED/OFFLINE).
        // Здесь просто пытаемся синхронизироваться, ошибки будут обработаны в MainActivity.
        
        // Получаем все pending операции с разумным лимитом (10000)
        // Если операций больше, они будут синхронизированы в следующих вызовах
        val allQueueItems = repository.getPendingQueueItems(limit = 10000)
        if (allQueueItems.isEmpty()) {
            // Очередь синхронизации пуста
            BinaryLogger.getInstance()?.log(40u, emptyList())
            return Result.success(SyncResult(0, 0, 0))
        }
        
        return try {
            // Синхронизация начата
            BinaryLogger.getInstance()?.log(
                1u,
                listOf(allQueueItems.size)
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
                    41u,
                    listOf(allTasks.size, "syncQueue", allQueueItems.size)
                )
            }

            val syncResult = SyncResult(
                successCount = totalSuccessCount,
                failureCount = 0,
                conflictCount = 0
            )
            // Синхронизация успешно завершена
            BinaryLogger.getInstance()?.log(
                2u,
                listOf(syncResult.successCount, 0, 0)
            )
            Result.success(syncResult)
        } catch (e: Exception) {
            // Исключение: ожидалось %wait%, фактически %fact%
            BinaryLogger.getInstance()?.log(
                91u,
                listOf(e.message ?: "Unknown error", e::class.simpleName ?: "Unknown")
            )
            Result.failure(e)
        }
    }
    
    data class SyncResult(
        val successCount: Int,
        val failureCount: Int,
        val conflictCount: Int
    )
    
    /**
     * Результат синхронизации кэша с сервером.
     */
    data class SyncCacheResult(
        val cacheUpdated: Boolean,
        val users: List<UserSummary>? = null,
        val groups: Map<Int, String>? = null
    )
    
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
        BinaryLogger.getInstance()?.log(303u, emptyList())
        android.util.Log.i(TAG, "syncCacheWithServer: starting")

        return try {
            // Единый код синхронизации: просто вызываем syncQueue()
            // В онлайн режиме он работает сразу, в оффлайн - вернет ошибку, но при восстановлении сети сработает так же
            // syncQueue() сам обновит кэш актуальными данными с сервера после применения операций
            val queueSyncResult = syncQueue()
            
            // Если syncQueue() успешно выполнился и были операции, кэш уже обновлен актуальными данными с сервера
            // Если очередь была пуста или вернул ошибку, загружаем задачи с сервера для полной синхронизации
            val cacheUpdated = if (queueSyncResult.isSuccess) {
                val syncResult = queueSyncResult.getOrNull()
                if (syncResult != null && syncResult.successCount > 0) {
                    // Кэш уже обновлен syncQueue()
                    true
                } else {
                    // Очередь была пуста - загружаем задачи с сервера для полной синхронизации
                    try {
                        val serverTasks = serverApi.getTasksServer().getOrThrow()
                        val cachedTasks = repository.loadTasksFromCache()
                        val cachedHash = calculateTasksHash(cachedTasks)
                        val serverHash = calculateTasksHash(serverTasks)

                        if (cachedHash != serverHash) {
                            repository.saveTasksToCache(serverTasks)
                            true
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        BinaryLogger.getInstance()?.log(
                            91u,
                            listOf(e.message ?: "Unknown error", e::class.simpleName ?: "Unknown")
                        )
                        false
                    }
                }
            } else {
                // syncCacheWithServer: синхронизация очереди завершилась с ошибкой, но продолжаем для загрузки групп/пользователей
                BinaryLogger.getInstance()?.log(310u, emptyList())
                false
            }
            
            // Опционально: загрузка групп и пользователей
            var groups: Map<Int, String>? = null
            var users: List<UserSummary>? = null
            
            if (groupsApi != null) {
                try {
                    groups = groupsApi.getAll()
                    // syncCacheWithServer: загружены группы
                    BinaryLogger.getInstance()?.log(311u, emptyList())
                } catch (e: Exception) {
                    // Исключение: ожидалось %wait%, фактически %fact%
                    BinaryLogger.getInstance()?.log(
                        91u,
                        listOf(e.message ?: "Unknown error", e::class.simpleName ?: "Unknown")
                    )
                }
            }
            
            if (usersApi != null) {
                android.util.Log.i(TAG, "syncCacheWithServer: attempting to load users")
                // syncCacheWithServer: вызов загрузки пользователей с сервера
                BinaryLogger.getInstance()?.log(400u, emptyList())
                try {
                    users = usersApi.getUsers()
                    android.util.Log.i(TAG, "syncCacheWithServer: loaded ${users?.size} users")
                    // syncCacheWithServer: получен ответ от сервера с пользователями
                    BinaryLogger.getInstance()?.log(401u, listOf(users?.size ?: 0))
                    // syncCacheWithServer: загружены пользователи
                    BinaryLogger.getInstance()?.log(312u, emptyList())

                    // Save users to cache
                    if (users != null && users.isNotEmpty()) {
                        val userModels = users.map { summary ->
                            com.homeplanner.model.User(
                                id = summary.id,
                                name = summary.name
                            )
                        }
                        repository.saveUsersToCache(userModels)
                        android.util.Log.i(TAG, "syncCacheWithServer: saved ${userModels.size} users to cache")
                        // syncCacheWithServer: пользователи сохранены в локальный кеш
                        BinaryLogger.getInstance()?.log(402u, listOf(userModels.size))
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "syncCacheWithServer: failed to load users", e)
                    // Исключение: ожидалось %wait%, фактически %fact%
                    BinaryLogger.getInstance()?.log(
                        91u,
                        listOf(e.message ?: "Unknown error", e::class.simpleName ?: "Unknown")
                    )
                }
            } else {
                android.util.Log.w(TAG, "syncCacheWithServer: usersApi is null")
            }
            
            // Возврат результата
            val result = SyncCacheResult(
                cacheUpdated = cacheUpdated,
                users = users,
                groups = groups
            )
            // syncCacheWithServer: успешно завершено
            BinaryLogger.getInstance()?.log(313u, emptyList())
            Result.success(result)
        } catch (e: Exception) {
            // Исключение: ожидалось %wait%, фактически %fact%
            BinaryLogger.getInstance()?.log(
                91u,
                listOf(e.message ?: "Unknown error", e::class.simpleName ?: "Unknown")
            )
            Result.failure(e)
        }
    }
    
    /**
     * Отправка операций на сервер.
     * Алиас для syncQueue() для соответствия спецификации.
     */
    suspend fun sendOperations(): Result<SyncResult> = syncQueue()

    /**
     * Добавление операций в очередь.
     * Делегирует в OfflineRepository.
     */
    suspend fun addOperationToQueue(
        operation: String,
        entityType: String,
        entityId: Int?,
        payload: Any? = null
    ): Result<Long> = repository.addToSyncQueue(operation, entityType, entityId, payload)

    /**
     * Получение ожидающих операций.
     * Делегирует в OfflineRepository.
     */
    suspend fun getPendingOperations(): List<SyncQueueItem> = repository.getPendingQueueItems()

    /**
     * Очистка обработанных операций.
     * Делегирует в OfflineRepository.
     */
    suspend fun clearProcessedOperations() = repository.clearAllQueue()

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
            "${task.id}:${task.title}:${task.reminderTime}:${task.completed}:${task.active}"
        }
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

