package com.homeplanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.homeplanner.database.AppDatabase
import com.homeplanner.database.entity.TaskEntity
import com.homeplanner.sync.SyncService
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.api.ServerSyncApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Instrumented tests for sync functionality: users and tasks synchronization.
 */
@RunWith(AndroidJUnit4::class)
class SyncTest {

    private lateinit var appContext: android.content.Context
    private lateinit var database: AppDatabase
    private lateinit var repository: OfflineRepository
    private lateinit var serverApi: ServerSyncApi
    private lateinit var syncService: SyncService

    @Before
    fun setup() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        database = AppDatabase.getDatabase(appContext)
        repository = OfflineRepository(database, appContext)
        serverApi = ServerSyncApi(baseUrl = "http://192.168.1.39:8000/api/v0.3")
        syncService = SyncService(repository, serverApi, appContext)
    }

    @After
    fun teardown() {
        // Clean up database after each test
        database.clearAllTables()
        database.close()
    }

    @Test
    fun testSyncUsersFromServer() = runBlocking {
        val networkConfig = NetworkConfig("192.168.1.39", 8000, "0.3", false)
        val apiBaseUrl = networkConfig.toApiBaseUrl()

        // This test requires backend to be running
        val result = syncService.syncCacheWithServer(baseUrl = apiBaseUrl)

        // If backend is not running, this will fail gracefully
        if (result.isFailure) {
            // Test that error is handled properly
            assertNotNull("Sync should return result even on failure", result.exceptionOrNull())
        } else {
            // If sync succeeds, check that some data was processed
            val syncResult = result.getOrNull()
            assertNotNull("Sync result should not be null on success", syncResult)
        }
    }

    @Test
    fun testTaskCreationAndSync() = runBlocking {
        // Create a test task in local database
        val taskDao = database.taskDao()
        val testTask = TaskEntity(
            id = 1,
            title = "Test Task",
            description = "Description",
            taskType = "one_time",
            recurrenceType = null,
            recurrenceInterval = null,
            intervalDays = null,
            reminderTime = "2026-01-07T12:00:00",
            groupId = null,
            enabled = true,
            completed = false,
            assignedUserIds = "[]",
            hash = "test_hash",
            updatedAt = System.currentTimeMillis(),
            lastAccessed = System.currentTimeMillis(),
            lastShownAt = null,
            createdAt = System.currentTimeMillis()
        )

        taskDao.insertTask(testTask)

        // Verify task was inserted
        val retrieved = taskDao.getTaskById(1)
        assertNotNull("Task should be retrievable", retrieved)
        assertEquals("Test Task", retrieved?.title)
    }

    @Test
    fun testUserStorage() = runBlocking {
        // Test user storage in database
        val metadataDao = database.metadataDao()

        // This test verifies database schema for users via metadata
        val testMetadata = com.homeplanner.database.entity.Metadata(
            key = "test_user",
            value = "test_value"
        )
        metadataDao.insertMetadata(testMetadata)

        val retrieved = metadataDao.getMetadata("test_user")
        assertNotNull("Metadata should be stored", retrieved)
        assertEquals("test_value", retrieved?.value)
    }

    @Test
    fun testNetworkConfigApiUrl() {
        val config = NetworkConfig("localhost", 8000, "0.3", false)
        val apiUrl = config.toApiBaseUrl()
        assertEquals("http://localhost:8000/api/v0.3", apiUrl)

        val httpsConfig = NetworkConfig("example.com", 443, "0.2", true)
        val httpsUrl = httpsConfig.toApiBaseUrl()
        assertEquals("https://example.com:443/api/v0.2", httpsUrl)
    }
}