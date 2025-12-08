# Формат логирования в Android приложении HomePlanner

> **Связанные документы:** [OFFLINE_FIRST_ARCHITECTURE.md](OFFLINE_FIRST_ARCHITECTURE.md)

## Обзор

Данный документ описывает формат логирования, используемый в Android приложении HomePlanner для отслеживания состояния синхронизации, диагностики проблем и анализа производительности.

## Формат хранения логов

### Бинарный формат

- **Все логи сохраняются в бинарном виде** для экономии места и быстрого доступа
- Использование протокола сериализации (Protocol Buffers, FlatBuffers или собственный бинарный формат)
- Структурированное хранение с метаданными (timestamp, уровень, тег, код сообщения, контекст)
- Хранение в файловой системе приложения или Room database

### Структура записи лога

```kotlin
data class LogEntry(
    val timestamp: Long,           // Время записи лога
    val level: LogLevel,            // Уровень логирования (DEBUG, INFO, WARN, ERROR)
    val tag: String,                 // Тег компонента (например, "SyncService", "LocalApi")
    val messageCode: String,         // Код сообщения из словаря (см. ниже)
    val context: Map<String, Any>   // Дополнительный контекст (ID задачи, размер очереди и т.д.)
)

data class DictionaryRevision(
    val major: Int,  // Мажорная версия
    val minor: Int   // Минорная версия
)

enum class LogLevel {
    DEBUG,  // Детальная информация для разработки
    INFO,   // Важные события
    WARN,   // Предупреждения
    ERROR   // Критические ошибки
}
```

**Важно:** В каждом файле лога должна указываться ревизия словаря сообщений в заголовке файла. Все записи в файле используют одну и ту же ревизию словаря.

## Словарь сообщений приложения

### Концепция

- **Цель:** Экономия места в бинарных логах за счёт использования кодов вместо текстовых описаний
- Создаётся словарь сообщений, где каждому событию присвоено:
  - **Код** (числовой или короткий строковый идентификатор)
  - **Текстовое описание** (полное описание события)
- В приложении для логирования используется **только код**
- Текстовое описание **не загружается в приложение** (хранится только на сервере или в документации)
- При отправке логов на сервер коды расшифровываются в текстовые описания

### Структура словаря

```kotlin
// На сервере/в документации
data class LogMessage(
    val code: String,        // Например: "SYNC_START", "SYNC_FAIL_500", "TASK_CREATE"
    val description: String  // Например: "Синхронизация начата", "Ошибка сервера 500", "Создана задача"
)

// В приложении - только коды
enum class LogMessageCode(val code: String) {
    // Синхронизация
    SYNC_START("SYNC_START"),
    SYNC_SUCCESS("SYNC_SUCCESS"),
    SYNC_FAIL_NETWORK("SYNC_FAIL_NETWORK"),
    SYNC_FAIL_500("SYNC_FAIL_500"),
    SYNC_FAIL_503("SYNC_FAIL_503"),
    SYNC_FAIL_409("SYNC_FAIL_409"),
    SYNC_FAIL_400("SYNC_FAIL_400"),
    SYNC_FAIL_401("SYNC_FAIL_401"),
    SYNC_FAIL_403("SYNC_FAIL_403"),
    
    // Задачи
    TASK_CREATE("TASK_CREATE"),
    TASK_UPDATE("TASK_UPDATE"),
    TASK_DELETE("TASK_DELETE"),
    TASK_COMPLETE("TASK_COMPLETE"),
    TASK_UNCOMPLETE("TASK_UNCOMPLETE"),
    
    // Очередь
    QUEUE_ADD("QUEUE_ADD"),
    QUEUE_CLEAR("QUEUE_CLEAR"),
    QUEUE_SIZE("QUEUE_SIZE"),
    
    // Состояние
    CONNECTION_ONLINE("CONNECTION_ONLINE"),
    CONNECTION_OFFLINE("CONNECTION_OFFLINE"),
    CACHE_UPDATED("CACHE_UPDATED"),
    
    // ... другие коды
}
```

### Использование в логировании

```kotlin
// Вместо текстового сообщения
Log.i(TAG, "Sync started", mapOf(...))

// Используем код
Log.i(TAG, LogMessageCode.SYNC_START.code, mapOf(
    "queueSize" to queueItems.size,
    "timestamp" to System.currentTimeMillis()
))

// Пример с ошибкой
Log.e(TAG, LogMessageCode.SYNC_FAIL_500.code, mapOf(
    "errorType" to SyncErrorType.SERVER_ERROR.name,
    "httpCode" to 500,
    "taskId" to taskId,
    "retryAttempt" to attempt,
    "timestamp" to System.currentTimeMillis()
))
```

### Преимущества

- Значительная экономия места в бинарных логах
- Быстрая запись и чтение (коды короче текстов)
- Единообразие логирования (стандартизированные коды)
- Расшифровка на сервере при анализе

### Версионирование словаря

**Структура ревизии:**

- Ревизия словаря состоит из **мажорной** и **минорной** версий (например, 1.0, 1.1, 2.0)
- Формат: `major.minor` (например, `1.0`, `1.2`, `2.0`)

**Правила изменения словаря:**

1. **В рамках одной мажорной версии:**
   - **Не допускается изменение существующих кодов** (код не может быть изменён или удалён)
   - **Разрешено только добавление новых кодов** (минорная версия увеличивается)
   - Пример: версия 1.0 → 1.1 (добавлены новые коды)

2. **При изменении мажорной версии:**
   - Разрешено изменение или удаление существующих кодов
   - Разрешено добавление новых кодов
   - Пример: версия 1.5 → 2.0 (изменены или удалены существующие коды)

**Указание ревизии в логах:**

- **В каждом файле лога должна указываться ревизия словаря**, используемая при записи логов
- Ревизия указывается в заголовке файла лога или в метаданных
- При чтении логов используется ревизия из файла для правильной расшифровки кодов

**Структура файла лога:**

```kotlin
data class LogFileHeader(
    val dictionaryRevision: DictionaryRevision,  // Ревизия словаря для этого файла
    val formatVersion: String,                    // Версия формата логов
    val createdAt: Long,                          // Время создания файла
    val deviceId: String?                         // ID устройства (опционально)
)

data class LogFile(
    val header: LogFileHeader,
    val entries: List<LogEntry>
)
```

**Хранение словаря:**

- Словарь хранится на сервере или в документации
- Каждая версия словаря хранится отдельно для совместимости
- При обновлении словаря старые логи остаются читаемыми по соответствующей версии словаря
- При чтении логов используется ревизия из файла для выбора правильной версии словаря

## Инциденты с ошибками

### Определение инцидентов

- **Важно:** Отдельно сохраняются инциденты с ошибками (кроме ошибок соединения)
- Инциденты включают:
  - Серверные ошибки (HTTP 500, 503, 429)
  - Ошибки валидации (HTTP 400)
  - Ошибки авторизации (HTTP 401, 403)
  - Конфликты (HTTP 409)
  - Критические системные ошибки
- Ошибки соединения (сетевые ошибки) **не сохраняются** как инциденты

### Структура инцидента

```kotlin
data class ErrorIncident(
    val id: String,                    // Уникальный идентификатор инцидента
    val timestamp: Long,                // Время возникновения ошибки
    val errorType: SyncErrorType,       // Тип ошибки
    val httpCode: Int?,                 // HTTP-код (если применимо)
    val messageCode: String,             // Код сообщения из словаря
    val context: Map<String, Any>,       // Дополнительный контекст
    val taskId: Int?,                   // ID задачи (если применимо)
    val retryAttempt: Int,              // Количество попыток retry
    val sentToServer: Boolean = false   // Флаг отправки на сервер
)

enum class SyncErrorType {
    NETWORK_ERROR,      // Сетевые ошибки (не сохраняются как инциденты)
    CONFLICT,  // Конфликты (HTTP 409)
    SERVER_ERROR,       // Серверные ошибки (500, 503, 429)
    VALIDATION_ERROR,   // Ошибки валидации (400)
    AUTH_ERROR,         // Ошибки авторизации (401, 403)
    UNKNOWN_ERROR       // Неизвестные ошибки
}
```

## Отправка аварийных логов на сервер

### Алгоритм отправки

- При появлении интернет-соединения автоматически отправляются аварийные логи (инциденты)
- Отправка происходит в фоне, не блокируя UI
- После успешной отправки инцидент помечается как отправленный (`sentToServer = true`)
- При неудачной отправке инцидент остаётся для повторной попытки

### Реализация

```kotlin
suspend fun sendErrorIncidentsToServer() {
    if (!syncService.isOnline()) return
    
    val unsentIncidents = incidentRepository.getUnsentIncidents()
    if (unsentIncidents.isEmpty()) return
    
    try {
        tasksApi.sendErrorIncidents(unsentIncidents)
        incidentRepository.markAsSent(unsentIncidents.map { it.id })
        Log.i(TAG, LogMessageCode.INCIDENTS_SENT.code, mapOf(
            "count" to unsentIncidents.size
        ))
    } catch (e: Exception) {
        Log.e(TAG, LogMessageCode.INCIDENTS_SEND_FAIL.code, mapOf(
            "error" to e.message,
            "count" to unsentIncidents.size
        ))
        // Инциденты остаются для повторной попытки
    }
}
```

## Политика удаления логов

### Обычные логи

- Удаляются через **7 дней** после создания
- Автоматическая очистка при старте приложения или периодически
- Очистка не затрагивает инциденты

### Аварийные логи (инциденты)

- Удаляются через **20 дней** после создания
- Удаляются только после успешной отправки на сервер
- Если отправка не удалась, инцидент сохраняется до успешной отправки или истечения 20 дней

### Реализация очистки

```kotlin
suspend fun cleanupOldLogs() {
    val now = System.currentTimeMillis()
    val sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000L)
    val twentyDaysAgo = now - (20 * 24 * 60 * 60 * 1000L)
    
    // Удаление обычных логов старше 7 дней
    logRepository.deleteLogsOlderThan(sevenDaysAgo)
    
    // Удаление отправленных инцидентов старше 20 дней
    incidentRepository.deleteSentIncidentsOlderThan(twentyDaysAgo)
    
    Log.i(TAG, LogMessageCode.LOGS_CLEANUP.code, mapOf(
        "timestamp" to now
    ))
}
```

## Примеры использования

### Создание файла лога с ревизией словаря

```kotlin
// Создание файла лога с указанием ревизии словаря
val currentDictionaryRevision = DictionaryRevision(major = 1, minor = 0)

val logFileHeader = LogFileHeader(
    dictionaryRevision = currentDictionaryRevision,
    formatVersion = "1.0",
    createdAt = System.currentTimeMillis(),
    deviceId = getDeviceId()
)

val logFile = LogFile(
    header = logFileHeader,
    entries = mutableListOf()
)

// При записи логов в файл используется ревизия из заголовка
logFile.entries.add(LogEntry(
    timestamp = System.currentTimeMillis(),
    level = LogLevel.INFO,
    tag = "SyncService",
    messageCode = LogMessageCode.SYNC_START.code,
    context = mapOf("queueSize" to 5)
))
```

### Логирование операции синхронизации

```kotlin
// Начало синхронизации
Log.i(TAG, LogMessageCode.SYNC_START.code, mapOf(
    "queueSize" to queueItems.size,
    "connectionStatus" to connectionStatus.name,
    "timestamp" to System.currentTimeMillis()
))

// Успешная синхронизация
Log.i(TAG, LogMessageCode.SYNC_SUCCESS.code, mapOf(
    "syncedCount" to syncedCount,
    "duration" to duration,
    "timestamp" to System.currentTimeMillis()
))

// Ошибка синхронизации
Log.e(TAG, LogMessageCode.SYNC_FAIL_500.code, mapOf(
    "errorType" to SyncErrorType.SERVER_ERROR.name,
    "httpCode" to 500,
    "retryAttempt" to attempt,
    "timestamp" to System.currentTimeMillis()
))
```

### Логирование создания задачи

```kotlin
Log.i(TAG, LogMessageCode.TASK_CREATE.code, mapOf(
    "taskId" to task.id,
    "title" to task.title,
    "timestamp" to System.currentTimeMillis()
))
```

### Логирование инцидента

```kotlin
val incident = ErrorIncident(
    id = UUID.randomUUID().toString(),
    timestamp = System.currentTimeMillis(),
    errorType = SyncErrorType.SERVER_ERROR,
    httpCode = 500,
    messageCode = LogMessageCode.SYNC_FAIL_500.code,
    context = mapOf(
        "queueSize" to queueItems.size,
        "retryAttempt" to attempt
    ),
    taskId = taskId,
    retryAttempt = attempt,
    sentToServer = false
)

incidentRepository.saveIncident(incident)
```

## Версионирование

### Версия формата логов

- Формат логов версионируется для обеспечения совместимости
- При изменении формата увеличивается версия
- Старые логи остаются читаемыми по старой версии формата

### Версия словаря сообщений

- Словарь сообщений версионируется отдельно с использованием мажорной и минорной версий
- **Мажорная версия:** Увеличивается при изменении или удалении существующих кодов
- **Минорная версия:** Увеличивается при добавлении новых кодов (в рамках одной мажорной версии)
- В каждом файле лога указывается ревизия словаря, используемая при записи
- Старые логи расшифровываются по версии словаря, указанной в файле лога
- Все версии словаря хранятся на сервере для обеспечения совместимости

---

**Последнее обновление:** [дата будет добавлена]  
**Версия формата:** 1.0  
**Ревизия словаря сообщений:** 1.0
