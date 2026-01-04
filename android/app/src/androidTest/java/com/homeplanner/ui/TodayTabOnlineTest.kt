package com.homeplanner.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDateTime

/**
 * Тесты для проверки корректности отображения задач на вкладке "Сегодня" в онлайн режиме.
 */
class TodayTabOnlineTest : BaseTodayTabTest() {

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