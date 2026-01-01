# Реализация системы бинарного логирования

## Обзор

Реализована полная система бинарного логирования согласно спецификации [LOGGING_FORMAT.md](LOGGING_FORMAT.md). Система включает:

- **Компактный бинарный формат** для экономии места (экономия ~97% места)
- **Потоковую отправку чанков** (формат v2) с протоколом ACK/REPIT
- **Словарь сообщений** с версионированием
- **Автоматическую очистку** старых логов
- **Декодирование на сервере** в читаемый формат

## Реализованные компоненты

### Backend (Python)

1. **`backend/debug_log_dictionary.py`**
   - Словарь сообщений с числовыми кодами (версия 1.0)
   - Поддержка шаблонов сообщений, уровней логирования и схем контекста

2. **`backend/binary_chunk_decoder.py`**
   - Декодер бинарных чанков формата v1.1
   - Парсинг заголовка чанка (magic, версия, дата, ревизия словаря, device_id, chunk_id)
   - Декодирование записей логов с относительными timestamp
   - Преобразование в читаемый текст по словарю

3. **`backend/models/debug_log.py`**
   - Обновленная модель DebugLog с поддержкой двух форматов:
     - v1 (JSON): `message_code`, `tag`, `context`
     - v2 (Binary chunks): `text`, `chunk_id`

4. **`backend/routers/debug_logs.py`**
   - Эндпоинт `POST /api/v0.2/debug-logs` для JSON формата (v1)
   - Эндпоинт `POST /api/v0.2/debug-logs/chunks` для бинарных чанков (v2)
   - Протокол ACK/REPIT для обработки ошибок
   - Эндпоинт `GET /api/v0.2/debug-logs` с фильтрацией по `text` (v2)
   - Эндпоинт `GET /api/v0.2/debug-logs/devices` для списка устройств
   - Эндпоинт `DELETE /api/v0.2/debug-logs/cleanup` для очистки старых логов

5. **`backend/schemas/debug_log.py`**
   - Обновленные Pydantic схемы с поддержкой обоих форматов

6. **`alembic/versions/20250115_add_v2_format_fields.py`**
   - Миграция для добавления полей `text` и `chunk_id`

### Android (Kotlin)

1. **`android/app/src/main/java/com/homeplanner/debug/LogMessageCode.kt`**
   - Все коды сообщений (1-102), синхронизированные со словарем на сервере

2. **`android/app/src/main/java/com/homeplanner/debug/BinaryLogEncoder.kt`**
   - Кодировщик записей в компактный бинарный формат
   - Поддержка всех типов данных (Int, Long, Float, Double, Boolean, String)
   - **Запись полей контекста в порядке схемы** (`ContextSchema.getSchemaOrder()`)
   - **Проверка обязательности всех полей** - выбрасывает `IllegalArgumentException`, если поле отсутствует

3. **`android/app/src/main/java/com/homeplanner/debug/ContextSchema.kt`**
   - Определяет порядок полей контекста для каждого кода сообщения
   - Должен быть синхронизирован с `backend/debug_log_dictionary.py`
   - Используется `BinaryLogEncoder` для записи полей в правильном порядке
   - Гарантирует соответствие порядка записи и декодирования

4. **`android/app/src/main/java/com/homeplanner/debug/BinaryLogStorage.kt`**
   - Обновлено для формата v1.1 с `chunkId`
   - Хранение логов по дням (чанкам)
   - Генерация уникальных `chunkId` для каждого чанка
   - Методы для получения завершенных чанков

4. **`android/app/src/main/java/com/homeplanner/debug/ChunkSender.kt`**
   - Периодическая отправка бинарных чанков (каждые 15 секунд)
   - Протокол ACK/REPIT для обработки ошибок
   - Удаление отправленных чанков
   - Retry для неуспешных отправок

6. **`android/app/src/main/java/com/homeplanner/debug/LogCleanupManager.kt`**
   - Автоматическая очистка логов старше 7 дней
   - Периодическая проверка (каждые 24 часа)
   - Запуск при старте приложения

6. **`android/app/src/main/java/com/homeplanner/debug/BinaryLogger.kt`**
   - Обновлен для работы с `ChunkSender`
   - Метод `getStorage()` для доступа к `BinaryLogStorage`

8. **`android/app/src/main/java/com/homeplanner/MainActivity.kt`**
   - Инициализация всех компонентов логирования:
     - `BinaryLogger` - основной логгер
     - `ChunkSender` - отправка бинарных чанков (v2)
     - `LogCleanupManager` - автоматическая очистка

> Историческая заметка: ранее в Android‑клиенте существовал JSON‑отправщик логов (`LogSender`, формат v1, `POST /api/v0.2/debug-logs`), но он удалён; текущая реализация использует только поток бинарных чанков (`/debug-logs/chunks`).

### Тесты

1. **`tests/test_debug_logs_router.py`**
   - Тесты декодера бинарных чанков
   - Тесты приема бинарных чанков через API
   - Тесты протокола ACK/REPIT
   - Тесты фильтрации по тексту (v2)

## Формат бинарного чанка (v1.1)

### Заголовок чанка

```
Смещение  Размер  Тип            Описание
0-3       4       ASCII          Magic "HDBG"
4         1       uint8          Format version major (1)
5         1       uint8          Format version minor (1)
6-7       2       uint16 LE      Year
8         1       uint8          Month (1-12)
9         1       uint8          Day (1-31)
10        1       uint8          Dictionary revision major
11        1       uint8          Dictionary revision minor
12        1       uint8          Device ID length (0-255)
13+       N       UTF-8          Device ID
13+N      8       uint64 LE      Chunk ID
```

### Запись лога

```
Смещение  Размер  Тип            Описание
0-1       2       uint16 LE      Message code
2-4       3       uint24 LE      Timestamp (10ms intervals from day start)
5+        ...     ...            Context data (depends on message code)
```

## API Endpoints

### JSON формат (v1)

```
POST /api/v0.2/debug-logs
Content-Type: application/json

{
  "logs": [
    {
      "timestamp": "2025-01-15T10:30:00Z",
      "level": "INFO",
      "tag": "SyncService",
      "message_code": "SYNC_START",
      "context": {"queueSize": 5},
      "device_id": "android_abc123",
      "device_info": "Samsung Galaxy (Android 12)",
      "app_version": "1.0.0 (1)",
      "dictionary_revision": "1.0"
    }
  ]
}
```

### Бинарные чанки (v2)

```
POST /api/v0.2/debug-logs/chunks
Content-Type: application/octet-stream
X-Chunk-Id: 123456789
X-Device-Id: android_abc123

<binary data>

Response:
{"result": "ACK", "chunk_id": "123456789"}
или
{"result": "REPIT", "chunk_id": "123456789"}
или
{"result": "ACK", "chunk_id": "123456789", "error": "UNRECOVERABLE_CHUNK"}
```

### Получение логов

```
GET /api/v0.2/debug-logs?device_id={device_id}&level={level}&query={query}&limit={limit}&hours={hours}

Response:
{
  "logs": [
    {
      "id": 1,
      "timestamp": "2025-01-15T10:30:00Z",
      "level": "INFO",
      "text": "Синхронизация начата",
      "device_id": "android_abc123",
      "chunk_id": "123456789"
    }
  ]
}
```

## Использование

### Android

```kotlin
// Инициализация (выполняется автоматически в MainActivity)
BinaryLogger.initialize(context)
ChunkSender.start(context, networkConfig, storage)
LogCleanupManager.start(context)

// Логирование
val logger = BinaryLogger.getInstance()

// ВАЖНО: Все поля контекста, определенные в ContextSchema для данного кода сообщения, обязательны
// ВАЖНО: Порядок ключей в Map должен соответствовать порядку полей в схеме
logger?.log(
    level = LogLevel.INFO,
    tag = "SyncService",
    messageCode = LogMessageCode.SYNC_START,
    context = mapOf(
        "cache_size" to 5  // Обязательное поле для SYNC_START согласно схеме
    )
)

// Пример с несколькими полями (SYNC_CACHE_UPDATED)
// Порядок ключей соответствует схеме: tasks_count, source, queue_items
logger?.log(
    level = LogLevel.INFO,
    tag = "SyncService",
    messageCode = LogMessageCode.SYNC_CACHE_UPDATED,
    context = mapOf(
        "tasks_count" to serverTasks.size,    // 1-е поле схемы
        "source" to "syncCacheWithServer",     // 2-е поле схемы
        "queue_items" to queueSize             // 3-е поле схемы
    )
)
```

### Backend

```python
# Декодирование бинарного чанка
from backend.binary_chunk_decoder import BinaryChunkDecoder

decoder = BinaryChunkDecoder()
header, entries = decoder.decode_chunk(binary_data)

for entry in entries:
    print(f"{entry.timestamp} [{entry.level}] {entry.text}")
```

## Преимущества

1. **Экономия места**: ~97% экономии по сравнению с Java/Kotlin объектами
2. **Быстрая передача**: компактный бинарный формат
3. **Автоматическая очистка**: логи автоматически удаляются через 7 дней
4. **Надежность**: протокол ACK/REPIT предотвращает бесконечные retry
5. **Читаемость**: логи декодируются на сервере в читаемый текст
6. **Версионирование**: поддержка разных версий словаря и формата

## Ограничения

- Работает только в debug-версиях приложения (`BuildConfig.DEBUG`)
- Максимальный размер чанка: не ограничен (но рекомендуется держать в пределах 1-10 КБ)
- Логи хранятся до 7 дней на клиенте
- Логи хранятся на сервере до очистки (по умолчанию 7 дней)

## Дальнейшие улучшения

- [ ] Добавить сжатие бинарных чанков (gzip)
- [ ] Реализовать инциденты с ошибками (ErrorIncident)
- [ ] Добавить метрики производительности логирования
- [ ] Реализовать фоновую отправку чанков через WorkManager
- [ ] Добавить UI для просмотра логов в приложении


