package com.homeplanner.services

import android.util.Log
import com.homeplanner.api.ServerApi
import com.homeplanner.api.GroupsServerApi
import com.homeplanner.api.UsersServerApi
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.sync.SyncService
import com.homeplanner.services.TaskValidationService
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
    private val taskValidationService: TaskValidationService
) {

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun syncTasksServer(): Result<Unit> = runCatching {
        sendQueueToServer().getOrThrow()

        val serverTasks = serverApi.getTasksServer().getOrThrow()
        updateLocalCache(serverTasks).getOrThrow()
    }

    private suspend fun performQueueSync() {
        val networkConfig = null // TODO: pass from caller

        // 1. Sync queue first (apply pending changes to server)
        val queueResult = try {
            syncService.syncQueue()
        } catch (e: Exception) {
            Log.w("TaskSyncManager", "Queue sync failed", e)
            null
        }

        // 2. If queue sync was successful and had operations, cache is already updated
        val syncResult = queueResult?.getOrNull()
        if (syncResult?.successCount ?: 0 > 0) {
            Log.d("TaskSyncManager", "Queue sync applied ${syncResult?.successCount} operations")
            return
        }

        // 3. Otherwise, perform full server sync
        performServerSync()
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

    fun observeSyncRequests() {
        scope.launch {
            while (true) {
                try {
                    // Проверяем флаг requestSync каждые 30 секунд
                    delay(30_000L)

                    if (offlineRepository.requestSync) {
                        Log.d("TaskSyncManager", "Sync request detected, starting sync")
                        val syncResult = performFullSync()

                        if (syncResult.isSuccess) {
                            // Сбрасываем флаг после успешной синхронизации
                            offlineRepository.requestSync = false
                            Log.d("TaskSyncManager", "Sync completed successfully")
                        } else {
                            Log.w("TaskSyncManager", "Sync failed: ${syncResult.exceptionOrNull()?.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TaskSyncManager", "Error in sync observation", e)
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
    }
}