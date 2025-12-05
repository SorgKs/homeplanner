package com.homeplanner.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        composeRule.onNodeWithText("Настройки").performClick()
        val expected = "Версия: ${BuildConfig.VERSION_NAME}"
        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }
}
