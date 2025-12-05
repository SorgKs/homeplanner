package com.homeplanner

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_settings"
)

data class SelectedUser(
    val id: Int,
    val name: String,
)

/**
 * Stores the currently selected user for filtering tasks.
 */
class UserSettings(private val context: Context) {
    private val dataStore = context.userSettingsDataStore

    private object Keys {
        val USER_ID = intPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val CONNECTION_CHECK_INTERVAL_MINUTES = intPreferencesKey("connection_check_interval_minutes")
    }

    /**
     * Flow with the currently selected user or null when not configured.
     */
    val selectedUserFlow: Flow<SelectedUser?> = dataStore.data.map { prefs ->
        val id = prefs[Keys.USER_ID]
        val name = prefs[Keys.USER_NAME]
        if (id != null && !name.isNullOrBlank()) {
            SelectedUser(id = id, name = name)
        } else {
            null
        }
    }

    /**
     * Persist selected user.
     */
    suspend fun saveSelectedUser(user: SelectedUser) {
        dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.USER_NAME] = user.name
        }
    }

    /**
     * Remove selected user.
     */
    suspend fun clearSelectedUser() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USER_NAME)
        }
    }

    /**
     * Flow with connection check interval in minutes (default: 5).
     */
    val connectionCheckIntervalMinutesFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.CONNECTION_CHECK_INTERVAL_MINUTES] ?: 5
    }

    /**
     * Get connection check interval in minutes (default: 5).
     */
    suspend fun getConnectionCheckIntervalMinutes(): Int {
        val prefs = dataStore.data.first()
        return prefs[Keys.CONNECTION_CHECK_INTERVAL_MINUTES] ?: 5
    }

    /**
     * Set connection check interval in minutes.
     */
    suspend fun setConnectionCheckIntervalMinutes(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.CONNECTION_CHECK_INTERVAL_MINUTES] = minutes
        }
    }
}

