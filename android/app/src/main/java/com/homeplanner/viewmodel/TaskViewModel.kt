package com.homeplanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homeplanner.model.Task
import com.homeplanner.utils.TaskFilter
import com.homeplanner.utils.TaskFilterType
import com.homeplanner.debug.BinaryLogger
import com.homeplanner.NetworkConfig
import com.homeplanner.SelectedUser
import com.homeplanner.api.LocalApi
import com.homeplanner.NetworkSettings
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

enum class ViewTab { TODAY, ALL, SETTINGS }

data class TaskScreenState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentFilter: TaskFilterType = TaskFilterType.TODAY,
    val selectedTab: ViewTab = ViewTab.TODAY
)

class TaskViewModel(
    application: Application,
    private val localApi: LocalApi,
    private val networkSettings: NetworkSettings
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TaskScreenState())
    val state: StateFlow<TaskScreenState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Load network config and initialize
            try {
                val networkConfig = networkSettings.configFlow.first()
                if (networkConfig != null) {
                    val apiBaseUrl = "http://${networkConfig.host}:${networkConfig.port}/api/${networkConfig.apiVersion}"

                    android.util.Log.i("TaskViewModel", "Initialized with network config: $networkConfig")
                    performInitialSyncIfNeeded(networkConfig, apiBaseUrl)
                } else {
                    android.util.Log.w("TaskViewModel", "Network config is null, skipping initialization")
                }
            } catch (e: Exception) {
                android.util.Log.e("TaskViewModel", "Error loading network config", e)
            }

            loadInitialData()
        }
    }

    fun getFilteredTasks(tasks: List<Task>, filter: TaskFilterType): List<Task> {
        // TODO: Use TaskFilter to filter tasks based on current user and filter type
        // For now return all tasks
        return TaskFilter.filterTasks(tasks, filter, null, 4) // Default dayStartHour = 4
    }

    private fun updateState(newState: TaskScreenState) {
        _state.value = newState
    }

    private fun handleError(error: Throwable) {
        updateState(_state.value.copy(error = error.message))
        // TODO: Log error through BinaryLogger
        // BinaryLogger.logError(error, "TaskViewModel")
    }

    fun updateSelectedTab(tab: ViewTab) {
        updateState(_state.value.copy(selectedTab = tab))
    }

    private suspend fun performInitialSyncIfNeeded(networkConfig: NetworkConfig?, apiBaseUrl: String?) {
        if (networkConfig == null || apiBaseUrl == null) return

        // Get syncService from DI
        val koinApplication = GlobalContext.get()!! as KoinApplication
        val myKoin = koinApplication.koin
        val syncService = myKoin.get(com.homeplanner.sync.SyncService::class) as com.homeplanner.sync.SyncService
        val usersApi = com.homeplanner.api.UsersServerApi(baseUrl = apiBaseUrl)
        val groupsApi = com.homeplanner.api.GroupsServerApi(baseUrl = apiBaseUrl)

        android.util.Log.i("TaskViewModel", "Performing initial sync with server")
        val result = syncService.syncCacheWithServer(groupsApi, usersApi)

        if (result.isSuccess) {
            val syncResult = result.getOrNull()
            android.util.Log.i("TaskViewModel", "Initial sync completed: cacheUpdated=${syncResult?.cacheUpdated}, users=${syncResult?.users?.size}")
        } else {
            android.util.Log.w("TaskViewModel", "Initial sync failed: ${result.exceptionOrNull()?.message}")
        }
    }

    private suspend fun loadInitialData() {
        try {
            updateState(_state.value.copy(isLoading = true, error = null))

            // Load tasks from local cache (after potential sync)
            val tasksResult = localApi.getTasksLocal()
            tasksResult.onSuccess { tasks ->
                android.util.Log.i("TaskViewModel", "Loaded ${tasks.size} tasks from local cache")
                updateState(_state.value.copy(tasks = tasks, isLoading = false))
            }.onFailure { error ->
                android.util.Log.e("TaskViewModel", "Error loading tasks from cache", error)
                handleError(error)
            }

        } catch (e: Exception) {
            handleError(e)
        }
    }
}