package com.homeplanner.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.UnknownHostException

@RunWith(AndroidJUnit4::class)
class ConnectionStatusManagerTest {

    private val connectionStatusManager = ConnectionStatusManager()

    @Test
    fun getConnectionStatus_returnsUnknown() {
        // When
        val status = connectionStatusManager.getConnectionStatus()

        // Then
        assertEquals(ConnectionStatusManager.ConnectionStatus.UNKNOWN, status)
    }

    @Test
    fun updateConnectionStatusFromResponse_successResponse_returnsOnline() {
        // Given
        val response = createMockResponse(200)

        // When
        val status = connectionStatusManager.updateConnectionStatusFromResponse(response)

        // Then
        assertEquals(ConnectionStatusManager.ConnectionStatus.ONLINE, status)
    }

    @Test
    fun updateConnectionStatusFromResponse_serverError_returnsOffline() {
        // Given
        val response = createMockResponse(500)

        // When
        val status = connectionStatusManager.updateConnectionStatusFromResponse(response)

        // Then
        assertEquals(ConnectionStatusManager.ConnectionStatus.OFFLINE, status)
    }

    @Test
    fun updateConnectionStatusFromResponse_clientError_returnsDegraded() {
        // Given
        val response = createMockResponse(404)

        // When
        val status = connectionStatusManager.updateConnectionStatusFromResponse(response)

        // Then
        assertEquals(ConnectionStatusManager.ConnectionStatus.DEGRADED, status)
    }

    @Test
    fun updateConnectionStatusFromError_unknownHostException_returnsOffline() {
        // Given
        val error = UnknownHostException("Host not found")

        // When
        val status = connectionStatusManager.updateConnectionStatusFromError(error)

        // Then
        assertEquals(ConnectionStatusManager.ConnectionStatus.OFFLINE, status)
    }

    @Test
    fun updateConnectionStatusFromError_genericException_returnsOffline() {
        // Given
        val error = RuntimeException("Network error")

        // When
        val status = connectionStatusManager.updateConnectionStatusFromError(error)

        // Then
        assertEquals(ConnectionStatusManager.ConnectionStatus.OFFLINE, status)
    }

    @Test
    fun isCriticalError_returnsFalse() {
        // Given - метод ещё не реализован, всегда возвращает false
        val error = RuntimeException("Test error")

        // When
        val isCritical = connectionStatusManager.isCriticalError(error)

        // Then
        assertFalse(isCritical)
    }

    @Test
    fun shouldRetry_returnsTrue() {
        // Given - метод ещё не реализован, всегда возвращает true
        val error = RuntimeException("Test error")

        // When
        val shouldRetry = connectionStatusManager.shouldRetry(error)

        // Then
        assertTrue(shouldRetry)
    }

    private fun createMockResponse(code: Int): Response {
        return Response.Builder()
            .request(Request.Builder().url("http://test.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Test")
            .body("".toResponseBody())
            .build()
    }
}