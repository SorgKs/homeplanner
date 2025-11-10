package com.homeplanner.api

import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeplanner.model.Task
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
    private lateinit var client: TasksApi

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
    )

    @Before
    fun setUp() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .permitAll()
                .build(),
        )
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/api/v0.2").toString().trimEnd('/')
        client = TasksApi(
            httpClient = OkHttpClient.Builder().build(),
            baseUrl = baseUrl,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getTasks_returnsParsedList() {
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
                        "completed": false
                      }
                    ]
                    """.trimIndent(),
                ),
        )

        val tasks = client.getTasks(activeOnly = false)
        val recordedRequest = server.takeRequest()

        assertEquals("/api/v0.2/tasks/", recordedRequest.path)
        assertEquals(1, tasks.size)
        val task = tasks.first()
        assertEquals("Позвонить родителям", task.title)
        assertTrue(task.active)
        assertFalse(task.completed)
    }

    @Test
    fun getTasks_withActiveOnlyTrue_appendsQuery() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[]"),
        )

        client.getTasks()
        val recordedRequest = server.takeRequest()

        assertEquals("/api/v0.2/tasks/?active_only=true", recordedRequest.path)
    }

    @Test
    fun getTasks_errorResponse_throws() {
        server.enqueue(MockResponse().setResponseCode(500))

        try {
            client.getTasks(activeOnly = false)
            fail("Ожидалось исключение при ошибочном ответе сервера.")
        } catch (expected: IllegalStateException) {
            // ok
        }
    }

    @Test
    fun getTodayTasks_hitsTodayEndpoint() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[]"),
        )

        client.getTodayTasks()
        val recordedRequest = server.takeRequest()
        assertEquals("/api/v0.2/tasks/today", recordedRequest.path)
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

        val created = client.createTask(sampleTask)
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

        val updated = client.updateTask(123, sampleTask)
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

        val completed = client.completeTask(123)
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

        val uncompleted = client.uncompleteTask(123)
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

        client.deleteTask(321)
        val recordedRequest = server.takeRequest()

        assertEquals("/api/v0.2/tasks/321", recordedRequest.path)
        assertEquals("DELETE", recordedRequest.method)
    }
}


