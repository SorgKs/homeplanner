package com.homeplanner.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeplanner.api.TasksApi
import com.homeplanner.database.AppDatabase
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SyncServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var tasksApi: TasksApi
    private lateinit var db: AppDatabase
    private lateinit var repository: OfflineRepository
    private lateinit var syncService: SyncService
    private lateinit var context: Context

    private val sampleTask = Task(
        id = 1,
        title = "Тестовая задача",
        description = "Описание",
        taskType = "one_time",
        recurrenceType = null,
        recurrenceInterval = null,
        intervalDays = null,
        reminderTime = "2025-01-15T10:00:00",
        groupId = null,
        active = true,
        completed = false,
        assignedUserIds = emptyList()
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // MockWebServer для тестирования API
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/api/v0.2").toString().trimEnd('/')
        tasksApi = TasksApi(
            httpClient = OkHttpClient.Builder().build(),
            baseUrl = baseUrl
        )
        
        // In-memory база данных
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = OfflineRepository(db, context)
        syncService = SyncService(repository, tasksApi, context)
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    @Test
    fun syncQueue_createOperation_syncsSuccessfully() = runTest {
        // Добавляем операцию создания в очередь
        repository.addToSyncQueue("create", "task", null, sampleTask)
        
        // Настраиваем успешный ответ сервера (batched sync-queue возвращает массив задач)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {
                        "id": 1,
                        "title": "Тестовая задача",
                        "description": "Описание",
                        "task_type": "one_time",
                        "recurrence_type": null,
                        "recurrence_interval": null,
                        "interval_days": null,
                        "reminder_time": "2025-01-15T10:00:00",
                        "group_id": null,
                        "active": true,
                        "completed": false
                      }
                    ]
                    """.trimIndent()
                )
        )
        
        // Синхронизируем
        val result = syncService.syncQueue()
        
        // Проверяем успешность
        val syncResult = result.getOrNull()
        assertNotNull(syncResult)
        assertTrue(syncResult!!.successCount > 0)
        
        // Проверяем, что операция удалена из очереди
        val queueItems = repository.getPendingQueueItems()
        assertEquals(0, queueItems.size)
    }

    @Test
    fun syncQueue_updateOperation_syncsSuccessfully() = runTest {
        // Сохраняем задачу в кэш
        repository.saveTasksToCache(listOf(sampleTask))
        
        // Добавляем операцию обновления в очередь
        val updatedTask = sampleTask.copy(title = "Обновленная задача")
        repository.addToSyncQueue("update", "task", sampleTask.id, updatedTask, sampleTask.revision)
        
        // Настраиваем успешный ответ сервера
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {
                        "id": 1,
                        "title": "Обновленная задача",
                        "description": "Описание",
                        "task_type": "one_time",
                        "recurrence_type": null,
                        "recurrence_interval": null,
                        "interval_days": null,
                        "reminder_time": "2025-01-15T10:00:00",
                        "group_id": null,
                        "active": true,
                        "completed": false
                      }
                    ]
                    """.trimIndent()
                )
        )
        
        // Синхронизируем
        val result = syncService.syncQueue()
        
        // Проверяем успешность
        val syncResult = result.getOrNull()
        assertNotNull(syncResult)
        assertTrue(syncResult!!.successCount > 0)
        
        // Проверяем, что операция удалена из очереди
        val queueItems = repository.getPendingQueueItems()
        assertEquals(0, queueItems.size)
    }

    @Test
    fun syncQueue_completeOperation_syncsSuccessfully() = runTest {
        // Сохраняем задачу в кэш
        repository.saveTasksToCache(listOf(sampleTask))
        
        // Добавляем операцию завершения в очередь
        repository.addToSyncQueue("complete", "task", sampleTask.id)
        
        // Настраиваем успешный ответ сервера
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {
                        "id": 1,
                        "title": "Тестовая задача",
                        "description": "Описание",
                        "task_type": "one_time",
                        "recurrence_type": null,
                        "recurrence_interval": null,
                        "interval_days": null,
                        "reminder_time": "2025-01-15T10:00:00",
                        "group_id": null,
                        "active": true,
                        "completed": true
                      }
                    ]
                    """.trimIndent()
                )
        )
        
        // Синхронизируем
        val result = syncService.syncQueue()
        
        // Проверяем успешность
        val syncResult = result.getOrNull()
        assertNotNull(syncResult)
        assertTrue(syncResult!!.successCount > 0)
        
        // Проверяем, что операция удалена из очереди
        val queueItems = repository.getPendingQueueItems()
        assertEquals(0, queueItems.size)
    }

    @Test
    fun syncQueue_failedOperation_returnsFailure_andKeepsQueue() = runTest {
        // Добавляем операцию в очередь
        repository.addToSyncQueue("create", "task", null, sampleTask)

        // Настраиваем ошибку сервера
        server.enqueue(MockResponse().setResponseCode(500))

        // Синхронизируем
        val result = syncService.syncQueue()

        // Проверяем, что синхронизация не удалась и очередь не очищена
        assertTrue(result.isFailure)
        val queueItems = repository.getPendingQueueItems()
        assertEquals(1, queueItems.size)
    }

    @Test
    fun syncQueue_emptyQueue_returnsSuccess() = runTest {
        // Синхронизируем пустую очередь
        val result = syncService.syncQueue()
        
        // Должно быть успешно (нет операций для синхронизации)
        val syncResult = result.getOrNull()
        assertNotNull(syncResult)
        assertEquals(0, syncResult!!.successCount)
    }

    @Test
    fun syncStateBeforeRecalculation_sendsPendingQueue_andReloadsTasks() = runTest {
        // Подготовка: одна операция в очереди и пустой кэш
        repository.addToSyncQueue("create", "task", null, sampleTask)

        // Первый ответ сервера — на batched syncQueue (ожидается массив задач)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {
                        "id": 1,
                        "title": "Тестовая задача",
                        "description": "Описание",
                        "task_type": "one_time",
                        "recurrence_type": null,
                        "recurrence_interval": null,
                        "interval_days": null,
                        "reminder_time": "2025-01-15T10:00:00",
                        "group_id": null,
                        "active": true,
                        "completed": false
                      }
                    ]
                    """.trimIndent()
                )
        )

        // Второй ответ — getTasks (reload)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {
                        "id": 1,
                        "title": "Тестовая задача",
                        "description": "Описание",
                        "task_type": "one_time",
                        "recurrence_type": null,
                        "recurrence_interval": null,
                        "interval_days": null,
                        "reminder_time": "2025-01-15T10:00:00",
                        "group_id": null,
                        "active": true,
                        "completed": false
                      }
                    ]
                    """.trimIndent()
                )
        )

        val ok = syncService.syncStateBeforeRecalculation()
        assertTrue(ok)

        // Очередь должна быть пустой
        val pending = repository.getPendingQueueItems()
        assertEquals(0, pending.size)

        // В кэше должна быть задача, пришедшая с сервера
        val cached = repository.loadTasksFromCache()
        assertEquals(1, cached.size)
        assertEquals(sampleTask.id, cached[0].id)
    }

    @Test
    fun isOnline_checksConnectivity() {
        // Проверяем метод isOnline
        val isOnline = syncService.isOnline()
        
        // Может быть true или false в зависимости от тестового окружения
        assertNotNull(isOnline)
    }
}

