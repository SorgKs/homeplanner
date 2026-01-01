package com.homeplanner.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeplanner.model.Task
import com.homeplanner.utils.TaskFilterType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TaskViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: TaskViewModel
    private lateinit var application: Application

    private val sampleTasks = listOf(
        Task(
            id = 1,
            title = "Сегодняшняя задача",
            description = "Описание",
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
        ),
        Task(
            id = 2,
            title = "Завершенная задача",
            description = null,
            taskType = "one_time",
            recurrenceType = null,
            recurrenceInterval = null,
            intervalDays = null,
            reminderTime = "2025-12-30T10:00:00",
            groupId = null,
            active = true,
            completed = true,
            assignedUserIds = emptyList(),
            updatedAt = System.currentTimeMillis(),
            lastAccessed = System.currentTimeMillis(),
            lastShownAt = null,
            createdAt = System.currentTimeMillis()
        )
    )

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        viewModel = TaskViewModel(application)
    }

    @Test
    fun initialize_setsUpViewModel() = runTest {
        // Given
        val networkConfig = null
        val apiBaseUrl = "http://localhost:8000"
        val selectedUser = null

        // When
        viewModel.initialize(networkConfig, apiBaseUrl, selectedUser)

        // Then - ViewModel должен инициализироваться без ошибок
        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(TaskFilterType.TODAY, state.currentFilter)
    }

    @Test
    fun getFilteredTasks_withAllFilter_returnsAllTasks() {
        // Given
        val tasks = sampleTasks

        // When
        val filtered = viewModel.getFilteredTasks(tasks, TaskFilterType.ALL)

        // Then
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == 1 })
        assertTrue(filtered.any { it.id == 2 })
    }

    @Test
    fun getFilteredTasks_withTodayFilter_filtersTasksByDate() {
        // Given
        val tasks = sampleTasks

        // When
        val filtered = viewModel.getFilteredTasks(tasks, TaskFilterType.TODAY)

        // Then
        // Фильтрация должна происходить через TaskFilter
        // В данном случае, задача на сегодня должна быть включена
        assertTrue(filtered.isNotEmpty())
    }

    @Test
    fun initialState_hasCorrectDefaults() {
        // When - ViewModel создан

        // Then
        val state = viewModel.state.value
        assertTrue(state.tasks.isEmpty())
        assertFalse(state.isLoading)
        assertEquals(null, state.error)
        assertEquals(TaskFilterType.TODAY, state.currentFilter)
    }
}