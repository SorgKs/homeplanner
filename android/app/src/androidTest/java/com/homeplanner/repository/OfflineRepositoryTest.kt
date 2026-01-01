package com.homeplanner.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeplanner.database.AppDatabase
import com.homeplanner.model.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: OfflineRepository
    private lateinit var context: Context

    private val sampleTask1 = Task(
        id = 1,
        title = "Задача 1",
        description = "Описание 1",
        taskType = "one_time",
        recurrenceType = null,
        recurrenceInterval = null,
        intervalDays = null,
        reminderTime = "2025-01-15T10:00:00",
        groupId = null,
        active = true,
        completed = false,
        assignedUserIds = emptyList(),
        updatedAt = System.currentTimeMillis(),
        lastAccessed = System.currentTimeMillis(),
        lastShownAt = null,
        createdAt = System.currentTimeMillis()
    )

    private val sampleTask2 = Task(
        id = 2,
        title = "Задача 2",
        description = "Описание 2",
        taskType = "recurring",
        recurrenceType = "daily",
        recurrenceInterval = 1,
        intervalDays = null,
        reminderTime = "2025-01-16T11:00:00",
        groupId = 1,
        active = true,
        completed = false,
        assignedUserIds = emptyList(),
        updatedAt = System.currentTimeMillis(),
        lastAccessed = System.currentTimeMillis(),
        lastShownAt = null,
        createdAt = System.currentTimeMillis()
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Используем in-memory базу данных для тестов
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = OfflineRepository(db, context)

        // Очищаем оффлайн-кэш, очередь и связанные SharedPreferences перед каждым тестом,
        // чтобы избежать влияния предыдущих запусков/тестов.
        runBlocking {
            repository.clearAllCacheLocal()
            repository.clearAllQueue()
        }
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveTasksToCache_savesTasks() = runBlocking {
        val result = repository.saveTasksToCache(listOf(sampleTask1, sampleTask2))
        
        assertTrue(result.isSuccess)
        val cachedTasks = repository.loadTasksFromCache()
        assertEquals(2, cachedTasks.size)
        val ids = cachedTasks.map { it.id }.toSet()
        assertTrue(ids.contains(sampleTask1.id))
        assertTrue(ids.contains(sampleTask2.id))
    }

    @Test
    fun loadTasksFromCache_returnsAllTasks() = runBlocking {
        repository.saveTasksToCache(listOf(sampleTask1, sampleTask2))
        
        val tasks = repository.loadTasksFromCache()
        
        assertEquals(2, tasks.size)
        assertTrue(tasks.any { it.id == sampleTask1.id })
        assertTrue(tasks.any { it.id == sampleTask2.id })
    }

    @Test
    fun getTaskFromCache_returnsSpecificTask() = runBlocking {
        repository.saveTasksToCache(listOf(sampleTask1, sampleTask2))
        
        val task = repository.getTaskFromCache(sampleTask1.id)
        
        assertNotNull(task)
        assertEquals(sampleTask1.id, task!!.id)
        assertEquals(sampleTask1.title, task.title)
    }

    @Test
    fun getTaskFromCache_returnsNullForNonExistent() = runBlocking {
        val task = repository.getTaskFromCache(999)
        
        assertNull(task)
    }

    @Test
    fun addToSyncQueue_addsOperation() = runBlocking {
        val result = repository.addToSyncQueue("create", "task", null, sampleTask1)
        
        assertTrue(result.isSuccess)
        val queueItems = repository.getPendingQueueItems()
        assertEquals(1, queueItems.size)
        assertEquals("create", queueItems[0].operation)
        assertEquals("task", queueItems[0].entityType)
    }

    @Test
    fun addToSyncQueue_lightOperation_doesNotStorePayload() = runBlocking {
        val result = repository.addToSyncQueue("complete", "task", 1)
        
        assertTrue(result.isSuccess)
        val queueItems = repository.getPendingQueueItems()
        assertEquals(1, queueItems.size)
        assertEquals("complete", queueItems[0].operation)
        assertNull(queueItems[0].payload)
    }

    @Test
    fun getPendingQueueItems_returnsOnlyPending() = runBlocking {
        repository.addToSyncQueue("create", "task", null, sampleTask1)
        repository.addToSyncQueue("update", "task", 2, sampleTask2)
        
        val pending = repository.getPendingQueueItems()
        
        assertEquals(2, pending.size)
        assertTrue(pending.all { it.status == "pending" })
    }

    @Test
    fun getPendingOperationsCount_returnsCorrectCount() = runBlocking {
        repository.addToSyncQueue("create", "task", null, sampleTask1)
        repository.addToSyncQueue("update", "task", 2, sampleTask2)
        repository.addToSyncQueue("complete", "task", 3)
        
        val count = repository.getPendingOperationsCount()
        
        assertEquals(3, count)
    }

    @Test
    fun getCachedTasksCount_returnsCorrectCount() = runBlocking {
        repository.saveTasksToCache(listOf(sampleTask1, sampleTask2))
        
        val count = repository.getCachedTasksCountLocal()
        
        assertEquals(2, count)
    }

    @Test
    fun deleteTaskFromCache_removesTask() = runBlocking {
        repository.saveTasksToCache(listOf(sampleTask1, sampleTask2))
        
        repository.deleteTaskFromCache(sampleTask1.id)
        
        val tasks = repository.loadTasksFromCache()
        assertEquals(1, tasks.size)
        assertEquals(sampleTask2.id, tasks[0].id)
    }

    @Test
    fun getStoragePercentage_returnsPercentage() = runBlocking {
        repository.saveTasksToCache(listOf(sampleTask1))
        repository.addToSyncQueue("create", "task", null, sampleTask2)
        
        val percentage = repository.getStoragePercentage()
        
        assertTrue(percentage >= 0f)
        assertTrue(percentage <= 100f)
    }

    @Test
    fun updateRecurringTasksForNewDay_oneTime_becomesInactive_andDateNotChanged() = runBlocking {
        val completedOneTime = sampleTask1.copy(
            completed = true,
            active = true,
            reminderTime = "2025-01-10T10:00:00"
        )
        repository.saveTasksToCache(listOf(completedOneTime))

        val changed = repository.updateRecurringTasksForNewDay(dayStartHour = 4)
        assertTrue(changed)

        val tasks = repository.loadTasksFromCache()
        val updated = tasks.first { it.id == completedOneTime.id }

        assertEquals(completedOneTime.reminderTime, updated.reminderTime)
        assertFalse(updated.active)
        assertTrue(updated.completed)
    }

    @Test
    fun updateRecurringTasksForNewDay_recurring_completed_getsNewDate_andCompletedReset() = runBlocking {
        val completedRecurring = sampleTask2.copy(
            completed = true,
            active = true
        )
        repository.saveTasksToCache(listOf(completedRecurring))

        val changed = repository.updateRecurringTasksForNewDay(dayStartHour = 4)
        assertTrue(changed)

        val tasks = repository.loadTasksFromCache()
        val updated = tasks.first { it.id == completedRecurring.id }

        assertNotEquals(completedRecurring.reminderTime, updated.reminderTime)
        assertFalse(updated.completed)
    }
}

