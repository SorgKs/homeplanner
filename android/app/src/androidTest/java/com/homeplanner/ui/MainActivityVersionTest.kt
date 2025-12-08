package com.homeplanner.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeplanner.BuildConfig
import com.homeplanner.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityVersionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsScreen_showsVersion() {
        // Обрабатываем системный диалог разрешений и ждем готовности UI
        TestUtils.waitForAppReady(composeRule)
        
        // Дополнительная проверка диалогов перед ожиданием элементов
        TestUtils.dismissAnySystemDialogs()
        composeRule.waitForIdle()
        
        // Ждем появления кнопки "Настройки" перед кликом
        // Используем цикл ожидания с проверкой наличия узла и обработкой диалогов
        var attempts = 0
        while (attempts < 50) {
            // Проверяем и закрываем диалоги перед каждой попыткой
            TestUtils.dismissAnySystemDialogs()
            composeRule.waitForIdle()
            
            try {
                val nodes = composeRule.onAllNodesWithText("Настройки").fetchSemanticsNodes()
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
        
        composeRule.onNodeWithText("Настройки").performClick()
        
        // Ждем перехода на экран настроек
        composeRule.waitForIdle()
        
        val expected = "Версия: ${BuildConfig.VERSION_NAME}"
        
        // Ждем появления текста версии
        attempts = 0
        while (attempts < 50) {
            // Проверяем и закрываем диалоги перед каждой попыткой
            TestUtils.dismissAnySystemDialogs()
            composeRule.waitForIdle()
            
            try {
                val nodes = composeRule.onAllNodesWithText(expected).fetchSemanticsNodes()
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
        
        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }
}
