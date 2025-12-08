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
import com.homeplanner.NetworkConfig
import com.homeplanner.NetworkSettings
import com.homeplanner.SelectedUser
import com.homeplanner.UserSettings
import com.homeplanner.database.AppDatabase
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Тесты для проверки корректности отображения задач на вкладке "Сегодня".
 * 
 * Проверяет:
 * - Оффлайн режим: задачи на сегодня с разными статусами (completed/active)
 * - Онлайн режим: аналогично (если настроена сеть)
 * - Разные типы задач (one_time, recurring, interval)
 * - Задачи на сегодня, в прошлом, в будущем
 */
@RunWith(AndroidJUnit4::class)
class TodayTabUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context
    private lateinit var userSettings: UserSettings
    private lateinit var networkSettings: NetworkSettings
    private lateinit var repository: OfflineRepository
    private val testUserId = 1
    private val testUserName = "Test User"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        userSettings = UserSettings(context)
        networkSettings = NetworkSettings(context)
        val db = AppDatabase.getDatabase(context)
        repository = OfflineRepository(db, context)

        // Очищаем все настройки перед тестом
        runBlocking {
            userSettings.clearSelectedUser()
            networkSettings.clearConfig()
            repository.clearAllCache()
            // Даем время на сохранение настроек
            Thread.sleep(200)
        }
    }

    @After
    fun tearDown() {
        // Очищаем настройки после теста
        runBlocking {
            userSettings.clearSelectedUser()
            networkSettings.clearConfig()
            repository.clearAllCache()
        }
    }

    /**
     * Создает задачу с указанными параметрами.
     */
    private fun createTask(
        id: Int,
        title: String,
        taskType: String = "one_time",
        reminderTime: LocalDateTime,
        completed: Boolean = false,
        active: Boolean = true,
        assignedUserIds: List<Int> = listOf(testUserId)
    ): Task {
        return Task(
            id = id,
            title = title,
            description = null,
            taskType = taskType,
            recurrenceType = null,
            recurrenceInterval = null,
            intervalDays = null,
            reminderTime = reminderTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            groupId = null,
            active = active,
            completed = completed,
            assignedUserIds = assignedUserIds
        )
    }

    /**
     * Получает дату начала сегодняшнего дня (с учетом dayStartHour = 4).
     */
    private fun getTodayStart(): LocalDateTime {
        val now = LocalDateTime.now()
        return if (now.hour >= 4) {
            now.withHour(4).withMinute(0).withSecond(0).withNano(0)
        } else {
            now.minusDays(1).withHour(4).withMinute(0).withSecond(0).withNano(0)
        }
    }

    /**
     * Настраивает пользователя для теста.
     */
    private fun setupUser() {
        runBlocking {
            userSettings.saveSelectedUser(SelectedUser(id = testUserId, name = testUserName))
            // Даем время на сохранение в DataStore
            Thread.sleep(300)
        }
    }

    /**
     * Подготавливает тест: настраивает пользователя и сохраняет задачи в кэш.
     * Вызывается в начале каждого теста.
     */
    private fun prepareTest(tasks: List<Task>) {
        runBlocking {
            // Сначала настраиваем пользователя, чтобы приложение могло загрузить его
            setupUser()
            // Даем время на загрузку пользователя в приложении
            Thread.sleep(500)
            // Затем сохраняем задачи
            repository.saveTasksToCache(tasks)
        }
        // Даем дополнительное время на инициализацию приложения с пользователем
        Thread.sleep(1000)
        composeRule.waitForIdle()
    }

    /**
     * Настраивает сеть для онлайн-режима (для тестов с реальным сервером).
     */
    private fun setupNetwork() {
        runBlocking {
            val config = NetworkConfig(
                host = "10.0.2.2", // Эмулятор Android использует 10.0.2.2 для localhost
                port = 8000,
                apiVersion = "0.2",
                useHttps = false
            )
            networkSettings.saveConfig(config)
        }
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
     * Переходит на вкладку "Сегодня".
     */
    private fun navigateToTodayTab() {
        TestUtils.waitForAppReady(composeRule)
        
        // Даем время на загрузку пользователя и инициализацию приложения
        Thread.sleep(1000)
        composeRule.waitForIdle()
        
        // Обрабатываем возможный экран выбора пользователя
        // Если пользователь уже выбран, этот экран не появится
        try {
            // Проверяем, есть ли диалог выбора пользователя или экран выбора
            val userPickerNodes = composeRule.onAllNodesWithText("Выберите пользователя", substring = true).fetchSemanticsNodes()
            if (userPickerNodes.isNotEmpty()) {
                // Пользователь не выбран, нужно подождать или выбрать
                // Но мы уже настроили пользователя в setUp(), поэтому просто ждем
                Thread.sleep(2000)
                composeRule.waitForIdle()
            }
        } catch (e: Exception) {
            // Игнорируем - возможно, экран выбора пользователя не отображается
        }
        
        // Ждем появления вкладок навигации
        var attempts = 0
        while (attempts < 100) {
            TestUtils.dismissAnySystemDialogs()
            composeRule.waitForIdle()
            try {
                val nodes = composeRule.onAllNodesWithText("Сегодня").fetchSemanticsNodes()
                if (nodes.isNotEmpty()) {
                    break
                }
            } catch (e: Exception) {
                // Игнорируем ошибки
            }
            Thread.sleep(100)
            attempts++
        }
        
        // Переходим на вкладку "Сегодня"
        composeRule.onNodeWithText("Сегодня").performClick()
        composeRule.waitForIdle()
        Thread.sleep(500)
        composeRule.waitForIdle()
    }

    // ========== ОФФЛАЙН РЕЖИМ ==========

    /**
     * Оффлайн: задачи на сегодня (completed=false, active=true) должны отображаться.
     */
    @Test
    fun offline_todayTasks_completedFalseActiveTrue_shouldBeVisible() {
        val todayStart = getTodayStart()
        val taskToday = createTask(
            id = 101,
            title = "Задача сегодня активная",
            reminderTime = todayStart.plusHours(10),
            completed = false,
            active = true
        )

        prepareTest(listOf(taskToday))
        navigateToTodayTab()

        // Задача должна быть видна
        assert(waitForNode("Задача сегодня активная", maxAttempts = 100)) {
            "Задача на сегодня (completed=false, active=true) должна отображаться"
        }
        composeRule.onNodeWithText("Задача сегодня активная").assertIsDisplayed()
    }

    /**
     * Оффлайн: задачи на сегодня (completed=true, active=false) должны отображаться.
     */
    @Test
    fun offline_todayTasks_completedTrueActiveFalse_shouldBeVisible() {
        val todayStart = getTodayStart()
        val taskToday = createTask(
            id = 102,
            title = "Задача сегодня завершенная",
            reminderTime = todayStart.plusHours(10),
            completed = true,
            active = false
        )

        prepareTest(listOf(taskToday))
        navigateToTodayTab()

        // Задача должна быть видна
        assert(waitForNode("Задача сегодня завершенная", maxAttempts = 100)) {
            "Задача на сегодня (completed=true, active=false) должна отображаться"
        }
        composeRule.onNodeWithText("Задача сегодня завершенная").assertIsDisplayed()
    }

    /**
     * Оффлайн: задачи на сегодня (completed=false, active=false) должны отображаться.
     */
    @Test
    fun offline_todayTasks_completedFalseActiveFalse_shouldBeVisible() {
        val todayStart = getTodayStart()
        val taskToday = createTask(
            id = 103,
            title = "Задача сегодня неактивная",
            reminderTime = todayStart.plusHours(10),
            completed = false,
            active = false
        )

        prepareTest(listOf(taskToday))
        navigateToTodayTab()

        // Задача должна быть видна
        assert(waitForNode("Задача сегодня неактивная", maxAttempts = 100)) {
            "Задача на сегодня (completed=false, active=false) должна отображаться"
        }
        composeRule.onNodeWithText("Задача сегодня неактивная").assertIsDisplayed()
    }

    /**
     * Оффлайн: задачи на сегодня (completed=true, active=true) должны отображаться.
     */
    @Test
    fun offline_todayTasks_completedTrueActiveTrue_shouldBeVisible() {
        val todayStart = getTodayStart()
        val taskToday = createTask(
            id = 104,
            title = "Задача сегодня завершенная активная",
            reminderTime = todayStart.plusHours(10),
            completed = true,
            active = true
        )

        prepareTest(listOf(taskToday))
        navigateToTodayTab()

        // Задача должна быть видна
        assert(waitForNode("Задача сегодня завершенная активная", maxAttempts = 100)) {
            "Задача на сегодня (completed=true, active=true) должна отображаться"
        }
        composeRule.onNodeWithText("Задача сегодня завершенная активная").assertIsDisplayed()
    }

    /**
     * Оффлайн: задачи в прошлом должны отображаться.
     */
    @Test
    fun offline_pastTasks_shouldBeVisible() {
        val todayStart = getTodayStart()
        val taskPast = createTask(
            id = 105,
            title = "Задача в прошлом",
            reminderTime = todayStart.minusDays(1).plusHours(10),
            completed = false,
            active = true
        )

        prepareTest(listOf(taskPast))
        navigateToTodayTab()

        // Задача должна быть видна
        assert(waitForNode("Задача в прошлом", maxAttempts = 100)) {
            "Задача в прошлом должна отображаться"
        }
        composeRule.onNodeWithText("Задача в прошлом").assertIsDisplayed()
    }

    /**
     * Оффлайн: задачи в будущем (completed=false) не должны отображаться.
     */
    @Test
    fun offline_futureTasks_completedFalse_shouldNotBeVisible() {
        val todayStart = getTodayStart()
        val taskFuture = createTask(
            id = 106,
            title = "Задача в будущем",
            reminderTime = todayStart.plusDays(1).plusHours(10),
            completed = false,
            active = true
        )

        prepareTest(listOf(taskFuture))
        navigateToTodayTab()

        // Даем время на загрузку и фильтрацию
        Thread.sleep(1000)
        composeRule.waitForIdle()

        // Задача не должна быть видна
        val nodes = composeRule.onAllNodesWithText("Задача в будущем").fetchSemanticsNodes()
        assert(nodes.isEmpty()) {
            "Задача в будущем (completed=false) не должна отображаться"
        }
    }

    /**
     * Оффлайн: задачи в будущем (completed=true) должны отображаться (для one_time).
     */
    @Test
    fun offline_futureTasks_completedTrue_shouldBeVisible() {
        val todayStart = getTodayStart()
        val taskFuture = createTask(
            id = 107,
            title = "Задача в будущем завершенная",
            reminderTime = todayStart.plusDays(1).plusHours(10),
            completed = true,
            active = false
        )

        prepareTest(listOf(taskFuture))
        navigateToTodayTab()

        // Задача должна быть видна (для one_time завершенные задачи видны даже в будущем)
        assert(waitForNode("Задача в будущем завершенная", maxAttempts = 100)) {
            "Задача в будущем (completed=true) должна отображаться для one_time задач"
        }
        composeRule.onNodeWithText("Задача в будущем завершенная").assertIsDisplayed()
    }

    /**
     * Оффлайн: повторяющиеся задачи (recurring) на сегодня должны отображаться.
     */
    @Test
    fun offline_recurringTasks_today_shouldBeVisible() {
        val todayStart = getTodayStart()
        val taskRecurring = createTask(
            id = 108,
            title = "Повторяющаяся задача сегодня",
            taskType = "recurring",
            reminderTime = todayStart.plusHours(10),
            completed = false,
            active = true
        )

        prepareTest(listOf(taskRecurring))
        navigateToTodayTab()

        // Задача должна быть видна
        assert(waitForNode("Повторяющаяся задача сегодня", maxAttempts = 100)) {
            "Повторяющаяся задача на сегодня должна отображаться"
        }
        composeRule.onNodeWithText("Повторяющаяся задача сегодня").assertIsDisplayed()
    }

    /**
     * Оффлайн: интервальные задачи (interval) на сегодня должны отображаться.
     */
    @Test
    fun offline_intervalTasks_today_shouldBeVisible() {
        val todayStart = getTodayStart()
        val taskInterval = createTask(
            id = 109,
            title = "Интервальная задача сегодня",
            taskType = "interval",
            reminderTime = todayStart.plusHours(10),
            completed = false,
            active = true
        )

        prepareTest(listOf(taskInterval))
        navigateToTodayTab()

        // Задача должна быть видна
        assert(waitForNode("Интервальная задача сегодня", maxAttempts = 100)) {
            "Интервальная задача на сегодня должна отображаться"
        }
        composeRule.onNodeWithText("Интервальная задача сегодня").assertIsDisplayed()
    }

    /**
     * Оффлайн: повторяющиеся задачи в будущем не должны отображаться.
     */
    @Test
    fun offline_recurringTasks_future_shouldNotBeVisible() {
        val todayStart = getTodayStart()
        val taskRecurring = createTask(
            id = 110,
            title = "Повторяющаяся задача в будущем",
            taskType = "recurring",
            reminderTime = todayStart.plusDays(1).plusHours(10),
            completed = false,
            active = true
        )

        prepareTest(listOf(taskRecurring))
        navigateToTodayTab()

        // Даем время на загрузку и фильтрацию
        Thread.sleep(1000)
        composeRule.waitForIdle()

        // Задача не должна быть видна
        val nodes = composeRule.onAllNodesWithText("Повторяющаяся задача в будущем").fetchSemanticsNodes()
        assert(nodes.isEmpty()) {
            "Повторяющаяся задача в будущем не должна отображаться"
        }
    }

    /**
     * Оффлайн: комбинация задач - на сегодня, в прошлом, в будущем.
     * Должны отображаться только задачи на сегодня и в прошлом.
     */
    @Test
    fun offline_mixedTasks_shouldFilterCorrectly() {
        val todayStart = getTodayStart()
        val taskToday = createTask(
            id = 111,
            title = "Задача сегодня",
            reminderTime = todayStart.plusHours(10),
            completed = false,
            active = true
        )
        val taskPast = createTask(
            id = 112,
            title = "Задача в прошлом",
            reminderTime = todayStart.minusDays(1).plusHours(10),
            completed = false,
            active = true
        )
        val taskFuture = createTask(
            id = 113,
            title = "Задача в будущем",
            reminderTime = todayStart.plusDays(1).plusHours(10),
            completed = false,
            active = true
        )
        val taskFutureCompleted = createTask(
            id = 114,
            title = "Задача в будущем завершенная",
            reminderTime = todayStart.plusDays(1).plusHours(10),
            completed = true,
            active = false
        )

        prepareTest(listOf(taskToday, taskPast, taskFuture, taskFutureCompleted))
        navigateToTodayTab()

        // Задачи на сегодня и в прошлом должны быть видны
        assert(waitForNode("Задача сегодня", maxAttempts = 100)) {
            "Задача на сегодня должна отображаться"
        }
        assert(waitForNode("Задача в прошлом", maxAttempts = 100)) {
            "Задача в прошлом должна отображаться"
        }
        assert(waitForNode("Задача в будущем завершенная", maxAttempts = 100)) {
            "Завершенная задача в будущем должна отображаться"
        }

        composeRule.onNodeWithText("Задача сегодня").assertIsDisplayed()
        composeRule.onNodeWithText("Задача в прошлом").assertIsDisplayed()
        composeRule.onNodeWithText("Задача в будущем завершенная").assertIsDisplayed()

        // Задача в будущем (незавершенная) не должна быть видна
        Thread.sleep(500)
        composeRule.waitForIdle()
        // Проверяем, что незавершенная задача в будущем не видна
        // Используем onAllNodesWithText - если найдены узлы, проверяем их количество
        // (должна быть видна только завершенная версия)
        val allFutureNodes = composeRule.onAllNodesWithText("Задача в будущем", substring = true).fetchSemanticsNodes()
        // Должен быть только один узел - "Задача в будущем завершенная"
        assert(allFutureNodes.size <= 1) {
            "Незавершенная задача в будущем не должна отображаться. Найдено узлов: ${allFutureNodes.size}"
        }
    }

    // ========== ОНЛАЙН РЕЖИМ ==========

    /**
     * Онлайн: задачи на сегодня (completed=false, active=true) должны отображаться.
     * Примечание: этот тест требует работающий сервер или мок.
     */
    @Test
    fun online_todayTasks_completedFalseActiveTrue_shouldBeVisible() {
        val todayStart = getTodayStart()
        val taskToday = createTask(
            id = 201,
            title = "Задача сегодня онлайн",
            reminderTime = todayStart.plusHours(10),
            completed = false,
            active = true
        )

        runBlocking {
            setupUser()
            setupNetwork()
            Thread.sleep(500)
            repository.saveTasksToCache(listOf(taskToday))
        }
        Thread.sleep(1000)
        composeRule.waitForIdle()
        navigateToTodayTab()

        // Задача должна быть видна (из кэша или с сервера)
        assert(waitForNode("Задача сегодня онлайн", maxAttempts = 150)) {
            "Задача на сегодня (completed=false, active=true) должна отображаться в онлайн режиме"
        }
        composeRule.onNodeWithText("Задача сегодня онлайн").assertIsDisplayed()
    }

    /**
     * Онлайн: задачи на сегодня (completed=true, active=false) должны отображаться.
     */
    @Test
    fun online_todayTasks_completedTrueActiveFalse_shouldBeVisible() {
        val todayStart = getTodayStart()
        val taskToday = createTask(
            id = 202,
            title = "Задача сегодня завершенная онлайн",
            reminderTime = todayStart.plusHours(10),
            completed = true,
            active = false
        )

        runBlocking {
            setupUser()
            setupNetwork()
            Thread.sleep(500)
            repository.saveTasksToCache(listOf(taskToday))
        }
        Thread.sleep(1000)
        composeRule.waitForIdle()
        navigateToTodayTab()

        // Задача должна быть видна
        assert(waitForNode("Задача сегодня завершенная онлайн", maxAttempts = 150)) {
            "Задача на сегодня (completed=true, active=false) должна отображаться в онлайн режиме"
        }
        composeRule.onNodeWithText("Задача сегодня завершенная онлайн").assertIsDisplayed()
    }

    /**
     * Онлайн: задачи в будущем (completed=false) не должны отображаться.
     */
    @Test
    fun online_futureTasks_completedFalse_shouldNotBeVisible() {
        val todayStart = getTodayStart()
        val taskFuture = createTask(
            id = 203,
            title = "Задача в будущем онлайн",
            reminderTime = todayStart.plusDays(1).plusHours(10),
            completed = false,
            active = true
        )

        runBlocking {
            setupUser()
            setupNetwork()
            Thread.sleep(500)
            repository.saveTasksToCache(listOf(taskFuture))
        }
        Thread.sleep(1000)
        composeRule.waitForIdle()
        navigateToTodayTab()

        // Даем время на загрузку и фильтрацию
        Thread.sleep(1500)
        composeRule.waitForIdle()

        // Задача не должна быть видна
        val nodes = composeRule.onAllNodesWithText("Задача в будущем онлайн").fetchSemanticsNodes()
        assert(nodes.isEmpty()) {
            "Задача в будущем (completed=false) не должна отображаться в онлайн режиме"
        }
    }

    /**
     * Онлайн: комбинация задач - на сегодня, в прошлом, в будущем.
     */
    @Test
    fun online_mixedTasks_shouldFilterCorrectly() {
        val todayStart = getTodayStart()
        val taskToday = createTask(
            id = 211,
            title = "Задача сегодня онлайн",
            reminderTime = todayStart.plusHours(10),
            completed = false,
            active = true
        )
        val taskPast = createTask(
            id = 212,
            title = "Задача в прошлом онлайн",
            reminderTime = todayStart.minusDays(1).plusHours(10),
            completed = false,
            active = true
        )
        val taskFuture = createTask(
            id = 213,
            title = "Задача в будущем онлайн",
            reminderTime = todayStart.plusDays(1).plusHours(10),
            completed = false,
            active = true
        )

        runBlocking {
            setupUser()
            setupNetwork()
            Thread.sleep(500)
            repository.saveTasksToCache(listOf(taskToday, taskPast, taskFuture))
        }
        Thread.sleep(1000)
        composeRule.waitForIdle()
        navigateToTodayTab()

        // Задачи на сегодня и в прошлом должны быть видны
        assert(waitForNode("Задача сегодня онлайн", maxAttempts = 150)) {
            "Задача на сегодня должна отображаться в онлайн режиме"
        }
        assert(waitForNode("Задача в прошлом онлайн", maxAttempts = 150)) {
            "Задача в прошлом должна отображаться в онлайн режиме"
        }

        composeRule.onNodeWithText("Задача сегодня онлайн").assertIsDisplayed()
        composeRule.onNodeWithText("Задача в прошлом онлайн").assertIsDisplayed()

        // Задача в будущем не должна быть видна
        Thread.sleep(1000)
        composeRule.waitForIdle()
        val nodes = composeRule.onAllNodesWithText("Задача в будущем онлайн").fetchSemanticsNodes()
        assert(nodes.isEmpty()) {
            "Незавершенная задача в будущем не должна отображаться в онлайн режиме"
        }
    }
}

