package com.homeplanner

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "network_settings")

/**
 * Class for managing network settings using DataStore.
 */
class NetworkSettings(private val context: Context) {
    private val dataStore: DataStore<Preferences> = context.dataStore
    
    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val API_VERSION = stringPreferencesKey("api_version")
        val USE_HTTPS = booleanPreferencesKey("use_https")
    }
    
    /**
     * Flow that emits current network configuration.
     * Returns null if settings are not configured.
     */
    val configFlow: Flow<NetworkConfig?> = dataStore.data.map { prefs ->
        val host = prefs[Keys.HOST]
        val port = prefs[Keys.PORT]
        val apiVersion = prefs[Keys.API_VERSION]
        val useHttps = prefs[Keys.USE_HTTPS] ?: true
        
        if (host != null && port != null && apiVersion != null) {
            NetworkConfig(host, port, apiVersion, useHttps)
        } else {
            null
        }
    }
    
    /**
     * Save network configuration.
     */
    suspend fun saveConfig(config: NetworkConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.HOST] = config.host
            prefs[Keys.PORT] = config.port
            prefs[Keys.API_VERSION] = config.apiVersion
            prefs[Keys.USE_HTTPS] = config.useHttps
        }
    }
    
    /**
     * Clear network configuration (reset to unconfigured state).
     */
    suspend fun clearConfig() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.HOST)
            prefs.remove(Keys.PORT)
            prefs.remove(Keys.API_VERSION)
            prefs.remove(Keys.USE_HTTPS)
        }
    }

    /**
     * Get current API base URL from settings.
     * Returns null if settings are not configured.
     */
    suspend fun getApiBaseUrl(): String? {
        return configFlow.first()?.toApiBaseUrl()
    }

    /**
     * Get current WebSocket URL from settings.
     * Returns null if settings are not configured.
     */
    suspend fun getWebSocketUrl(): String? {
        return configFlow.first()?.toWebSocketUrl()
    }
    
    /**
     * Parse and save network configuration from JSON string (for QR code scanning).
     * Returns true if successful, false otherwise.
     */
    suspend fun parseAndSaveFromJson(jsonString: String): Boolean {
        return try {
            val config = parseNetworkConfigFromJson(jsonString) ?: return false
            saveConfig(config)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Parse NetworkConfig from JSON string (simplified format for QR codes).
 * Format: {"host": "192.168.1.2", "port": 8000, "apiVersion": "0.2", "useHttps": true}
 */
fun parseNetworkConfigFromJson(jsonString: String): NetworkConfig? {
    return try {
        val json = org.json.JSONObject(jsonString)
        
        val host = json.getString("host")
        val port = json.getInt("port")
        val apiVersion = json.getString("apiVersion")
        val useHttps = json.optBoolean("useHttps", true)
        
        // Validation
        if (host.isBlank() || port !in 1..65535 || apiVersion.isBlank()) {
            return null
        }
        
        // Check that API version is supported
        if (apiVersion !in SupportedApiVersions.versions) {
            return null
        }
        
        NetworkConfig(host, port, apiVersion, useHttps)
    } catch (e: Exception) {
        null
    }
}

