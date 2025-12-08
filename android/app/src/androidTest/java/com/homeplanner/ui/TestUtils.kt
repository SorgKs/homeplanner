package com.homeplanner.ui

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Утилиты для UI тестов.
 */
object TestUtils {
    
    /**
     * Обрабатывает системный диалог "Alarms&reminders", который появляется
     * при первом запуске приложения, запрашивающего разрешение SCHEDULE_EXACT_ALARM.
     * Нажимает кнопку "Назад" для закрытия диалога.
     */
    fun dismissAlarmsRemindersDialog() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        
        // Даем время на появление диалога
        Thread.sleep(1500)
        
        // Пытаемся найти диалог по тексту "Alarms" или "reminders"
        var dialogFound = false
        try {
            val selectors = listOf(
                UiSelector().textContains("Alarm"),
                UiSelector().textContains("alarm"),
                UiSelector().textContains("Reminder"),
                UiSelector().textContains("reminder"),
                UiSelector().textContains("Alarms"),
                UiSelector().textContains("reminders")
            )
            
            for (selector in selectors) {
                val obj = device.findObject(selector)
                if (obj.exists()) {
                    dialogFound = true
                    break
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки поиска
        }
        
        // Нажимаем "Назад" для закрытия диалога
        if (dialogFound) {
            device.pressBack()
            Thread.sleep(500)
            device.pressBack() // На всякий случай еще раз
            Thread.sleep(300)
        } else {
            device.pressBack()
            Thread.sleep(500)
        }
    }
    
    /**
     * Обрабатывает системный диалог разрешения на уведомления.
     * Находит и нажимает кнопку "Allow" / "Разрешить" для подтверждения разрешения.
     */
    fun allowNotificationsDialog() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        
        // Даем время на появление диалога
        Thread.sleep(1000)
        
        try {
            // Ищем кнопку "Allow" или "Разрешить" в диалоге уведомлений
            val allowSelectors = listOf(
                UiSelector().text("Allow"),
                UiSelector().text("ALLOW"),
                UiSelector().text("Разрешить"),
                UiSelector().text("РАЗРЕШИТЬ"),
                UiSelector().textMatches("(?i).*allow.*"),
                UiSelector().textMatches("(?i).*разрешить.*"),
                // Также ищем по resource-id системных кнопок
                UiSelector().resourceId("com.android.permissioncontroller:id/permission_allow_button"),
                UiSelector().resourceId("android:id/button1") // Обычно это кнопка "Allow"
            )
            
            var buttonFound = false
            for (selector in allowSelectors) {
                val button = device.findObject(selector)
                if (button.exists() && button.isEnabled) {
                    button.click()
                    buttonFound = true
                    Thread.sleep(500)
                    break
                }
            }
            
            // Если кнопка не найдена, пробуем найти диалог по тексту и нажать на первую активную кнопку
            if (!buttonFound) {
                // Ищем диалог по тексту "notifications"
                val notificationText = device.findObject(
                    UiSelector().textMatches("(?i).*notification.*")
                )
                if (notificationText.exists()) {
                    // Пробуем найти любую кнопку в диалоге
                    val allowButton = device.findObject(
                        UiSelector().className("android.widget.Button")
                            .textMatches("(?i).*(allow|разрешить).*")
                    )
                    if (allowButton.exists()) {
                        allowButton.click()
                        Thread.sleep(500)
                    }
                }
            }
        } catch (e: Exception) {
            // Если произошла ошибка, пробуем найти и нажать на кнопку "Allow" по координатам
            // или просто ждем, возможно диалог уже закрыт
            Thread.sleep(300)
        }
    }
    
    /**
     * Универсальная функция для закрытия любых системных диалогов.
     * Проверяет наличие диалогов и закрывает их.
     */
    fun dismissAnySystemDialogs() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        
        try {
            // Проверяем наличие различных системных диалогов
            val dialogIndicators = listOf(
                UiSelector().textMatches("(?i).*alarm.*"),
                UiSelector().textMatches("(?i).*reminder.*"),
                UiSelector().textMatches("(?i).*notification.*"),
                UiSelector().textMatches("(?i).*permission.*"),
                UiSelector().textMatches("(?i).*разрешить.*"),
                UiSelector().textMatches("(?i).*allow.*"),
                UiSelector().resourceId("com.android.permissioncontroller:id/permission_allow_button"),
                UiSelector().resourceId("android:id/button1")
            )
            
            var dialogFound = false
            for (indicator in dialogIndicators) {
                val obj = device.findObject(indicator)
                if (obj.exists()) {
                    dialogFound = true
                    break
                }
            }
            
            // Если найден диалог с кнопкой "Allow", нажимаем на неё
            if (dialogFound) {
                val allowButton = device.findObject(
                    UiSelector().textMatches("(?i).*(allow|разрешить).*")
                        .className("android.widget.Button")
                )
                if (allowButton.exists() && allowButton.isEnabled) {
                    allowButton.click()
                    Thread.sleep(500)
                } else {
                    // Если кнопка "Allow" не найдена, нажимаем "Назад"
                    device.pressBack()
                    Thread.sleep(500)
                }
            } else {
                // На всякий случай нажимаем "Назад" один раз
                device.pressBack()
                Thread.sleep(300)
            }
        } catch (e: Exception) {
            // В случае ошибки просто нажимаем "Назад"
            device.pressBack()
            Thread.sleep(300)
        }
    }
    
    /**
     * Разворачивает приложение, если оно свернуто.
     * Использует UiAutomator для запуска приложения на переднем плане.
     */
    fun ensureAppInForeground() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = context.packageName
        
        try {
            // Проверяем, находится ли приложение на переднем плане
            val currentPackage = device.currentPackageName
            if (currentPackage != packageName) {
                // Приложение не на переднем плане - запускаем его через Intent
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Thread.sleep(1500) // Даем время на запуск
                    
                    // Ждем, пока приложение появится на переднем плане
                    device.waitForWindowUpdate(packageName, 3000)
                }
            } else {
                // Приложение уже на переднем плане, но может быть свернуто
                // Попробуем "разбудить" его, нажав на экран
                device.pressRecentApps()
                Thread.sleep(500)
                // Ищем приложение в списке недавних и кликаем на него
                val appWindow = device.findObject(UiSelector().packageName(packageName))
                if (appWindow.exists()) {
                    appWindow.click()
                    Thread.sleep(1000)
                } else {
                    // Если не нашли в списке, просто запускаем через Intent
                    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        Thread.sleep(1500)
                    }
                }
            }
        } catch (e: Exception) {
            // Если что-то пошло не так, просто запускаем через Intent
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Thread.sleep(1500)
                    device.waitForWindowUpdate(packageName, 3000)
                }
            } catch (e2: Exception) {
                // Игнорируем ошибки
            }
        }
    }

    /**
     * Ожидает закрытия системных диалогов и готовности UI приложения.
     * 
     * Обрабатывает последовательно:
     * 1. Разворачивает приложение, если оно свернуто
     * 2. Диалог "Alarms&reminders" (нажимает "Назад")
     * 3. Диалог разрешения на уведомления (нажимает "Allow")
     * 4. Проверяет и закрывает любые другие системные диалоги
     */
    fun waitForAppReady(composeRule: AndroidComposeTestRule<*, *>) {
        // Сначала убеждаемся, что приложение на переднем плане
        ensureAppInForeground()
        composeRule.waitForIdle()
        Thread.sleep(500)
        
        // Делаем несколько попыток обработать все диалоги
        for (attempt in 1..5) {
            // Обрабатываем диалог "Alarms&reminders"
            dismissAlarmsRemindersDialog()
            composeRule.waitForIdle()
            Thread.sleep(300)
            
            // Обрабатываем диалог разрешения на уведомления
            allowNotificationsDialog()
            composeRule.waitForIdle()
            Thread.sleep(300)
            
            // Универсальная обработка любых других диалогов
            dismissAnySystemDialogs()
            composeRule.waitForIdle()
            Thread.sleep(300)
        }
        
        // Ждем готовности Compose UI
        composeRule.waitForIdle()
        
        // Даем дополнительное время на инициализацию
        Thread.sleep(1000)
        composeRule.waitForIdle()
    }
}

