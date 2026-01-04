package com.homeplanner

import android.app.Application
import com.homeplanner.api.GroupsServerApi
import com.homeplanner.api.LocalApi
import com.homeplanner.api.ServerApi
import com.homeplanner.api.UsersServerApi
import com.homeplanner.database.AppDatabase
import com.homeplanner.debug.BinaryLogger
import com.homeplanner.debug.ChunkSender
import com.homeplanner.debug.LogCleanupManager
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.sync.SyncService
import com.homeplanner.utils.TaskDateCalculator
import com.homeplanner.viewmodel.TaskViewModel
import com.homeplanner.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

// DI Module
val appModule = module {
    // Database
    single { AppDatabase.getDatabase(androidContext()) }

    // Repository
    single { OfflineRepository(get(), androidContext()) }

    // Utils
    single { TaskDateCalculator }

    // Settings
    single { NetworkSettings(androidContext()) }
    single { UserSettings(androidContext()) }

    // APIs
    single { LocalApi(get(), get()) }
    singleOf(::ServerApi)

    // Sync
    single { SyncService(get(), get(), androidContext()) }

    // ViewModel
    viewModel { TaskViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
}

class Application : android.app.Application() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        android.util.Log.d("Application", "Starting Application onCreate")

        // Initialize Koin DI
        startKoin {
            androidContext(this@Application)
            modules(appModule)
        }

        android.util.Log.d("Application", "Koin initialized successfully")

        // Initialize DI container
        initializeDependencies()

        // Create Business Logic + Data Layer components
        createTaskViewModel()

        // Create Network Layer components
        createTaskSyncManager()

        // Initialize Logging Layer
        initializeLogging()
    }

    private fun initializeDependencies() {
        // TODO: Настройка DI framework
        // Создание всех зависимостей: OfflineRepository, ServerApi, etc.
    }

    private fun createTaskViewModel() {
        // TODO: Создание TaskViewModel с зависимостями
        // Получить networkConfig, apiBaseUrl, selectedUser из DI
        // Инициализировать ViewModel.initialize(networkConfig, apiBaseUrl, selectedUser)
    }

    private fun createTaskSyncManager() {
        // TODO: Создание TaskSyncManager с зависимостями
        // Получить serverApi, offlineRepository, syncService, taskValidationService из DI

        // Perform initial cache synchronization to load users
        scope.launch {
            performInitialCacheSync()
        }
    }

    private fun performInitialCacheSync() {
        android.util.Log.i("Application", "performInitialCacheSync: starting")
        val networkSettings = NetworkSettings(this)

        scope.launch {
            try {
                android.util.Log.i("Application", "performInitialCacheSync: getting networkConfig")
                val networkConfig = try {
                    networkSettings.configFlow.first()
                } catch (e: Exception) {
                    android.util.Log.i("Application", "performInitialCacheSync: networkConfig not set, using default")
                    null
                }
                val finalNetworkConfig = networkConfig ?: NetworkConfig(
                    host = "192.168.0.2", // Default host for debug
                    port = 8000,
                    apiVersion = "0.3", // Changed to match backend
                    useHttps = false
                )
                android.util.Log.i("Application", "performInitialCacheSync: networkConfig = $networkConfig")

                val apiBaseUrl = finalNetworkConfig.toApiBaseUrl()
                android.util.Log.i("Application", "performInitialCacheSync: apiBaseUrl = $apiBaseUrl")

                // Get syncService from DI
                val koinApplication = GlobalContext.get()!! as KoinApplication
                val myKoin = koinApplication.koin
                val syncService = myKoin.get(SyncService::class) as SyncService
                val usersApi = UsersServerApi(baseUrl = apiBaseUrl)
                val groupsApi = GroupsServerApi(baseUrl = apiBaseUrl)

                android.util.Log.i("Application", "Attempting to sync users from server")
                val result = syncService.syncCacheWithServer(groupsApi, usersApi)
                if (result.isFailure) {
                    android.util.Log.w("Application", "Failed to sync users from server: ${result.exceptionOrNull()?.message}")
                } else {
                    android.util.Log.i("Application", "Successfully synced users from server")
                }

                if (result.isSuccess) {
                    val syncResult = result.getOrNull()
                    android.util.Log.i("Application", "Initial cache sync successful: cacheUpdated=${syncResult?.cacheUpdated}, users=${syncResult?.users?.size}")
                } else {
                    android.util.Log.w("Application", "Initial cache sync failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("Application", "Error during initial cache sync", e)
            }
            android.util.Log.i("Application", "performInitialCacheSync: finished")
        }
    }

    private fun initializeLogging() {
        // Initialize BinaryLogger
        BinaryLogger.initialize(this)

        // Log application start
        BinaryLogger.getInstance()?.log(100u, emptyList())

        // Initialize ChunkSender for automatic log upload
        initializeLogUpload()

        // Initialize LogCleanupManager for automatic cleanup
        LogCleanupManager.start(this)
    }

    private fun initializeLogUpload() {
        // Get networkConfig from NetworkSettings or use default
        val networkSettings = NetworkSettings(this)
        val networkConfig = runBlocking {
            networkSettings.configFlow.first()
        } ?: NetworkConfig(
            apiVersion = "0.2",
            host = "localhost",
            port = 8000,
            useHttps = false // Default to HTTP for development
        )

        val storage = BinaryLogger.getStorage() ?: return
        if (storage != null) {
            ChunkSender.start(this@Application, networkConfig, storage)
        }
    }
}