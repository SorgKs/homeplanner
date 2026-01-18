package com.homeplanner

import android.app.Application
import com.homeplanner.api.GroupsServerApi
import com.homeplanner.api.LocalApi
import com.homeplanner.api.ServerApi
import com.homeplanner.api.UsersServerApi
import com.homeplanner.database.AppDatabase
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.sync.SyncService
import com.homeplanner.sync.SyncWorker
import com.homeplanner.sync.WebSocketService
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
import kotlinx.coroutines.runBlocking
import androidx.work.WorkManager

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

    // HTTP Client
    single {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    // APIs
    single { LocalApi(get(), get()) }
    single { com.homeplanner.api.GroupsLocalApi(get()) }
    single {
        val networkSettings = get<NetworkSettings>()
        val baseUrl = runBlocking { networkSettings.getApiBaseUrl() }
        if (baseUrl != null) {
            ServerApi(baseUrl = baseUrl)
        } else {
            // Fallback to BuildConfig if settings not configured
            ServerApi()
        }
    }

    // Sync
    single { SyncService(get(), get(), androidContext()) }
    single { WebSocketService(androidContext(), get(), get()) }
    single { com.homeplanner.services.ConnectionMonitor(androidContext()) }

    // ViewModel
    viewModel { TaskViewModel(androidContext() as Application, get(), get(), get(), get()) }
    viewModel { SettingsViewModel(androidContext() as Application, get(), get(), get()) }
    android.util.Log.d("Application", "SettingsViewModel registered in DI")
}

class Application : android.app.Application() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        android.util.Log.d("Application", "Starting Application onCreate")

        // Initialize Koin DI
        try {
            startKoin {
                androidContext(this@Application)
                modules(appModule)
            }
            android.util.Log.d("Application", "Koin initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("Application", "Failed to initialize Koin", e)
            return // Don't continue if DI fails
        }

        // Initialize DI container
        initializeDependencies()

        // Schedule day change scheduler for offline task recalculation
        val dayChangeScheduler = com.homeplanner.services.DayChangeScheduler(this)
        dayChangeScheduler.scheduleDailyRecalculation()

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
        // Получить зависимости из DI
        val koin = GlobalContext.get()!!
        val serverApi = koin.get<ServerApi>()
        val offlineRepository = koin.get<OfflineRepository>()
        val syncService = koin.get<SyncService>()

        // Создать TaskSyncManager
        val taskSyncManager = com.homeplanner.services.TaskSyncManager(
            serverApi = serverApi,
            offlineRepository = offlineRepository,
            syncService = syncService,
            taskValidationService = com.homeplanner.services.TaskValidationService(),
            webSocketService = koin.get<WebSocketService>(),
            networkSettings = koin.get<NetworkSettings>(),
            connectionMonitor = koin.get<com.homeplanner.services.ConnectionMonitor>()
        )

        // Запустить наблюдение за запросами синхронизации
        taskSyncManager.observeSyncRequests()
        android.util.Log.d("Application", "TaskSyncManager created and sync observation started")

        // Запланировать периодическую синхронизацию
        val workManager = WorkManager.getInstance(this)
        val syncWorkRequest = SyncWorker.createPeriodicWorkRequest()
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
            syncWorkRequest
        )
        android.util.Log.d("Application", "SyncWorker periodic work scheduled")

        // Perform initial cache synchronization to load users
        android.util.Log.d("Application", "createTaskSyncManager: launching performInitialCacheSync")
        scope.launch {
            android.util.Log.d("Application", "createTaskSyncManager: inside launch, calling performInitialCacheSync")
            performInitialCacheSync()
        }
    }

    private fun performInitialCacheSync() {
        android.util.Log.i("Application", "performInitialCacheSync: starting")
        android.util.Log.i("Application", "performInitialCacheSync: checking if network settings are configured")
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
                    host = "192.168.1.1", // Default host for device to reach host machine
                    port = 8000,
                    apiVersion = "0.3", // Use v0.3 to match backend
                    useHttps = false
                )
                android.util.Log.i("Application", "performInitialCacheSync: networkConfig = $networkConfig")
                android.util.Log.i("Application", "performInitialCacheSync: finalNetworkConfig = $finalNetworkConfig")

                val apiBaseUrl = finalNetworkConfig.toApiBaseUrl()
                android.util.Log.i("Application", "performInitialCacheSync: apiBaseUrl = $apiBaseUrl")

                // Note: baseUrl is now immutable and sourced from BuildConfig, no need to set globally

                // Get syncService from DI
                android.util.Log.d("Application", "Getting Koin context")
                val koin = GlobalContext.get()!!
                android.util.Log.d("Application", "Koin context obtained: $koin")
                val syncService = koin.get<SyncService>()
                android.util.Log.d("Application", "SyncService obtained from DI")

                android.util.Log.i("Application", "Attempting to sync from server with baseUrl: $apiBaseUrl")
                val result = syncService.syncCacheWithServer(baseUrl = apiBaseUrl)
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
        // Logging initialization removed - BinaryLogger deleted
    }

}