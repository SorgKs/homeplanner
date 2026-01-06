package com.homeplanner.debug

/**
 * Запись лога в бинарном формате.
 * 
 * Соответствует спецификации из LOGGING_FORMAT.md.
 */
data class BinaryLogEntry(
    val timestamp: Long,           // Время записи лога (число 10мс интервалов от начала дня)
    val level: LogLevel,           // Уровень логирования
    val tag: String,               // Тег компонента (например, "SyncService", "LocalApi")
    val messageCode: UShort,       // Числовой код сообщения (2 байта)
    val context: List<Any> = emptyList()  // Список значений контекста в порядке схемы (без ключей)
) {
    /**
     * Конвертировать в формат для отправки на сервер (JSON).
     */
    fun toJsonMap(): Map<String, Any> {
        return mapOf(
            "timestamp" to timestamp,
            "level" to level.name,
            "tag" to tag,
            "message_code" to messageCode.toInt(),
            "context" to context
        )
    }
}

/**
 * Заголовок файла лога.
 */
data class LogFileHeader(
    val dictionaryRevision: DictionaryRevision,  // Ревизия словаря для этого файла
    val formatVersion: String = "1.0",           // Версия формата логов
    val createdAt: Long = System.currentTimeMillis(),  // Время создания файла
    val deviceId: String? = null                 // ID устройства (опционально)
)
