package com.homeplanner.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeplanner.api.ServerApi
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.sync.SyncService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.Result

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TaskSyncManagerTest {

    private lateinit var taskSyncManager: TaskSyncManager
    private lateinit var mockServerApi: ServerApi
    private lateinit var mockOfflineRepository: OfflineRepository
    private lateinit var mockSyncService: SyncService
    private lateinit var mockTaskValidationService: TaskValidationService

    private val sampleTasks = listOf(
        Task(
            id = 1,
            title = "Server Task",
            description = "From server",
            taskType = "one_time",
            recurrenceType = null,
            recurrenceInterval = null,
            intervalDays = null,
            reminderTime = "2025-12-31T10:00:00",
            groupId = null,
            active = true,
            completed = false,
            assignedUserIds = emptyList(),
            updatedAt = System.currentTimeMillis(),
            lastAccessed = System.currentTimeMillis(),
            lastShownAt = null,
            createdAt = System.currentTimeMillis()
        )
    )

    @Before
    fun setUp() {
        mockServerApi = mockk()
        mockOfflineRepository = mockk()
        mockSyncService = mockk()
        mockTaskValidationService = mockk()

        taskSyncManager = TaskSyncManager(
            mockServerApi,
            mockOfflineRepository,
            mockSyncService,
            mockTaskValidationService
        )
    }

    @Test
    fun syncTasksServer_successfulSync_returnsSuccess() = runTest {
        // Given
        coEvery { mockOfflineRepository.getPendingQueueItems() } returns emptyList()
        coEvery { mockServerApi.getTasksServer(activeOnly = true) } returns Result.success(sampleTasks)
        coEvery { mockOfflineRepository.saveTasksToCache(sampleTasks) } returns Result.success(Unit)

        // When
        val result = taskSyncManager.syncTasksServer()

        // Then
        assertTrue(result.isSuccess)
        coVerify { mockOfflineRepository.saveTasksToCache(sampleTasks) }
    }

    @Test
    fun syncTasksServer_serverApiFailure_returnsFailure() = runTest {
        // Given
        val error = RuntimeException("Server error")
        coEvery { mockOfflineRepository.getPendingQueueItems() } returns emptyList()
        coEvery { mockServerApi.getTasksServer(activeOnly = true) } returns Result.failure(error)

        // When
        val result = taskSyncManager.syncTasksServer()

        // Then
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun syncTasksServer_saveToCacheFailure_returnsFailure() = runTest {
        // Given
        val error = RuntimeException("Cache error")
        coEvery { mockOfflineRepository.getPendingQueueItems() } returns emptyList()
        coEvery { mockServerApi.getTasksServer(activeOnly = true) } returns Result.success(sampleTasks)
        coEvery { mockOfflineRepository.saveTasksToCache(sampleTasks) } returns Result.failure(error)

        // When
        val result = taskSyncManager.syncTasksServer()

        // Then
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun performFullSyncExternal_callsBothSyncMethods() = runTest {
        // Given
        coEvery { mockOfflineRepository.getPendingQueueItems() } returns emptyList()
        coEvery { mockServerApi.getTasksServer(activeOnly = true) } returns Result.success(sampleTasks)
        coEvery { mockOfflineRepository.saveTasksToCache(sampleTasks) } returns Result.success(Unit)
        coEvery { taskSyncManager.syncGroupsAndUsersServer() } returns Result.success(Unit)

        // When
        val result = taskSyncManager.performFullSyncExternal()

        // Then
        assertTrue(result.isSuccess)
        coVerify { taskSyncManager.syncTasksServer() }
        coVerify { taskSyncManager.syncGroupsAndUsersServer() }
    }

    @Test
    fun performFullSyncExternal_tasksSyncFailure_returnsFailure() = runTest {
        // Given
        val error = RuntimeException("Tasks sync failed")
        coEvery { mockOfflineRepository.getPendingQueueItems() } returns emptyList()
        coEvery { mockServerApi.getTasksServer(activeOnly = true) } returns Result.failure(error)

        // When
        val result = taskSyncManager.performFullSyncExternal()

        // Then
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun syncGroupsAndUsersServer_returnsSuccess() = runTest {
        // When - метод ещё не реализован, должен возвращать success
        val result = taskSyncManager.syncGroupsAndUsersServer()

        // Then
        assertTrue(result.isSuccess)
    }
}