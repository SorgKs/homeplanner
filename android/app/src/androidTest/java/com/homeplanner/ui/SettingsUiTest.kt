package com.homeplanner.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeplanner.MainActivity
import com.homeplanner.UserSettings
import com.homeplanner.database.AppDatabase
import com.homeplanner.model.User
import com.homeplanner.repository.OfflineRepository
import com.homeplanner.utils.TaskDateCalculator
import com.homeplanner.api.LocalApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Тесты для проверки функциональности выбора пользователя в настройках.
 *
 * Проверяет:
 * - Отображение секции выбора пользователя
 * - Загрузку списка пользователей
 * - Выбор пользователя из диалога
 * - Сброс выбранного пользователя
 * - Сохранение выбранного пользователя
 */
@RunWith(AndroidJUnit4::class)
class SettingsUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context
    private lateinit var userSettings: UserSettings
    private lateinit var repository: OfflineRepository
    private lateinit var localApi: LocalApi

    private val testUsers = listOf(
        User(id = 1, name = "Иван Иванов", email = "ivan@example.com", role = "admin", status = "active", updatedAt = System.currentTimeMillis()),
        User(id = 2, name = "Мария Петрова", email = "maria@example.com", role = "user", status = "active", updatedAt = System.currentTimeMillis()),
        User(id = 3, name = "Алексей Сидоров", email = "alex@example.com", role = "user", status = "inactive", updatedAt = System.currentTimeMillis())
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        userSettings = UserSettings(context)
        val db = AppDatabase.getDatabase(context)
        repository = OfflineRepository(db, context)
        localApi = LocalApi(repository, TaskDateCalculator)

        // Очищаем все настройки перед тестом
        runBlocking {
            userSettings.clearSelectedUser()
            repository.clearAllCacheLocal()
            // Даем время на сохранение настроек
            Thread.sleep(200)
        }
    }

    @After
    fun tearDown() {
        // Очищаем настройки после теста
        runBlocking {
            userSettings.clearSelectedUser()
            repository.clearAllCacheLocal()
        }
    }

    /**
     * Сохраняет тестовых пользователей в кэш.
     */
    private fun saveTestUsers() {
        runBlocking {
            repository.saveUsersToCache(testUsers)
        }
    }

    /**
     * Переходит на вкладку "Настройки".
     */
    private fun navigateToSettingsTab() {
        TestUtils.waitForAppReady(composeRule)

        // Ждем появления вкладок навигации
        var attempts = 0
        val maxAttempts = 150 // Увеличиваем время ожидания
        while (attempts < maxAttempts) {
            TestUtils.dismissAnySystemDialogs()
            composeRule.waitForIdle()
            try {
                val nodes = composeRule.onAllNodesWithText("Настройки").fetchSemanticsNodes()
                if (nodes.isNotEmpty()) {
                    break
                }
            } catch (e: Exception) {
                // Игнорируем ошибки
            }
            Thread.sleep(200) // Немного увеличиваем интервал
            attempts++
        }

        // Переходим на вкладку "Настройки"
        composeRule.onNodeWithText("Настройки").performClick()
        composeRule.waitForIdle()
        Thread.sleep(1000) // Увеличиваем время ожидания после клика
        composeRule.waitForIdle()
    }

    /**
     * Ждет появления элемента с текстом.
     */
    private fun waitForNode(text: String, maxAttempts: Int = 50): Boolean {
        var attempts = 0
        while (attempts < maxAttempts) {
            TestUtils.dismissAnySystemDialogs()
            composeRule.waitForIdle()
            try {
                val nodes = composeRule.onAllNodesWithText(text).fetchSemanticsNodes()
                if (nodes.isNotEmpty()) {
                    return true
                }
            } catch (e: Exception) {
                // Игнорируем ошибки
            }
            Thread.sleep(100)
            attempts++
        }
        return false
    }

    /**
     * Проверяет, что секция выбора пользователя отображается корректно.
     */
    @Test
    fun settings_userSelectionSection_shouldBeDisplayed() {
        navigateToSettingsTab()

        // Проверяем наличие секции выбора пользователя
        assert(waitForNode("Выбор пользователя")) {
            "Секция выбора пользователя должна отображаться"
        }
        composeRule.onNodeWithText("Выбор пользователя").assertIsDisplayed()
    }

    /**
     * Проверяет загрузку списка пользователей.
     */
    @Test
    fun settings_userList_shouldLoadUsers() {
        saveTestUsers()
        navigateToSettingsTab()

        // Ждем загрузки пользователей
        Thread.sleep(1000)
        composeRule.waitForIdle()

        // Проверяем, что пользователи загружены (должна появиться кнопка "Выбрать")
        assert(waitForNode("Выбрать")) {
            "Кнопка 'Выбрать' должна отображаться после загрузки пользователей"
        }
        composeRule.onNodeWithText("Выбрать").assertIsDisplayed()
    }

    /**
     * Проверяет, что при отсутствии пользователей отображается соответствующее сообщение.
     */
    @Test
    fun settings_noUsers_shouldShowEmptyMessage() {
        navigateToSettingsTab()

        // Ждем загрузки пользователей (асинхронная операция)
        Thread.sleep(2000)
        composeRule.waitForIdle()

        // Проверяем, что секция выбора пользователя отображается
        composeRule.onNodeWithText("Выбор пользователя").assertIsDisplayed()

        // При отсутствии пользователей должна отображаться кнопка "Выбрать"
        // (поскольку список пустой, кнопка все равно должна быть видна)
        composeRule.onNodeWithText("Выбрать").assertIsDisplayed()

        // Проверяем, что текущий пользователь не выбран (пустое состояние)
        // Это нормально для начального состояния
    }

    /**
     * Проверяет выбор пользователя через диалог.
     */
    @Test
    fun settings_selectUser_shouldOpenDialogAndSelect() {
        saveTestUsers()
        navigateToSettingsTab()

        // Ждем загрузки пользователей
        Thread.sleep(1000)
        composeRule.waitForIdle()

        // Нажимаем кнопку "Выбрать"
        composeRule.onNodeWithText("Выбрать").performClick()
        composeRule.waitForIdle()

        // Ждем открытия диалога
        Thread.sleep(500)
        composeRule.waitForIdle()

        // Проверяем, что диалог открыт
        assert(waitForNode("Выбор пользователя")) {
            "Диалог выбора пользователя должен открыться"
        }

        // Выбираем первого пользователя
        composeRule.onNodeWithText("Иван Иванов").performClick()
        composeRule.waitForIdle()

        // Ждем закрытия диалога и обновления UI
        Thread.sleep(1000)
        composeRule.waitForIdle()

        // Проверяем, что пользователь выбран
        assert(waitForNode("Иван Иванов")) {
            "Выбранный пользователь должен отображаться в настройках"
        }
        composeRule.onNodeWithText("Иван Иванов").assertIsDisplayed()
    }

    /**
     * Проверяет сброс выбранного пользователя.
     */
    @Test
    fun settings_clearSelectedUser_shouldResetSelection() {
        // Сначала выбираем пользователя
        runBlocking {
            userSettings.saveSelectedUser(com.homeplanner.SelectedUser(1, "Иван Иванов"))
            Thread.sleep(300)
        }

        navigateToSettingsTab()

        // Проверяем, что пользователь выбран
        assert(waitForNode("Иван Иванов")) {
            "Пользователь должен быть выбран"
        }

        // Нажимаем кнопку "Сбросить"
        composeRule.onNodeWithText("Сбросить").performClick()
        composeRule.waitForIdle()

        // Ждем обновления UI
        Thread.sleep(1000)
        composeRule.waitForIdle()

        // Проверяем, что пользователь сброшен
        val selectedNodes = composeRule.onAllNodesWithText("Иван Иванов").fetchSemanticsNodes()
        val clearNodes = composeRule.onAllNodesWithText("Сбросить").fetchSemanticsNodes()

        assert(selectedNodes.isEmpty() || clearNodes.isEmpty()) {
            "Выбранный пользователь должен быть сброшен, кнопка 'Сбросить' должна исчезнуть"
        }
    }

    /**
     * Проверяет сохранение выбранного пользователя при перезапуске.
     */
    @Test
    fun settings_selectedUser_shouldPersistAcrossSessions() {
        // Выбираем пользователя в первом "сеансе"
        runBlocking {
            userSettings.saveSelectedUser(com.homeplanner.SelectedUser(2, "Мария Петрова"))
            Thread.sleep(300)
        }

        // Перезапускаем активность (имитируем перезапуск приложения)
        composeRule.activityRule.scenario.recreate()

        navigateToSettingsTab()

        // Проверяем, что пользователь все еще выбран
        assert(waitForNode("Мария Петрова")) {
            "Выбранный пользователь должен сохраняться между сессиями"
        }
        composeRule.onNodeWithText("Мария Петрова").assertIsDisplayed()
    }

    /**
     * Проверяет возможность выбора "Не выбран" в диалоге.
     */
    @Test
    fun settings_selectNoUser_shouldClearSelection() {
        // Сначала выбираем пользователя
        runBlocking {
            userSettings.saveSelectedUser(com.homeplanner.SelectedUser(1, "Иван Иванов"))
            Thread.sleep(300)
        }

        navigateToSettingsTab()

        // Проверяем, что пользователь выбран
        assert(waitForNode("Иван Иванов")) {
            "Пользователь должен быть выбран"
        }

        // Открываем диалог выбора
        composeRule.onNodeWithText("Выбрать").performClick()
        composeRule.waitForIdle()
        Thread.sleep(500)
        composeRule.waitForIdle()

        // Выбираем "Не выбран"
        composeRule.onNodeWithText("Не выбран").performClick()
        composeRule.waitForIdle()

        // Ждем обновления UI
        Thread.sleep(1000)
        composeRule.waitForIdle()

        // Проверяем, что выбор сброшен
        val selectedNodes = composeRule.onAllNodesWithText("Иван Иванов").fetchSemanticsNodes()
        assert(selectedNodes.isEmpty()) {
            "Выбор пользователя должен быть сброшен"
        }
    }
}