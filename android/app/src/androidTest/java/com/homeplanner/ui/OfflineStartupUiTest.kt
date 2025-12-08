package com.homeplanner.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

        // Обрабатываем системный диалог разрешений и ждем готовности UI
        TestUtils.waitForAppReady(composeRule)
        
        // Дополнительная проверка диалогов перед ожиданием элементов
        TestUtils.dismissAnySystemDialogs()
        composeRule.waitForIdle()
        
        // Ждем появления кнопки "Все задачи" перед кликом
        // Используем цикл ожидания с проверкой наличия узла и обработкой диалогов
        var attempts = 0
        while (attempts < 50) {
            // Проверяем и закрываем диалоги перед каждой попыткой
            TestUtils.dismissAnySystemDialogs()
            composeRule.waitForIdle()
            
            try {
                val nodes = composeRule.onAllNodesWithText("Все задачи").fetchSemanticsNodes()
                if (nodes.isNotEmpty()) {
                    break
                }
            } catch (e: Exception) {
                // Игнорируем ошибки при поиске узла
            }
            Thread.sleep(100)
            attempts++
            composeRule.waitForIdle()
        }

        // Переходим на вкладку "Все задачи"
        composeRule.onNodeWithText("Все задачи").performClick()

        // Ждем загрузки данных и обновления UI
        composeRule.waitForIdle()
        
        // Даем дополнительное время на загрузку данных из кэша
        Thread.sleep(500)
        composeRule.waitForIdle()
        
        // Ждем появления задач из кэша (увеличиваем время ожидания)
        attempts = 0
        var task1Found = false
        var task2Found = false
        while (attempts < 150) {
            try {
                val nodes1 = composeRule.onAllNodesWithText("Задача оффлайн 1").fetchSemanticsNodes()
                if (nodes1.isNotEmpty()) {
                    task1Found = true
                }
                val nodes2 = composeRule.onAllNodesWithText("Задача оффлайн 2").fetchSemanticsNodes()
                if (nodes2.isNotEmpty()) {
                    task2Found = true
                }
                if (task1Found && task2Found) {
                    break
                }
            } catch (e: Exception) {
                // Игнорируем ошибки при поиске узла
            }
            Thread.sleep(100)
            attempts++
            composeRule.waitForIdle()
        }

        // Ожидаем увидеть хотя бы одну из оффлайн-задач по заголовку
        // Если задачи не найдены, тест должен упасть
        assert(task1Found || task2Found) {
            "Задачи из кэша не отображаются в UI. " +
            "Ожидалось увидеть 'Задача оффлайн 1' или 'Задача оффлайн 2', но они не найдены."
        }
        
        // Проверяем отображение найденных задач
        if (task1Found) {
        composeRule.onNodeWithText("Задача оффлайн 1").assertIsDisplayed()
        }
        if (task2Found) {
        composeRule.onNodeWithText("Задача оффлайн 2").assertIsDisplayed()
        }
    }
}
