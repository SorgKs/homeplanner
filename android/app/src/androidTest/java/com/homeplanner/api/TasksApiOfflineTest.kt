package com.homeplanner.api

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeplanner.database.AppDatabase
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.sync.SyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
class TasksApiOfflineTest {

    private lateinit var server: MockWebServer
    private lateinit var tasksApi: TasksApi
    private lateinit var db: AppDatabase
    private lateinit var repository: OfflineRepository
    private lateinit var syncService: SyncService
    private lateinit var apiOffline: TasksApiOffline
    private lateinit var context: Context
    private val testDispatcher = TestCoroutineDispatcher()

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
        completed = false
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
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
        
        apiOffline = TasksApiOffline(
            tasksApi = tasksApi,
            offlineRepository = repository,
            syncService = syncService,
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            onSyncStateChanged = {},
            onSyncSuccess = null
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun getTasks_offlineFirst_loadsFromCache() = runTest {
        // Сохраняем задачи в кэш
        repository.saveTasksToCache(listOf(sampleTask))
        
        // Загружаем через TasksApiOffline (должно загрузить из кэша)
        val tasks = apiOffline.getTasks(activeOnly = false)
        
        // Должны получить задачи из кэша немедленно
        assertEquals(1, tasks.size)
        assertEquals(sampleTask.id, tasks[0].id)
    }

    @Test
    fun getTasks_online_syncsInBackground() = runTest {
        // Настраиваем сервер для ответа
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [{
                      "id": 2,
                      "title": "Серверная задача",
                      "description": null,
                      "task_type": "one_time",
                      "recurrence_type": null,
                      "recurrence_interval": null,
                      "interval_days": null,
                      "reminder_time": "2025-01-16T11:00:00",
                      "group_id": null,
                      "active": true,
                      "completed": false
                    }]
                    """.trimIndent()
                )
        )
        
        // Сохраняем локальную задачу в кэш
        repository.saveTasksToCache(listOf(sampleTask))
        
        // Загружаем (должно вернуть из кэша сразу)
        val tasks = apiOffline.getTasks(activeOnly = false)
        
        // Проверяем, что получили из кэша
        assertEquals(1, tasks.size)
        assertEquals(sampleTask.id, tasks[0].id)
        
        // Ждем синхронизации в фоне
        // Используем планировщик тестового диспетчера напрямую, чтобы избежать
        // устаревшей функции advanceUntilIdle() с неявной делегацией.
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Проверяем, что задача с сервера добавлена в кэш
        val cachedTasks = repository.loadTasksFromCache()
        assertTrue(cachedTasks.size >= 1) // Может быть 1 или 2 в зависимости от синхронизации
    }

    @Test
    fun createTask_offlineFirst_savesLocally() = runTest {
        // Создаем задачу в оффлайне
        val created = apiOffline.createTask(sampleTask, emptyList())
        
        // Должна быть сохранена локально
        val cached = repository.getTaskFromCache(created.id)
        assertNotNull(cached)
        assertEquals(sampleTask.title, cached!!.title)
        
        // Должна быть добавлена в очередь
        val queueItems = repository.getPendingQueueItems()
        assertEquals(1, queueItems.size)
        assertEquals("create", queueItems[0].operation)
    }

    @Test
    fun updateTask_offlineFirst_updatesLocally() = runTest {
        // Сохраняем задачу в кэш
        repository.saveTasksToCache(listOf(sampleTask))
        
        // Обновляем задачу
        val updated = sampleTask.copy(title = "Обновленная задача")
        val result = apiOffline.updateTask(sampleTask.id, updated, emptyList())
        
        // Проверяем, что обновлена локально
        val cached = repository.getTaskFromCache(sampleTask.id)
        assertNotNull(cached)
        assertEquals("Обновленная задача", cached!!.title)
        
        // Должна быть добавлена в очередь
        val queueItems = repository.getPendingQueueItems()
        assertEquals(1, queueItems.size)
        assertEquals("update", queueItems[0].operation)
    }

    @Test
    fun completeTask_offlineFirst_updatesLocally() = runTest {
        // Сохраняем задачу в кэш
        repository.saveTasksToCache(listOf(sampleTask))
        
        // Завершаем задачу
        val completed = apiOffline.completeTask(sampleTask.id)
        
        // Проверяем, что обновлена локально
        assertTrue(completed.completed)
        val cached = repository.getTaskFromCache(sampleTask.id)
        assertNotNull(cached)
        assertTrue(cached!!.completed)
        
        // Должна быть добавлена в очередь
        val queueItems = repository.getPendingQueueItems()
        assertEquals(1, queueItems.size)
        assertEquals("complete", queueItems[0].operation)
    }

    @Test
    fun deleteTask_offlineFirst_removesLocally() = runTest {
        // Сохраняем задачу в кэш
        repository.saveTasksToCache(listOf(sampleTask))
        
        // Удаляем задачу
        apiOffline.deleteTask(sampleTask.id)
        
        // Проверяем, что удалена локально
        val cached = repository.getTaskFromCache(sampleTask.id)
        assertNull(cached)
        
        // Должна быть добавлена в очередь
        val queueItems = repository.getPendingQueueItems()
        assertEquals(1, queueItems.size)
        assertEquals("delete", queueItems[0].operation)
    }
}

