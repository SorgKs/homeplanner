package com.homeplanner.debug

import android.content.Context
import com.homeplanner.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Утилита для бинарного логирования согласно спецификации LOGGING_FORMAT.md.
 * 
 * Сохраняет логи в бинарном формате с использованием кодов сообщений вместо текста.
 * 
 * ВАЖНО: Этот класс НЕ использует android.util.Log для избежания зацикливания.
 */
class BinaryLogger private constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {
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
     * Записать лог.
     *
     * Автоматически добавляет file (byte) и line (int) поля в конец контекста.
     *
     * @param code Числовой код сообщения (UShort, 2 байта)
     * @param context Список значений контекста в порядке схемы (без ключей)
     * @param fileCode Код файла (Byte, 1 байт)
     */
    fun log(
        code: UShort,
        context: List<Any> = emptyList(),
        fileCode: Byte
    ) {
        if (!BuildConfig.DEBUG) return

        // Получить line из стека вызовов
        val stackTrace = Thread.currentThread().stackTrace
        val caller = stackTrace.getOrNull(4) // 4-й элемент - вызывающий метод (после log() -> getInstance() -> вызывающий)

        val lineNumber = caller?.lineNumber ?: -1

        // Добавить file и line в конец контекста
        val fullContext = context + fileCode + lineNumber

        val now = LocalDateTime.now()
        val startOfDay = LocalDate.now().atStartOfDay()
        val duration = Duration.between(startOfDay, now)
        val intervals = duration.toMillis() / 10
        val entry = BinaryLogEntry(
            timestamp = intervals,
            level = LogLevel.INFO,
            tag = "",
            messageCode = code,
            context = fullContext
        )

        // Пишем запись в компактный бинарный файл (чанк текущего дня).
        scope.launch {
            binaryStorage.append(entry)
        }
    }



    /**
     * Внутреннее завершение работы логгера:
     * - закрытие бинарного хранилища.
     */
    private fun shutdownInternal() {
        binaryStorage.close()
    }
}
