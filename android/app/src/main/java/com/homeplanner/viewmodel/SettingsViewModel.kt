package com.homeplanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homeplanner.*
import com.homeplanner.api.LocalApi
import com.homeplanner.api.UsersServerApi
import com.homeplanner.database.AppDatabase
import com.homeplanner.model.User
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.repository.TaskRepository
import com.homeplanner.utils.TaskDateCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsState(
    val users: List<User> = emptyList(),
    val isUsersLoading: Boolean = false,
    val selectedUser: SelectedUser? = null,
    val networkConfig: NetworkConfig? = null,
    val tasksCount: Int = 0,
    val debugMessagesCount: Int = 0
)

class SettingsViewModel(
    application: Application,
    private val localApi: LocalApi,
    private val networkSettings: NetworkSettings,
    private val userSettings: UserSettings
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val taskRepository = TaskRepository(db, application)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Load selected user
            val selectedUser = userSettings.selectedUserFlow.first()
            updateState(_state.value.copy(selectedUser = selectedUser))

            // Load network config
            val networkConfig = networkSettings.configFlow.first()
            updateState(_state.value.copy(networkConfig = networkConfig))

            loadUsersFromCache()
            loadDebugStats()
        }

        // Refresh from server when network config changes
        viewModelScope.launch {
            networkSettings.configFlow.collect { config ->
                android.util.Log.d("SettingsViewModel", "network config changed: config=$config")
                updateState(_state.value.copy(networkConfig = config))
                if (config != null) {
                    android.util.Log.d("SettingsViewModel", "network config not null, calling refreshUsersFromServer")
                    refreshUsersFromServer()
                } else {
                    android.util.Log.d("SettingsViewModel", "network config is null, not refreshing users")
                }
            }
        }
    }

    private fun updateState(newState: SettingsState) {
        _state.value = newState
    }

    private suspend fun loadUsersFromCache() {
        android.util.Log.d("SettingsViewModel", "loadUsersFromCache: started")
        try {
            val result = localApi.getUsersLocal()
            val users = result.getOrDefault(emptyList())
            android.util.Log.d("SettingsViewModel", "loadUsersFromCache: loaded ${users.size} users: ${users.map { "${it.name}(${it.id})" }}")
            updateState(_state.value.copy(users = users))
            android.util.Log.d("SettingsViewModel", "loadUsersFromCache: state updated successfully")
        } catch (e: Exception) {
            android.util.Log.e("SettingsViewModel", "Error loading users from cache", e)
        }
    }

    private suspend fun loadDebugStats() {
        try {
            val tasksCount = withContext(Dispatchers.IO) {
                taskRepository.getCachedTasksCount()
            }
            // BinaryLogger removed, set debug count to 0
            updateState(_state.value.copy(tasksCount = tasksCount, debugMessagesCount = 0))
        } catch (e: Exception) {
            android.util.Log.e("SettingsViewModel", "Error loading debug stats", e)
        }
    }

    fun refreshUsersFromServer() {
        viewModelScope.launch {
            updateState(_state.value.copy(isUsersLoading = true))
            try {
                val config = _state.value.networkConfig
                if (config != null) {
                    refreshUsersFromServerInternal(config)
                    loadUsersFromCache()
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Error refreshing users from server", e)
            } finally {
                updateState(_state.value.copy(isUsersLoading = false))
            }
        }
    }

    private suspend fun refreshUsersFromServerInternal(networkConfig: NetworkConfig) {
        android.util.Log.d("SettingsViewModel", "refreshUsersFromServerInternal: started with config=$networkConfig")
        val apiBaseUrl = networkConfig.toApiBaseUrl()
        val api = UsersServerApi(baseUrl = apiBaseUrl)

        // syncCacheWithServer: вызов загрузки пользователей с сервера
        val fetchedSummaries = withContext(Dispatchers.IO) {
            api.getUsers()
        }
        android.util.Log.d("SettingsViewModel", "refreshUsersFromServerInternal: fetched ${fetchedSummaries.size} summaries from server")
        // syncCacheWithServer: получен ответ от сервера с пользователями

        val fetchedUsers = fetchedSummaries.map { summary ->
            User(id = summary.id, name = summary.name)
        }
        android.util.Log.d("SettingsViewModel", "refreshUsersFromServerInternal: mapped to ${fetchedUsers.size} users")

        val db = AppDatabase.getDatabase(getApplication())
        val offlineRepository = OfflineRepository(db, getApplication())
        withContext(Dispatchers.IO) {
            offlineRepository.saveUsersToCache(fetchedUsers)
        }
        android.util.Log.d("SettingsViewModel", "refreshUsersFromServerInternal: saved users to cache")
        // syncCacheWithServer: пользователи сохранены в локальный кеш
    }

    fun clearSelectedUser() {
        viewModelScope.launch {
            userSettings.clearSelectedUser()
            updateState(_state.value.copy(selectedUser = null))
        }
    }

    fun saveSelectedUser(user: User) {
        viewModelScope.launch {
            val selected = SelectedUser(user.id, user.name)
            userSettings.saveSelectedUser(selected)
            updateState(_state.value.copy(selectedUser = selected))
        }
    }

    fun saveNetworkConfig(config: NetworkConfig) {
        viewModelScope.launch {
            networkSettings.saveConfig(config)
            updateState(_state.value.copy(networkConfig = config))
        }
    }

    fun clearNetworkConfig() {
        viewModelScope.launch {
            networkSettings.clearConfig()
            updateState(_state.value.copy(networkConfig = null))
        }
    }

    suspend fun parseAndSaveFromJson(qrContent: String): Boolean {
        return networkSettings.parseAndSaveFromJson(qrContent)
    }
}