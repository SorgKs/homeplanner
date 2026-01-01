package com.homeplanner.api

import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeplanner.model.Task
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TasksApiInstrumentedTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ServerApi

    private val sampleTask = Task(
        id = 123,
        title = "Полить цветы",
        description = "Каждое утро",
        taskType = "recurring",
        recurrenceType = "weekly",
        recurrenceInterval = 1,
        intervalDays = null,
        reminderTime = "2025-11-05T09:00:00",
        groupId = 2,
        active = true,
        completed = false,
        assignedUserIds = emptyList(),
        updatedAt = System.currentTimeMillis(),
        lastAccessed = System.currentTimeMillis(),
        lastShownAt = null,
        createdAt = System.currentTimeMillis()
    )

    @Before
    fun setUp() {
        try {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .permitAll()
                    .build(),
            )
            server = MockWebServer()
            // Пытаемся запустить сервер, если не получается - пробуем другой порт
            try {
                server.start()
            } catch (e: java.net.BindException) {
                // Если порт занят, пробуем с портом 0 (случайный свободный порт)
                android.util.Log.w("TasksApiInstrumentedTest", "Port busy, retrying with random port", e)
                server.shutdown()
                server = MockWebServer()
                server.start(0) // Случайный свободный порт
            }
            val baseUrl = server.url("/api/v0.2").toString().trimEnd('/')
            client = ServerApi(
                httpClient = OkHttpClient.Builder().build(),
                baseUrl = baseUrl,
            )
            android.util.Log.d("TasksApiInstrumentedTest", "MockWebServer started at $baseUrl")
        } catch (e: Exception) {
            android.util.Log.e("TasksApiInstrumentedTest", "setUp failed", e)
            e.printStackTrace()
            throw e
        }
    }

    @After
    fun tearDown() {
        try {
            server.shutdown()
        } catch (e: Exception) {
            // Игнорируем ошибки при закрытии сервера
        }
    }

    @Test
    fun getTasks_returnsParsedList() {
        try {
            android.util.Log.d("TasksApiInstrumentedTest", "Starting getTasks_returnsParsedList test")
            
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        [
                          {
                            "id": 1,
                            "title": "Позвонить родителям",
                            "description": null,
                            "task_type": "one_time",
                            "recurrence_type": null,
                            "recurrence_interval": null,
                            "interval_days": null,
                            "reminder_time": "2025-11-05T19:00:00",
                            "group_id": null,
                            "active": true,
                            "completed": false,
                            "assigned_user_ids": []
                          }
                        ]
                        """.trimIndent(),
                    ),
            )

            android.util.Log.d("TasksApiInstrumentedTest", "Calling client.getTasksServer()")
            val tasks = runBlocking { client.getTasksServer(activeOnly = false).getOrThrow() }
            android.util.Log.d("TasksApiInstrumentedTest", "Received ${tasks.size} tasks")
            
            val recordedRequest = server.takeRequest()
            android.util.Log.d("TasksApiInstrumentedTest", "Recorded request path: ${recordedRequest.path}")

            assertEquals("/api/v0.2/tasks/", recordedRequest.path)
            assertEquals(1, tasks.size)
            val task = tasks.first()
            assertEquals("Позвонить родителям", task.title)
            assertTrue(task.active)
            assertFalse(task.completed)
            
            android.util.Log.d("TasksApiInstrumentedTest", "Test completed successfully")
        } catch (e: Exception) {
            // Логируем исключение для диагностики
            android.util.Log.e("TasksApiInstrumentedTest", "Test failed with exception", e)
            e.printStackTrace()
            throw e
        } catch (e: Error) {
            // Логируем ошибки (не исключения)
            android.util.Log.e("TasksApiInstrumentedTest", "Test failed with error", e)
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun getTasks_withActiveOnlyTrue_appendsQuery() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[]"),
        )

        runBlocking { client.getTasksServer().getOrThrow() }
        val recordedRequest = server.takeRequest()

        assertEquals("/api/v0.2/tasks/?active_only=true", recordedRequest.path)
    }

    @Test
    fun getTasks_errorResponse_throws() {
        server.enqueue(MockResponse().setResponseCode(500))

        try {
            runBlocking { client.getTasksServer(activeOnly = false).getOrThrow() }
            fail("Ожидалось исключение при ошибочном ответе сервера.")
        } catch (expected: IllegalStateException) {
            // ok
        }
    }

    @Test
    fun getTodayTaskIds_hitsTodayIdsEndpoint() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[1, 2, 3]"),
        )

        val ids = runBlocking { client.getTodayTaskIds() }
        val recordedRequest = server.takeRequest()
        assertEquals("/api/v0.2/tasks/today/ids", recordedRequest.path)
        assertEquals(3, ids.size)
        assertEquals(listOf(1, 2, 3), ids)
    }

    @Test
    fun createTask_sendsPayloadAndParsesResponse() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "id": 555,
                      "title": "Полить цветы",
                      "description": "Каждое утро",
                      "task_type": "recurring",
                      "recurrence_type": "weekly",
                      "recurrence_interval": 1,
                      "interval_days": null,
                      "reminder_time": "2025-11-05T09:00:00",
                      "group_id": 2,
                      "active": true,
                      "completed": false
                    }
                    """.trimIndent(),
                ),
        )

        val created = runBlocking { client.createTaskServer(sampleTask).getOrThrow() }
        val recordedRequest = server.takeRequest()
        val bodyJson = JSONObject(recordedRequest.body.readUtf8())

        assertEquals("/api/v0.2/tasks/", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)
        assertEquals(sampleTask.title, bodyJson.getString("title"))
        assertEquals(sampleTask.reminderTime, bodyJson.getString("reminder_time"))
        assertEquals(555, created.id)
        assertEquals(sampleTask.title, created.title)
    }

    @Test
    fun updateTask_sendsPayloadAndParsesResponse() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "id": 123,
                      "title": "Полить цветы",
                      "description": "Каждое утро",
                      "task_type": "recurring",
                      "recurrence_type": "weekly",
                      "recurrence_interval": 1,
                      "interval_days": null,
                      "reminder_time": "2025-11-05T10:00:00",
                      "group_id": 2,
                      "active": true,
                      "completed": false
                    }
                    """.trimIndent(),
                ),
        )

        val updated = runBlocking { client.updateTaskServer(123, sampleTask).getOrThrow() }
        val recordedRequest = server.takeRequest()

        assertEquals("/api/v0.2/tasks/123", recordedRequest.path)
        assertEquals("PUT", recordedRequest.method)
        assertEquals("2025-11-05T10:00:00", updated.reminderTime)
    }

    @Test
    fun completeTask_hitsCompleteEndpoint() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "id": 123,
                      "title": "Полить цветы",
                      "description": "Каждое утро",
                      "task_type": "recurring",
                      "recurrence_type": "weekly",
                      "recurrence_interval": 1,
                      "interval_days": null,
                      "reminder_time": "2025-11-05T09:00:00",
                      "group_id": 2,
                      "active": true,
                      "completed": true
                    }
                    """.trimIndent(),
                ),
        )

        val completed = runBlocking { client.completeTaskServer(123).getOrThrow() }
        val recordedRequest = server.takeRequest()

        assertEquals("/api/v0.2/tasks/123/complete", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)
        assertTrue(completed.completed)
    }

    @Test
    fun uncompleteTask_hitsUncompleteEndpoint() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "id": 123,
                      "title": "Полить цветы",
                      "description": "Каждое утро",
                      "task_type": "recurring",
                      "recurrence_type": "weekly",
                      "recurrence_interval": 1,
                      "interval_days": null,
                      "reminder_time": "2025-11-05T09:00:00",
                      "group_id": 2,
                      "active": true,
                      "completed": false
                    }
                    """.trimIndent(),
                ),
        )

        val uncompleted = runBlocking { client.uncompleteTaskServer(123).getOrThrow() }
        val recordedRequest = server.takeRequest()

        assertEquals("/api/v0.2/tasks/123/uncomplete", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)
        assertFalse(uncompleted.completed)
    }

    @Test
    fun deleteTask_sendsDeleteRequest() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(""),
        )

        runBlocking { client.deleteTaskServer(321).getOrThrow() }
        val recordedRequest = server.takeRequest()

        assertEquals("/api/v0.2/tasks/321", recordedRequest.path)
        assertEquals("DELETE", recordedRequest.method)
    }
}


