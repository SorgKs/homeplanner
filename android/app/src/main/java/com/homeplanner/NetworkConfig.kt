package com.homeplanner

/**
 * Data class for network configuration.
 * Stores host, port, API version, and protocol (HTTP/HTTPS).
 */
data class NetworkConfig(
    val host: String,
    val port: Int,
    val apiVersion: String,
    val useHttps: Boolean = true
) {
    /**
     * Convert to API base URL (e.g., "https://192.168.1.2:8000/api/v0.3")
     */
    fun toApiBaseUrl(): String {
        // Валидация перед созданием URL
        if (host.isBlank()) {
            throw IllegalArgumentException("Host cannot be empty")
        }
        if (port !in 1..65535) {
            throw IllegalArgumentException("Port must be in range 1-65535, got: $port")
        }
        if (apiVersion.isBlank()) {
            throw IllegalArgumentException("API version cannot be empty")
        }
        
        val protocol = if (useHttps) "https" else "http"
        return "$protocol://$host:$port/api/v$apiVersion"
    }
    
    /**
     * Convert to WebSocket URL (e.g., "wss://192.168.1.2:8000/api/v0.3/tasks/stream")
     */
    fun toWebSocketUrl(): String {
        val protocol = if (useHttps) "wss" else "ws"
        return "$protocol://$host:$port/api/v$apiVersion/tasks/stream"
    }
    
    companion object {
        /**
         * Parse NetworkConfig from URL string.
         * Supports formats like:
         * - "http://192.168.1.2:8000/api/v0.3"
         * - "https://example.com:443/api/v0.3"
         */
        fun parseFromUrl(url: String): NetworkConfig? {
            return try {
                val trimmed = url.trimEnd('/')
                val protocolMatch = Regex("^(https?|wss?)://").find(trimmed)
                if (protocolMatch == null) return null
                
                val protocol = protocolMatch.value.removeSuffix("://")
                val useHttps = protocol == "https" || protocol == "wss"
                
                val withoutProtocol = trimmed.substringAfter("://")
                val apiMatch = Regex("/api/v([\\d.]+)").find(withoutProtocol)
                if (apiMatch == null) return null
                
                val apiVersion = apiMatch.groupValues[1]
                val beforeApi = withoutProtocol.substringBefore("/api")
                
                val hostPort = beforeApi.split(":")
                if (hostPort.isEmpty()) return null
                
                val host = hostPort[0]
                val port = if (hostPort.size > 1) {
                    hostPort[1].toIntOrNull() ?: return null
                } else {
                    if (useHttps) 443 else 80
                }
                
                if (host.isBlank() || port !in 1..65535) return null
                
                NetworkConfig(host, port, apiVersion, useHttps)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Supported API versions.
 */
object SupportedApiVersions {
    val versions = listOf("0.3", "0.2")
    val defaultVersion = versions.firstOrNull() ?: "0.3"
}

