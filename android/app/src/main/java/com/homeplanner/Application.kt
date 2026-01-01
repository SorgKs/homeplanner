package com.homeplanner

import android.app.Application
import com.homeplanner.debug.BinaryLogger
import com.homeplanner.debug.ChunkSender
import com.homeplanner.debug.LogCleanupManager
import com.homeplanner.NetworkConfig
import com.homeplanner.NetworkSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class Application : android.app.Application() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

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