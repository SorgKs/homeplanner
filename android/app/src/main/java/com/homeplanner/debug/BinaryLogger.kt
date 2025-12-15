package com.homeplanner.debug

import android.content.Context
import com.homeplanner.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Утилита для бинарного логирования согласно спецификации LOGGING_FORMAT.md.
 * 
 * Сохраняет логи в бинарном формате с использованием кодов сообщений вместо текста.
 * Логи буферизуются и отправляются на сервер через LogSender.
 * 
 * ВАЖНО: Этот класс НЕ использует android.util.Log для избежания зацикливания.
 */
class BinaryLogger private constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val logBuffer = ConcurrentLinkedQueue<BinaryLogEntry>()
    private var logSender: LogSender? = null
    private val binaryStorage: BinaryLogStorage = BinaryLogStorage(context)

    companion object {
        private var instance: BinaryLogger? = null

        /**
         * Инициализировать бинарный логгер.
         * Только для дебаг-версий.
         */
        fun initialize(context: Context) {
            if (!BuildConfig.DEBUG) return
            
            synchronized(this) {
                if (instance != null) return
                
                val scope = CoroutineScope(Dispatchers.IO)
                instance = BinaryLogger(context, scope)
            }
        }

    /**
     * Получить экземпляр логгера.
     */
    fun getInstance(): BinaryLogger? = instance
    
    /**
     * Получить экземпляр BinaryLogStorage для ChunkSender и статистики.
     */
    fun getStorage(): BinaryLogStorage? = instance?.binaryStorage

    /**
     * Остановить логгер и отправить оставшиеся логи.
     */
    fun shutdown() {
        synchronized(this) {
            instance?.shutdownInternal()
            instance = null
        }
    }
    }

    /**
     * Установить LogSender для отправки логов на сервер.
     */
    fun setLogSender(sender: LogSender) {
        logSender = sender
    }

    /**
     * Записать лог.
     */
    fun log(
        level: LogLevel,
        tag: String,
        messageCode: String,
        context: Map<String, Any> = emptyMap()
    ) {
        if (!BuildConfig.DEBUG) return

        val entry = BinaryLogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            messageCode = messageCode,
            context = context
        )

        logBuffer.offer(entry)

        // Пишем запись в компактный бинарный файл (чанк текущего дня).
        scope.launch {
            binaryStorage.append(entry)
        }

        // Автоматически отправляем при достижении определенного размера буфера
        if (logBuffer.size >= 50) {
            flush()
        }
    }

    /**
     * Отправить все накопленные логи через LogSender.
     */
    fun flush() {
        if (logBuffer.isEmpty()) return

        val logsToSend = mutableListOf<BinaryLogEntry>()
        while (logBuffer.isNotEmpty()) {
            logBuffer.poll()?.let { logsToSend.add(it) }
        }

        if (logsToSend.isNotEmpty()) {
            logSender?.sendBinaryLogs(logsToSend)
        }
    }

    /**
     * Внутреннее завершение работы логгера:
     * - отправка накопленных логов;
     * - закрытие бинарного хранилища.
     */
    private fun shutdownInternal() {
        flush()
        binaryStorage.close()
    }

    /**
     * Получить текущий размер буфера.
     */
    fun getBufferSize(): Int = logBuffer.size
}
