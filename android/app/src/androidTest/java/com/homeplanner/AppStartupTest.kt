package com.homeplanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.homeplanner.database.AppDatabase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Instrumented test for basic app functionality.
 */
@RunWith(AndroidJUnit4::class)
class AppStartupTest {

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.homeplanner", appContext.packageName)
    }

    @Test
    fun testAppVersion() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        assertNotNull("App version should be set", packageInfo.versionName)
    }

    @Test
    fun testDatabaseInitialization() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        // Test that database can be initialized
        val db = AppDatabase.getDatabase(appContext)
        assertNotNull("Database should initialize", db)
    }

    @Test
    fun testNetworkSettingsInitialization() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val networkSettings = NetworkSettings(appContext)
        // Test that network settings can be initialized
        assertNotNull("NetworkSettings should initialize", networkSettings)
    }
}