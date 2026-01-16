package com.homeplanner.services

import android.util.Log
import com.homeplanner.api.ServerApi
import com.homeplanner.api.GroupsServerApi
import com.homeplanner.api.UsersServerApi
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.sync.SyncService
import com.homeplanner.services.TaskValidationService
import com.homeplanner.NetworkSettings
import com.homeplanner.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.security.MessageDigest

data class SyncState(
    val isSyncing: Boolean = false,
    val lastSyncTime: Long? = null,
    val error: String? = null
)

class TaskSyncManager(
    private val serverApi: ServerApi,
    private val offlineRepository: OfflineRepository,
    private val syncService: SyncService,
    private val taskValidationService: TaskValidationService,
    private val webSocketService: com.homeplanner.sync.WebSocketService,
    private val networkSettings: NetworkSettings? = null
) {

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isQueueSyncInProgress = false
    private var isFullSyncInProgress = false
    private var lastFullSyncTime = 0L

    suspend fun syncTasksServer(): Result<Unit> = runCatching {
        sendQueueToServer().getOrThrow()

        val serverTasks = serverApi.getTasksServer().getOrThrow()
        updateLocalCache(serverTasks).getOrThrow()
    }



    private suspend fun performServerSync() {
        try {
            val serverTasks = serverApi.getTasksServer().getOrThrow()
            updateLocalCache(serverTasks).getOrThrow()

            // Также синхронизируем пользователей и группы
            syncGroupsAndUsersServer().getOrThrow()
        } catch (e: Exception) {
            Log.w("TaskSyncManager", "Server sync failed", e)
            throw e
        }
    }

    private fun calculateTasksHash(tasks: List<Task>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sortedTasks = tasks.sortedBy { it.id }
        val data = sortedTasks.joinToString("|") { task ->
            "${task.id}:${task.title}:${task.reminderTime}:${task.completed}:${task.enabled}"
        }
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun syncGroupsAndUsersServer(): Result<Unit> = runCatching {
        // Синхронизация пользователей с сервера
        try {
            val usersServerApi = UsersServerApi()
            val serverUsers = usersServerApi.getUsers()
            val userModels = serverUsers.map { summary ->
                com.homeplanner.model.User(
                    id = summary.id,
                    name = summary.name
                )
            }
            offlineRepository.saveUsersToCache(userModels)
            Log.d("TaskSyncManager", "Synced ${userModels.size} users from server")
        } catch (e: Exception) {
            Log.w("TaskSyncManager", "Failed to sync users", e)
            // Continue with groups sync even if users fail
        }

        // Синхронизация групп с сервера
        try {
            val groupsServerApi = GroupsServerApi()
            val serverGroups = groupsServerApi.getGroupsServer().getOrThrow()

            offlineRepository.saveGroupsToCache(serverGroups)
            Log.d("TaskSyncManager", "Synced ${serverGroups.size} groups from server")
        } catch (e: Exception) {
            Log.w("TaskSyncManager", "Failed to sync groups", e)
        }
    }

    suspend fun performFullSync(): Result<Unit> = runCatching {
        syncTasksServer().getOrThrow()
        syncGroupsAndUsersServer().getOrThrow()
        Log.d("TaskSyncManager", "Full sync completed successfully")
    }

    suspend fun performFullSyncExternal(): Result<Unit> = runCatching {
        syncTasksServer().getOrThrow()
        syncGroupsAndUsersServer().getOrThrow()
        Log.d("TaskSyncManager", "Full sync completed successfully")
    }

    /**
     * Синхронизация только очереди операций с сервером.
     * Не включает полную синхронизацию задач/групп/пользователей.
     */
    private suspend fun performQueueSync(): Result<Unit> = runCatching {
        val baseUrl = networkSettings?.getApiBaseUrl() ?: BuildConfig.API_BASE_URL
        syncService.syncQueue(baseUrl ?: BuildConfig.API_BASE_URL).getOrThrow()
    }

    /**
     * Синхронизация данных с сервера (задачи/группы/пользователи).
     * Не включает отправку очереди операций.
     */
    private suspend fun performDataSync(): Result<Unit> = runCatching {
        val serverTasks = serverApi.getTasksServer().getOrThrow()
        updateLocalCache(serverTasks).getOrThrow()
        syncGroupsAndUsersServer().getOrThrow()
        Log.d("TaskSyncManager", "Data sync completed successfully")
    }

    /**
     * Проверка наличия сообщений и отправка очереди на сервер.
     */
    private suspend fun checkAndSubmitQueue() {
        if (isQueueSyncInProgress || isFullSyncInProgress) return

        val pendingCount = offlineRepository.getPendingOperationsCount()
        if (pendingCount > 0) {
            Log.d("TaskSyncManager", "Pending operations detected: $pendingCount, starting queue submission")
            isQueueSyncInProgress = true

            val syncResult = performQueueSync()

            if (syncResult.isSuccess) {
                Log.d("TaskSyncManager", "Queue submission completed successfully")
            } else {
                Log.w("TaskSyncManager", "Queue submission failed: ${syncResult.exceptionOrNull()?.message}")
            }
            isQueueSyncInProgress = false
        }
    }

    fun observeSyncRequests() {
        Log.d("TaskSyncManager", "observeSyncRequests: starting sync observation loops")

        // Start WebSocket connection
        scope.launch {
            try {
                webSocketService.start()
                Log.d("TaskSyncManager", "WebSocket service started")
            } catch (e: Exception) {
                Log.e("TaskSyncManager", "Failed to start WebSocket service", e)
            }
        }

        // Быстрая проверка флага синхронизации (каждые 200 мс)
        scope.launch {
            while (true) {
                try {
                    delay(200L) // Проверка каждые 200 мс

                    if (offlineRepository.requestSync) {
                        offlineRepository.requestSync = false  // Сбрасываем флаг сразу
                        checkAndSubmitQueue()
                    }
                } catch (e: Exception) {
                    Log.e("TaskSyncManager", "Error in sync flag observation", e)
                }
            }
        }

        // Проверка наличия сообщений в очереди (каждые 15 секунд)
        scope.launch {
            while (true) {
                try {
                    delay(15_000L) // 15 секунд
                    checkAndSubmitQueue()
                } catch (e: Exception) {
                    Log.e("TaskSyncManager", "Error in queue observation", e)
                }
            }
        }

        // Периодическая синхронизация данных с сервера
        scope.launch {
            while (true) {
                try {
                    delay(15 * 60 * 1000L) // 15 минут

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastFullSyncTime >= 15 * 60 * 1000L) {
                        Log.d("TaskSyncManager", "Starting periodic data sync")
                        isFullSyncInProgress = true

                        val syncResult = performDataSync()

                        if (syncResult.isSuccess) {
                            lastFullSyncTime = currentTime
                            Log.d("TaskSyncManager", "Data sync completed successfully")
                        } else {
                            Log.w("TaskSyncManager", "Data sync failed: ${syncResult.exceptionOrNull()?.message}")
                        }
                        isFullSyncInProgress = false
                    }
                } catch (e: Exception) {
                    Log.e("TaskSyncManager", "Error in data sync observation", e)
                    isFullSyncInProgress = false
                }
            }
        }
    }

    private suspend fun sendQueueToServer(): Result<Unit> = runCatching {
        val queueItems = offlineRepository.getPendingQueueItems()
        if (queueItems.isEmpty()) return@runCatching

        val tasks = queueItems.mapNotNull { it.payload?.let { /* parse Task from JSON - TODO: implement proper parsing */ null } }
        validateTasksBeforeSync(tasks).getOrThrow()

        syncService.syncQueue().getOrThrow()
        offlineRepository.clearAllQueue()
    }

    private suspend fun updateLocalCache(serverTasks: List<Task>): Result<Unit> = runCatching {
        offlineRepository.saveTasksToCache(serverTasks)
    }

    private suspend fun validateTasksBeforeSync(tasks: List<Task>): Result<Unit> = runCatching {
        tasks.forEach { task ->
            taskValidationService.validateTaskBeforeSend(task)
        }
    }

    fun destroy() {
        scope.cancel()
        webSocketService.stop()
    }
}