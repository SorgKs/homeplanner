package com.homeplanner.sync

import android.content.Context
import com.homeplanner.BuildConfig
import com.homeplanner.api.GroupsServerApi
import com.homeplanner.api.ServerApi
import com.homeplanner.api.UsersServerApi
import com.homeplanner.database.entity.SyncQueueItem
import com.homeplanner.repository.OfflineRepository
import org.json.JSONObject
import kotlinx.coroutines.delay

/**
 * Сервис синхронизации очереди операций с сервером.
 * Обрабатывает экспоненциальную задержку, конфликты по времени обновления и ошибки.
 */
class SyncService(
    private val repository: OfflineRepository,
    private val serverApi: ServerApi,
    private val context: Context
) {
    private val queueSyncService = QueueSyncService(repository, serverApi)
    private val cacheSyncService = CacheSyncService(repository, serverApi)
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
                val result = syncQueue()
                if (result.isFailure) {
                    // syncStateBeforeRecalculation: syncQueue завершился с ошибкой, но продолжается
                }
            }

            val serverTasks = serverApi.getTasksServer(enabledOnly = false).getOrThrow()
            repository.saveTasksToCache(serverTasks)
            val queueSize = repository.getPendingOperationsCount()

            // Дополнительно синхронизируем пользователей и группы, если нужно
            // TODO: Implement users and groups sync here if not already done

            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun syncQueue(): Result<SyncResult> = queueSyncService.syncQueue()
    
    /**
     * Синхронизация кэша с сервером.
     */
    suspend fun syncCacheWithServer(
        groupsApi: GroupsServerApi? = null,
        usersApi: UsersServerApi? = null,
        baseUrl: String = BuildConfig.API_BASE_URL
    ): Result<SyncCacheResult> {
        val serverApi = com.homeplanner.api.ServerSyncApi(baseUrl = baseUrl)
        val groupsApiWithUrl = groupsApi ?: com.homeplanner.api.GroupsServerApi(baseUrl = baseUrl)
        val usersApiWithUrl = usersApi ?: com.homeplanner.api.UsersServerApi(baseUrl = baseUrl)
        return CacheSyncService(repository, serverApi).syncCacheWithServer(groupsApiWithUrl, usersApiWithUrl)
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

}

