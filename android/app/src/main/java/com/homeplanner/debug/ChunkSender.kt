package com.homeplanner.debug

import android.content.Context
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
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Отправщик бинарных чанков логов на сервер.
 * 
 * Реализует потоковый формат v2 с периодической отправкой чанков.
 * Согласно LOGGING_FORMAT.md:
 * - Отправка каждые 15 секунд
 * - Только если предыдущий чанк был подтвержден (ACK)
 * - Протокол ACK/REPIT для обработки ошибок
 * 
 * ВАЖНО: Не использует android.util.Log для избежания зацикливания.
 */
class ChunkSender private constructor(
    private val context: Context,
    private val networkConfig: NetworkConfig,
    private val httpClient: OkHttpClient,
    private val scope: CoroutineScope,
    private val storage: BinaryLogStorage
) {
    private var senderJob: Job? = null
    private var isRunning = false
    private var lastAcknowledgedChunkId: Long? = null
    private var pendingChunks = mutableListOf<Pair<File, Long>>()
    @Volatile
    private var lastSendAttemptTime: Long? = null
    @Volatile
    private var lastSendResult: String? = null
    @Volatile
    private var lastSendChunkId: Long? = null
    
    companion object {
        private const val SEND_INTERVAL_MS = 15_000L  // 15 seconds
        private var instance: ChunkSender? = null

        /**
         * Запустить отправку бинарных чанков на сервер.
         * Только для дебаг-версий.
         */
        fun start(context: Context, networkConfig: NetworkConfig, storage: BinaryLogStorage) {
            if (!BuildConfig.DEBUG) {
                return
            }

            synchronized(this) {
                if (instance != null) {
                    return
                }

                val httpClient = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)  // Longer for binary uploads
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val scope = CoroutineScope(Dispatchers.IO)
                instance = ChunkSender(context, networkConfig, httpClient, scope, storage)
                instance!!.startInternal()
            }
        }

        /**
         * Получить экземпляр ChunkSender.
         */
        fun getInstance(): ChunkSender? = instance
        
        /**
         * Получить время последней попытки отправки чанка (в миллисекундах с эпохи).
         * Возвращает null, если отправка еще не выполнялась.
         */
        fun getLastSendAttemptTime(): Long? {
            return instance?.lastSendAttemptTime
        }
        
        /**
         * Получить статус ChunkSender.
         * Возвращает пару (isRunning, lastResult, lastChunkId), где:
         * - isRunning: запущен ли ChunkSender
         * - lastResult: результат последней отправки ("ACK", "REPIT", "ERROR", "UNRECOVERABLE", "NO_CHUNKS", null)
         * - lastChunkId: ID последнего отправленного чанка
         */
        fun getStatus(): Triple<Boolean, String?, Long?> {
            val inst = instance ?: return Triple(false, null, null)
            return Triple(inst.isRunning, inst.lastSendResult, inst.lastSendChunkId)
        }
        
        /**
         * Принудительно отправить следующий чанк (если есть).
         * Выполняется асинхронно в фоновом потоке.
         */
        fun forceSendNextChunk() {
            instance?.forceSendNextChunkInternal()
        }

        /**
         * Остановить отправку чанков.
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

        // Запустить периодическую отправку чанков
        senderJob = scope.launch {
            sendChunksLoop()
        }
    }

    private fun stopInternal() {
        if (!isRunning) return
        isRunning = false

        senderJob?.cancel()
        senderJob = null
    }

    /**
     * Цикл периодической отправки чанков.
     * Каждые 15 секунд завершает текущий чанк (если в нем есть записи) и отправляет его.
     */
    private suspend fun sendChunksLoop() {
        while (scope.isActive && isRunning) {
            try {
                // Ожидание интервала отправки
                delay(SEND_INTERVAL_MS)
                
                // Завершаем текущий активный чанк, если в нем есть записи
                if (storage.hasEntriesInCurrentChunk()) {
                    storage.closeCurrentChunk()
                }
                
                // Обновить список завершённых чанков
                updatePendingChunks()
                
                // Отправить следующий чанк
                sendNextChunk()
            } catch (e: Exception) {
                // Не логируем ошибки, чтобы не зациклиться
            }
        }
    }
    

    /**
     * Обновить список ожидающих отправки чанков.
     */
    private fun updatePendingChunks() {
        val completedChunks = storage.getCompletedChunks()
        
        // Фильтруем чанки: берем только те, которые еще не отправлены
        pendingChunks = completedChunks.filter { (file, chunkId) ->
            // Исключаем текущий активный чанк
            val currentChunkId = storage.getCurrentChunkId()
            chunkId != currentChunkId
        }.toMutableList()
    }

    /**
     * Принудительная отправка следующего чанка.
     * Выполняется в фоновом потоке.
     * 
     * Перед отправкой закрывает текущий активный чанк, чтобы гарантировать
     * полную запись всех данных.
     */
    private fun forceSendNextChunkInternal() {
        if (!isRunning) return
        
        scope.launch {
            // Закрываем текущий чанк перед отправкой, чтобы гарантировать
            // полную запись всех данных
            storage.closeCurrentChunk()
            
            // Обновляем список завершенных чанков
            updatePendingChunks()
            
            // Отправляем следующий чанк
            sendNextChunk()
        }
    }
    
    /**
     * Отправить следующий чанк, если есть ожидающие.
     */
    private suspend fun sendNextChunk() {
        // Обновляем время последней попытки отправки
        lastSendAttemptTime = System.currentTimeMillis()
        
        if (pendingChunks.isEmpty()) {
            lastSendResult = "NO_CHUNKS"
            lastSendChunkId = null
            return
        }

        // Берём первый чанк из очереди
        val (file, chunkId) = pendingChunks.first()
        lastSendChunkId = chunkId

        try {
            val result = sendChunkToServer(file, chunkId)
            
            when (result) {
                ChunkSendResult.ACK -> {
                    // Успешно отправлено - удаляем файл и чанк из очереди
                    file.delete()
                    pendingChunks.removeAt(0)
                    lastAcknowledgedChunkId = chunkId
                    lastSendResult = "ACK"
                }
                ChunkSendResult.REPIT -> {
                    // Нужна повторная отправка - оставляем в очереди
                    // При следующем цикле попробуем ещё раз
                    lastSendResult = "REPIT"
                }
                ChunkSendResult.UNRECOVERABLE -> {
                    // Чанк невосстановим - удаляем файл и чанк из очереди
                    file.delete()
                    pendingChunks.removeAt(0)
                    lastSendResult = "UNRECOVERABLE"
                }
                ChunkSendResult.ERROR -> {
                    // Ошибка сети - оставляем в очереди для повторной попытки
                    lastSendResult = "ERROR"
                }
            }
        } catch (e: Exception) {
            lastSendResult = "ERROR"
            // Не логируем ошибки, чтобы не зациклиться
        }
    }

    /**
     * Отправить чанк на сервер и получить результат.
     */
    private suspend fun sendChunkToServer(file: File, chunkId: Long): ChunkSendResult {
        try {
            val apiBaseUrl = networkConfig.toApiBaseUrl()
            val url = "$apiBaseUrl/debug-logs/chunks"

            // Читаем бинарные данные из файла
            val binaryData = file.readBytes()

            val mediaType = "application/octet-stream".toMediaType()
            val requestBody = binaryData.toRequestBody(mediaType)

            val deviceId = DeviceIdHelper.getDeviceId(context)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("X-Chunk-Id", chunkId.toString())
                .header("X-Device-Id", deviceId)
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                response.close()

                if (responseBody != null) {
                    val json = JSONObject(responseBody)
                    val result = json.optString("result", "")
                    val error = json.optString("error", null)

                    return when {
                        result == "ACK" && error == "UNRECOVERABLE_CHUNK" -> ChunkSendResult.UNRECOVERABLE
                        result == "ACK" -> ChunkSendResult.ACK
                        result == "REPIT" -> ChunkSendResult.REPIT
                        else -> ChunkSendResult.ERROR
                    }
                }
            }

            response.close()
            return ChunkSendResult.ERROR
        } catch (e: Exception) {
            return ChunkSendResult.ERROR
        }
    }

    /**
     * Результат отправки чанка.
     */
    private enum class ChunkSendResult {
        ACK,           // Успешно отправлено
        REPIT,         // Нужна повторная отправка
        UNRECOVERABLE, // Чанк невосстановим
        ERROR          // Ошибка сети или другая ошибка
    }
}

