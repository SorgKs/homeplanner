package com.homeplanner.debug

import android.content.Context
import com.homeplanner.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
     * @param code Числовой код сообщения (UShort, 2 байта)
     * @param context Список значений контекста в порядке схемы (без ключей)
     */
    fun log(
        code: UShort,
        context: List<Any> = emptyList()
    ) {
        if (!BuildConfig.DEBUG) return

        val entry = BinaryLogEntry(
            timestamp = System.currentTimeMillis(),
            level = LogLevel.INFO,
            tag = "",
            messageCode = code,
            context = context
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
