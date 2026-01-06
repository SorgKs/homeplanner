// В android приложении для логирования используется ИСКЛЮЧИТЕЛЬНО бинарный лог
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
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentFilter: TaskFilterType = TaskFilterType.TODAY,
    val selectedUser: SelectedUser? = null
)

class TaskViewModel(
    application: Application,
    private val localApi: LocalApi,
    private val networkSettings: NetworkSettings,
    private val userSettings: UserSettings
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TaskScreenState())
    val state: StateFlow<TaskScreenState> = _state.asStateFlow()

    init {
        // Приложение запущено
        BinaryLogger.getInstance()?.log(100u, emptyList<Any>(), 69)
        viewModelScope.launch {
            // Load selected user
            val selectedUser = userSettings.selectedUserFlow.first()
            updateState(_state.value.copy(selectedUser = selectedUser))

            // Load network config and initialize
            var networkConfig: NetworkConfig? = null
            try {
                networkConfig = networkSettings.configFlow.first()
                if (networkConfig != null) {
                    val apiBaseUrl = "http://${networkConfig.host}:${networkConfig.port}/api/${networkConfig.apiVersion}"
                    performInitialSyncIfNeeded(networkConfig, apiBaseUrl)
                }
            } catch (e: Exception) {
                // Исключение: ожидалось Error loading network config, фактически %fact%
                BinaryLogger.getInstance()?.log(91u, listOf<Any>("Error loading network config",e.message ?: "Unknown", 69), 69)
            }

            // Always try to sync with default config if networkConfig is null
            if (networkConfig == null) {
                try {
                    val defaultConfig = NetworkConfig(
                        host = "10.0.2.2", // For emulator
                        port = 8000,
                        apiVersion = "0.3",
                        useHttps = false
                    )
                    val apiBaseUrl = "http://${defaultConfig.host}:${defaultConfig.port}/api/${defaultConfig.apiVersion}"
                    performInitialSyncIfNeeded(defaultConfig, apiBaseUrl)
                } catch (e: Exception) {
                    BinaryLogger.getInstance()?.log(91u, listOf<Any>("SYNC SKIPPED: Error syncing with default config",e.message ?: "Unknown", 69), 69)
                }
            }

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
        // TODO: Log error through BinaryLogger
        // BinaryLogger.logError(error, "TaskViewModel")
    }



    private suspend fun performInitialSyncIfNeeded(networkConfig: NetworkConfig?, apiBaseUrl: String?) {
        // Исключение: ожидалось performInitialSyncIfNeeded called, фактически networkConfig=...,apiBaseUrl=...
        BinaryLogger.getInstance()?.log(91u, listOf<Any>("performInitialSyncIfNeeded called","networkConfig=$networkConfig,apiBaseUrl=$apiBaseUrl", 69), 69)
        if (networkConfig == null || apiBaseUrl == null) {
            // Исключение: ожидалось %wait%, фактически %fact%
            BinaryLogger.getInstance()?.log(91u, listOf<Any>(networkConfig?.toString() ?: "null", apiBaseUrl ?: "null"), 69)
            return
        }

        // Note: baseUrl is now immutable and sourced from BuildConfig, no need to set globally

        // Get syncService from DI
        val koin = GlobalContext.get()!!
        val syncService = koin.get(com.homeplanner.sync.SyncService::class) as com.homeplanner.sync.SyncService

        val usersApi = com.homeplanner.api.UsersServerApi()
        val groupsApi = com.homeplanner.api.GroupsServerApi()

        try {
            val result = syncService.syncCacheWithServer(groupsApi, usersApi)

            if (result.isSuccess) {
                val syncResult = result.getOrNull()
                // Синхронизация успешно завершена: сервер %server_tasks% задач, %groups% групп, %users% пользователей
                BinaryLogger.getInstance()?.log(2u, listOf<Any>(syncResult?.users?.size ?: 0,syncResult?.groups?.size ?: 0,syncResult?.users?.size ?: 0, 69), 69)
            } else {
                // Исключение: ожидалось sync_failure, фактически %fact%
                BinaryLogger.getInstance()?.log(91u, listOf<Any>("sync_failure", result.exceptionOrNull()?.message ?: "unknown"), 69)
            }
        } catch (e: Exception) {
            // Исключение: ожидалось sync_exception, фактически %fact%
            BinaryLogger.getInstance()?.log(91u, listOf<Any>("sync_exception",e.message ?: "unknown", 69), 69)
        }
    }

    private suspend fun loadInitialData() {
        try {
            updateState(_state.value.copy(isLoading = true, error = null))

            // Load tasks from local cache (after potential sync)
            val tasksResult = localApi.getTasksLocal()
            tasksResult.onSuccess { tasks ->
                BinaryLogger.getInstance()?.log(200u, emptyList<Any>(), 69) // Загружены задачи из кэша
                if (tasks.isEmpty()) {
                    // Исключение: ожидалось empty_cache, фактически sync_may_have_failed
                    BinaryLogger.getInstance()?.log(91u, listOf<Any>("empty_cache","sync_may_have_failed", 69), 69)
                }
                updateState(_state.value.copy(tasks = tasks, isLoading = false))
            }.onFailure { error ->
                // Исключение: ожидалось cache_load_error, фактически %fact%
                BinaryLogger.getInstance()?.log(91u, listOf<Any>("cache_load_error",error.message ?: "unknown", 69), 69)
                handleError(error)
            }

        } catch (e: Exception) {
            // Исключение: ожидалось Exception in loadInitialData, фактически %fact%
            BinaryLogger.getInstance()?.log(91u, listOf<Any>("Exception in loadInitialData",e.message ?: "Unknown", 69), 69)
            handleError(e)
        }
    }
}