package com.homeplanner.debug

import android.content.Context
import android.os.Build
import com.homeplanner.BuildConfig
import com.homeplanner.NetworkConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Утилита для отправки бинарных логов на сервер.
 * 
 * Работает с бинарным форматом логирования согласно LOGGING_FORMAT.md.
 * Принимает логи от BinaryLogger и отправляет их на сервер в батчах.
 * Только для дебаг-версий.
 * 
 * IMPORTANT: This class intentionally does NOT use android.util.Log to avoid
 * infinite loops (logging -> sending logs -> logging again). All errors are
 * silently handled to prevent circular logging.
 * 
 * Usage:
 * ```kotlin
 * if (BuildConfig.DEBUG) {
 *     LogSender.start(context, networkConfig)
 *     BinaryLogger.getInstance()?.setLogSender(LogSender.getInstance()!!)
 * }
 * ```
 */
class LogSender private constructor(
    private val context: Context,
    private val networkConfig: NetworkConfig,
    private val httpClient: OkHttpClient,
    private val scope: CoroutineScope
) {
    private var logSenderJob: Job? = null
    private val logBuffer = ConcurrentLinkedQueue<BinaryLogEntry>()
    private var isRunning = false

    companion object {
        private const val LOG_BUFFER_SIZE = 50  // Send logs in batches of 50
        private const val LOG_SEND_INTERVAL_MS = 30_000L  // Send every 30 seconds
        private var instance: LogSender? = null

        /**
         * Start sending logs to the server.
         * Only works in debug builds.
         * 
         * @param context Application context
         * @param networkConfig Network configuration for API base URL
         */
        fun start(context: Context, networkConfig: NetworkConfig) {
            if (!BuildConfig.DEBUG) {
                return
            }

            synchronized(this) {
                if (instance != null) {
                    return
                }

                val httpClient = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val scope = CoroutineScope(Dispatchers.IO)
                instance = LogSender(context, networkConfig, httpClient, scope)
                instance!!.startInternal()
            }
        }

        /**
         * Получить экземпляр LogSender.
         */
        fun getInstance(): LogSender? = instance

        /**
         * Stop sending logs to the server.
         */
        fun stop() {
            synchronized(this) {
                instance?.stopInternal()
                instance = null
            }
        }
    }

    private fun startInternal() {
        if (isRunning) return
        isRunning = true

        // Start log sender (sends batches to server periodically)
        logSenderJob = scope.launch {
            sendLogsLoop()
        }
    }

    private fun stopInternal() {
        if (!isRunning) return
        isRunning = false

        logSenderJob?.cancel()
        logSenderJob = null

        // Send remaining logs before stopping
        flush()
    }

    /**
     * Добавить лог в буфер для отправки.
     */
    fun addLog(entry: BinaryLogEntry) {
        if (!isRunning) return
        logBuffer.offer(entry)
    }

    /**
     * Отправить бинарные логи на сервер.
     * Вызывается из BinaryLogger.
     */
    fun sendBinaryLogs(logs: List<BinaryLogEntry>) {
        if (logs.isEmpty()) return
        logBuffer.addAll(logs)
    }

    /**
     * Отправить все накопленные логи на сервер.
     */
    fun flush() {
        val logsToSend = mutableListOf<BinaryLogEntry>()
        while (logBuffer.isNotEmpty() && logsToSend.size < LOG_BUFFER_SIZE) {
            logBuffer.poll()?.let { logsToSend.add(it) }
        }

        if (logsToSend.isNotEmpty()) {
            scope.launch {
                sendLogsToServer(logsToSend)
            }
        }
    }

    private suspend fun sendLogsLoop() {
        while (scope.isActive && isRunning) {
            try {
                delay(LOG_SEND_INTERVAL_MS)
                flush()
            } catch (e: Exception) {
                // Don't log errors to avoid infinite loop
            }
        }
    }

    private suspend fun sendLogsToServer(logs: List<BinaryLogEntry>) {
        try {
            val apiBaseUrl = networkConfig.toApiBaseUrl()
            val url = "$apiBaseUrl/debug-logs"

            val deviceId = DeviceIdHelper.getDeviceId(context)
            val deviceInfo = getDeviceInfo()
            val appVersion = getAppVersion()
            val dictionaryRevision = DictionaryRevision.CURRENT

            val logsArray = JSONArray()
            for (log in logs) {
                // Convert context map to JSONObject
                val contextObj = JSONObject()
                for ((key, value) in log.context) {
                    when (value) {
                        is String -> contextObj.put(key, value)
                        is Number -> contextObj.put(key, value)
                        is Boolean -> contextObj.put(key, value)
                        else -> contextObj.put(key, value.toString())
                    }
                }
                
                val logObj = JSONObject().apply {
                    put("timestamp", java.time.Instant.ofEpochMilli(log.timestamp).toString())
                    put("level", log.level.name)
                    put("tag", log.tag)
                    put("message_code", log.messageCode)  // Код сообщения вместо текста
                    put("context", contextObj)
                    put("device_id", deviceId)  // Уникальный идентификатор устройства
                    put("device_info", deviceInfo)
                    put("app_version", appVersion)
                    put("dictionary_revision", "${dictionaryRevision.major}.${dictionaryRevision.minor}")
                }
                logsArray.put(logObj)
            }

            val batchObj = JSONObject().apply {
                put("logs", logsArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = batchObj.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                // Put logs back in buffer for retry (limited size)
                if (logBuffer.size < LOG_BUFFER_SIZE * 2) {
                    logs.forEach { logBuffer.offer(it) }
                }
            }

            response.close()
        } catch (e: Exception) {
            // Don't log errors to avoid infinite loop
            // Put logs back in buffer for retry (limited size)
            if (logBuffer.size < LOG_BUFFER_SIZE * 2) {
                logs.forEach { logBuffer.offer(it) }
            }
        }
    }

    private fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
