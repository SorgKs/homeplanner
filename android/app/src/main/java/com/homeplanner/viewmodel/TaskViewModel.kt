// В android приложении для логирования используется ИСКЛЮЧИТЕЛЬНО бинарный лог
package com.homeplanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homeplanner.model.Task
import com.homeplanner.model.Group
import com.homeplanner.utils.TaskFilter
import com.homeplanner.utils.TaskFilterType
import com.homeplanner.NetworkConfig
import com.homeplanner.SelectedUser
import com.homeplanner.api.LocalApi
import com.homeplanner.api.GroupsLocalApi
import com.homeplanner.NetworkSettings
import com.homeplanner.UserSettings
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.koin.core.Koin
import org.koin.core.KoinApplication

data class TaskScreenState(
    val tasks: List<Task> = emptyList(),
    val groups: List<Group> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentFilter: TaskFilterType = TaskFilterType.TODAY,
    val selectedUser: SelectedUser? = null
)

class TaskViewModel(
    application: Application,
    private val localApi: LocalApi,
    private val groupsLocalApi: GroupsLocalApi,
    private val networkSettings: NetworkSettings,
    private val userSettings: UserSettings
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TaskScreenState())
    val state: StateFlow<TaskScreenState> = _state.asStateFlow()

    init {
        android.util.Log.d("TaskViewModel", "TaskViewModel init started")
        // Приложение запущено
        viewModelScope.launch {
            android.util.Log.d("TaskViewModel", "TaskViewModel init: loading selected user")
            // Load selected user
            val selectedUser = userSettings.selectedUserFlow.first()
            android.util.Log.d("TaskViewModel", "TaskViewModel init: selectedUser loaded: $selectedUser")
            updateState(_state.value.copy(selectedUser = selectedUser))

            // Load network config and initialize
            var networkConfig: NetworkConfig? = null
            try {
                android.util.Log.d("TaskViewModel", "TaskViewModel init: loading network config")
                networkConfig = networkSettings.configFlow.first()
                android.util.Log.d("TaskViewModel", "TaskViewModel init: networkConfig loaded: $networkConfig")
                if (networkConfig != null) {
                    val apiBaseUrl = networkConfig.toApiBaseUrl()
                    android.util.Log.d("TaskViewModel", "TaskViewModel init: apiBaseUrl=$apiBaseUrl, calling performInitialSyncIfNeeded")
                    performInitialSyncIfNeeded(networkConfig, apiBaseUrl)
                } else {
                    android.util.Log.w("TaskViewModel", "TaskViewModel init: networkConfig is null")
                }
            } catch (e: Exception) {
                android.util.Log.e("TaskViewModel", "TaskViewModel init: exception loading network config", e)
            }

            // Always try to sync with default config if networkConfig is null
            if (networkConfig == null) {
                android.util.Log.d("TaskViewModel", "TaskViewModel init: networkConfig is null, trying default config")
                try {
                    val defaultConfig = NetworkConfig(
                        host = "10.0.2.2", // For emulator
                        port = 8000,
                        apiVersion = "0.3",
                        useHttps = false
                    )
                    val apiBaseUrl = defaultConfig.toApiBaseUrl()
                    android.util.Log.d("TaskViewModel", "TaskViewModel init: default apiBaseUrl=$apiBaseUrl, calling performInitialSyncIfNeeded")
                    performInitialSyncIfNeeded(defaultConfig, apiBaseUrl)
                } catch (e: Exception) {
                    android.util.Log.e("TaskViewModel", "TaskViewModel init: exception with default config", e)
                }
            } else {
                android.util.Log.d("TaskViewModel", "TaskViewModel init: networkConfig loaded, skipping default")
            }

            android.util.Log.d("TaskViewModel", "TaskViewModel init: calling loadInitialData")
            loadInitialData()
        }
    }

    fun getFilteredTasks(tasks: List<Task>, filter: TaskFilterType): List<Task> {
        val selectedUser = _state.value.selectedUser
        return TaskFilter.filterTasks(tasks, filter, selectedUser, 4) // Default dayStartHour = 4
    }

    private fun updateState(newState: TaskScreenState) {
        _state.value = newState
    }

    private fun handleError(error: Throwable) {
        updateState(_state.value.copy(error = error.message))
    }

    private suspend fun performInitialSyncIfNeeded(networkConfig: NetworkConfig?, apiBaseUrl: String?) {
        android.util.Log.d("TaskViewModel", "performInitialSyncIfNeeded called with networkConfig=$networkConfig, apiBaseUrl=$apiBaseUrl")
        if (networkConfig == null || apiBaseUrl == null) {
            android.util.Log.w("TaskViewModel", "performInitialSyncIfNeeded: networkConfig or apiBaseUrl is null, skipping sync")
            return
        }

        android.util.Log.d("TaskViewModel", "performInitialSyncIfNeeded: attempting to get SyncService from DI")

        // Get syncService from DI
        val koin = GlobalContext.get()!!
        val syncService: com.homeplanner.sync.SyncService
        try {
            syncService = koin.get(com.homeplanner.sync.SyncService::class) as com.homeplanner.sync.SyncService
            android.util.Log.d("TaskViewModel", "performInitialSyncIfNeeded: SyncService obtained successfully")
        } catch (e: Exception) {
            android.util.Log.e("TaskViewModel", "performInitialSyncIfNeeded: failed to get SyncService from DI", e)
            return
        }

        android.util.Log.d("TaskViewModel", "performInitialSyncIfNeeded: calling syncCacheWithServer with baseUrl=$apiBaseUrl")
        try {
            val result = syncService.syncCacheWithServer(baseUrl = apiBaseUrl)
            android.util.Log.d("TaskViewModel", "performInitialSyncIfNeeded: syncCacheWithServer result: isSuccess=${result.isSuccess}")

            if (result.isSuccess) {
                val syncResult = result.getOrNull()
                android.util.Log.i("TaskViewModel", "performInitialSyncIfNeeded: sync successful, cacheUpdated=${syncResult?.cacheUpdated}")
            } else {
                android.util.Log.w("TaskViewModel", "performInitialSyncIfNeeded: sync failed: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("TaskViewModel", "performInitialSyncIfNeeded: exception during sync", e)
        }
    }

    /**
     * Завершает задачу локально и добавляет операцию в очередь синхронизации.
     * Обновляет UI немедленно через оптимистичное обновление.
     */
    fun completeTask(taskId: Int) {
        android.util.Log.d("TaskViewModel", "completeTask: called with taskId=$taskId")
        viewModelScope.launch {
            try {
                // Выполняем завершение задачи через LocalApi (оптимистичное обновление)
                val result = localApi.completeTaskLocal(taskId)
                result.onSuccess { updatedTask ->
                    android.util.Log.i("TaskViewModel", "completeTask: successfully completed task $taskId locally")

                    // Обновляем состояние UI с новой задачей
                    val currentTasks = _state.value.tasks
                    val updatedTasks = currentTasks.map { task ->
                        if (task.id == taskId) updatedTask else task
                    }
                    updateState(_state.value.copy(tasks = updatedTasks))

                    android.util.Log.d("TaskViewModel", "completeTask: UI state updated with completed task")
                }.onFailure { error ->
                    android.util.Log.e("TaskViewModel", "completeTask: failed to complete task $taskId", error)
                    handleError(error)
                }
            } catch (e: Exception) {
                android.util.Log.e("TaskViewModel", "completeTask: exception", e)
                handleError(e)
            }
        }
    }

    /**
     * Отменяет завершение задачи локально и добавляет операцию в очередь синхронизации.
     * Обновляет UI немедленно через оптимистичное обновление.
     */
    fun uncompleteTask(taskId: Int) {
        android.util.Log.d("TaskViewModel", "uncompleteTask: called with taskId=$taskId")
        viewModelScope.launch {
            try {
                // Выполняем отмену завершения задачи через LocalApi (оптимистичное обновление)
                val result = localApi.uncompleteTaskLocal(taskId)
                result.onSuccess { updatedTask ->
                    android.util.Log.i("TaskViewModel", "uncompleteTask: successfully uncompleted task $taskId locally")

                    // Обновляем состояние UI с новой задачей
                    val currentTasks = _state.value.tasks
                    val updatedTasks = currentTasks.map { task ->
                        if (task.id == taskId) updatedTask else task
                    }
                    updateState(_state.value.copy(tasks = updatedTasks))

                    android.util.Log.d("TaskViewModel", "uncompleteTask: UI state updated with uncompleted task")
                }.onFailure { error ->
                    android.util.Log.e("TaskViewModel", "uncompleteTask: failed to uncomplete task $taskId", error)
                    handleError(error)
                }
            } catch (e: Exception) {
                android.util.Log.e("TaskViewModel", "uncompleteTask: exception", e)
                handleError(e)
            }
        }
    }

    private suspend fun loadInitialData() {
        android.util.Log.d("TaskViewModel", "loadInitialData: started")
        try {
            android.util.Log.d("TaskViewModel", "loadInitialData: setting loading state")
            updateState(_state.value.copy(isLoading = true, error = null))

            // Load groups from local cache
            android.util.Log.d("TaskViewModel", "loadInitialData: calling groupsLocalApi.getGroupsLocal")
            val groupsResult = groupsLocalApi.getGroupsLocal()
            android.util.Log.d("TaskViewModel", "loadInitialData: groupsLocalApi.getGroupsLocal result: isSuccess=${groupsResult.isSuccess}")
            var groups = emptyList<Group>()
            groupsResult.onSuccess { loadedGroups ->
                android.util.Log.i("TaskViewModel", "loadInitialData: loaded ${loadedGroups.size} groups from cache")
                groups = loadedGroups
            }.onFailure { error ->
                android.util.Log.e("TaskViewModel", "loadInitialData: failed to load groups from cache", error)
                // Continue without groups, as they are optional for display
            }

            // Load tasks from local cache (after potential sync)
            android.util.Log.d("TaskViewModel", "loadInitialData: calling localApi.getTasksLocal")
            val tasksResult = localApi.getTasksLocal()
            android.util.Log.d("TaskViewModel", "loadInitialData: localApi.getTasksLocal result: isSuccess=${tasksResult.isSuccess}")
            tasksResult.onSuccess { tasks ->
                android.util.Log.i("TaskViewModel", "loadInitialData: loaded ${tasks.size} tasks from cache")
                if (tasks.isEmpty()) {
                    android.util.Log.w("TaskViewModel", "loadInitialData: cache is empty, sync may have failed")
                }
                updateState(_state.value.copy(tasks = tasks, groups = groups, isLoading = false))
            }.onFailure { error ->
                android.util.Log.e("TaskViewModel", "loadInitialData: failed to load tasks from cache", error)
                handleError(error)
            }

        } catch (e: Exception) {
            android.util.Log.e("TaskViewModel", "loadInitialData: exception", e)
            handleError(e)
        }
    }
}