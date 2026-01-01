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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ViewTab { TODAY, ALL, SETTINGS }

data class TaskScreenState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentFilter: TaskFilterType = TaskFilterType.TODAY,
    val selectedTab: ViewTab = ViewTab.TODAY
)

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TaskScreenState())
    val state: StateFlow<TaskScreenState> = _state.asStateFlow()

    // TODO: Inject dependencies through DI
    // private val localApi: LocalApi
    // private val taskSyncManager: TaskSyncManager

    fun initialize(
        networkConfig: NetworkConfig?,
        apiBaseUrl: String?,
        selectedUser: SelectedUser?
    ) {
        // TODO: Initialize with network config, API base URL, and selected user
        // This will set up server connections and user context
        viewModelScope.launch {
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

    private suspend fun loadInitialData() {
        try {
            updateState(_state.value.copy(isLoading = true, error = null))
            // TODO: Load initial data from LocalApi
            // val tasks = localApi.getTasksLocal()
            // updateState(_state.value.copy(tasks = tasks, isLoading = false))
            updateState(_state.value.copy(isLoading = false))
        } catch (e: Exception) {
            handleError(e)
        }
    }
}