package com.homeplanner.services

import okhttp3.Response

class ConnectionStatusManager {

    fun getConnectionStatus(): ConnectionStatus {
        // TODO: Реализовать определение статуса соединения
        return ConnectionStatus.UNKNOWN
    }

    fun updateConnectionStatusFromResponse(response: Response): ConnectionStatus {
        return analyzeHttpResponse(response)
    }

    fun updateConnectionStatusFromError(error: Throwable): ConnectionStatus {
        return analyzeNetworkError(error)
    }

    fun isCriticalError(error: Throwable): Boolean {
        // TODO: Определить критические ошибки
        return false
    }

    fun shouldRetry(error: Throwable): Boolean {
        // TODO: Определить, стоит ли повторять запрос
        return true
    }

    private fun analyzeHttpResponse(response: Response): ConnectionStatus {
        return when (response.code) {
            in 200..299 -> ConnectionStatus.ONLINE
            in 500..599 -> ConnectionStatus.OFFLINE
            else -> ConnectionStatus.DEGRADED
        }
    }

    private fun analyzeNetworkError(error: Throwable): ConnectionStatus {
        // TODO: Анализ сетевых ошибок
        return ConnectionStatus.OFFLINE
    }

    private fun updateLastSuccessfulRequest() {
        // TODO: Обновить время последнего успешного запроса
    }

    enum class ConnectionStatus {
        UNKNOWN, ONLINE, DEGRADED, OFFLINE
    }
}