package com.homeplanner.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeplanner.MainActivity
import com.homeplanner.database.AppDatabase
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Тесты корректного offline-first поведения с точки зрения UI.
 *
 * Допущения:
 * - В кэше уже есть задачи (мы заполняем их напрямую через OfflineRepository).
 * - Настройки сети (NetworkSettings) не сконфигурированы, т.е. работаем "в офлайне".
 *
 * Ожидания offline-first:
 * - вкладка "Все задачи" отображает задачи из кэша даже без настроек сети;
 * - вкладка "Сегодня" строится по кэшу (после выбора пользователя и актуализации "сегодня");
 * - базовые действия (например, переход между видами) не блокируются из-за отсутствия сети.
 *
 * ВАЖНО: на текущем состоянии кода часть этих ожиданий нарушается,
 * поэтому тесты, проверяющие полноценное offline-first поведение, будут "красными",
 * пока UI не будет доработан под спецификацию.
 */
@RunWith(AndroidJUnit4::class)
class OfflineStartupUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun seedOfflineCacheWithSampleTasks() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = AppDatabase.getDatabase(context)
        val repository = OfflineRepository(db, context)

        val task1 = Task(
            id = 101,
            title = "Задача оффлайн 1",
            description = "Описание 1",
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
        val task2 = task1.copy(
            id = 102,
            title = "Задача оффлайн 2"
        )

        runBlocking {
            repository.clearAllCache()
            repository.saveTasksToCache(listOf(task1, task2))
        }
    }

    /**
     * Offline-first: при старте БЕЗ настроек сети вкладка "Все задачи"
     * должна отображать задачи из локального кэша, а не пустой список/только предупреждение.
     */
    @Test
    fun offlineStartup_allTab_showsTasksFromCache() {
        // Подготавливаем кэш до старта сценария
        seedOfflineCacheWithSampleTasks()

        // Переходим на вкладку "Все задачи"
        composeRule.onNodeWithText("Все задачи").performClick()

        // Ожидаем увидеть хотя бы одну из оффлайн-задач по заголовку
        composeRule.onNodeWithText("Задача оффлайн 1").assertIsDisplayed()
        composeRule.onNodeWithText("Задача оффлайн 2").assertIsDisplayed()
    }
}
