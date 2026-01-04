package com.homeplanner.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Test
import java.time.LocalDateTime

/**
 * Тесты для проверки корректности отображения задач на вкладке "Сегодня" в оффлайн режиме.
 */
class TodayTabOfflineTest : BaseTodayTabTest() {

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
}