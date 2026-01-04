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
import com.homeplanner.TestUtils
import com.homeplanner.UserSettings
import com.homeplanner.database.AppDatabase
import com.homeplanner.model.Task
import com.homeplanner.repository.OfflineRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Базовый класс для тестов вкладки "Сегодня".
 * Содержит общие настройки, помощники и методы инициализации.
 */
@RunWith(AndroidJUnit4::class)
open class BaseTodayTabTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    protected lateinit var context: Context
    protected lateinit var userSettings: UserSettings
    protected lateinit var networkSettings: NetworkSettings
    protected lateinit var repository: OfflineRepository
    protected val testUserId = 1
    protected val testUserName = "Test User"

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
            networkSettings.clearConfig()
            repository.clearAllCacheLocal()
        }
    }

    /**
     * Создает задачу с указанными параметрами.
     */
    protected fun createTask(
        id: Int,
        title: String,
        taskType: String = "one_time",
        reminderTime: LocalDateTime,
        completed: Boolean = false,
        active: Boolean = true,
        assignedUserIds: List<Int> = listOf(testUserId)
    ): Task {
        val currentTimeMillis = System.currentTimeMillis()
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
            assignedUserIds = assignedUserIds,
            updatedAt = currentTimeMillis,
            lastAccessed = currentTimeMillis,
            lastShownAt = null,
            createdAt = currentTimeMillis
        )
    }

    /**
     * Получает дату начала сегодняшнего дня (с учетом dayStartHour = 4).
     */
    protected fun getTodayStart(): LocalDateTime {
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
    protected fun setupUser() {
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
    protected fun prepareTest(tasks: List<Task>) {
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
    protected fun setupNetwork() {
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
    protected fun waitForNode(text: String, maxAttempts: Int = 50): Boolean {
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
    protected fun navigateToTodayTab() {
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
}