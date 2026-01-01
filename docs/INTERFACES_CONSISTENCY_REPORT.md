# Отчет о согласованности интерфейсов и разделения обязанностей

**Дата анализа:** 2025-01-XX  
**Аналитик:** AI Assistant

## Резюме

Проведен анализ согласованности описания интерфейсов и разделения обязанностей между:
- Документацией (OFFLINE_FIRST_ARCHITECTURE.md, OFFLINE_CLIENT_DESIGN.md)
- Фактической реализацией в коде (Android приложение)

**Критическое несоответствие:** Документация описывает архитектуру с `LocalApi`, но в коде используется `TasksApiOffline`, который объединяет обязанности нескольких компонентов. ✅ Миграция завершена, `TasksApiOffline` удалён, используется `LocalApi`.

## 1. TasksApi

### Описание в документации

**OFFLINE_FIRST_ARCHITECTURE.md:**
- **Назначение:** Выполнение HTTP-запросов к REST API сервера
- **Ответственность:**
  - Выполнение GET/POST/PUT/DELETE запросов к серверу
  - Парсинг JSON-ответов
  - Обработка HTTP-ошибок
  - Передача cookies для выбранного пользователя
- **Особенности:**
  - Не содержит логики кэширования
  - Не зависит от наличия интернета (выбрасывает исключения при отсутствии)
  - Используется только `SyncService` для синхронизации

**Методы:**
- `getTasks(activeOnly: Boolean = true): List<Task>`
- `createTask(task: Task, assignedUserIds: List<Int> = emptyList()): Task`
- `updateTask(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Task`
- `completeTask(taskId: Int): Task`
- `uncompleteTask(taskId: Int): Task`
- `deleteTask(taskId: Int)`
- `syncQueue(queueItems: List<SyncQueueItem>): List<Task>`

### Фактическая реализация

**Код (TasksApi.kt):**
- ✅ Соответствует описанию: выполняет HTTP-запросы
- ✅ Соответствует: парсинг JSON-ответов
- ✅ Соответствует: обработка HTTP-ошибок
- ✅ Соответствует: передача cookies через `applyUserCookie()`
- ✅ Соответствует: не содержит логики кэширования
- ✅ Соответствует: выбрасывает исключения при ошибках сети
- ✅ Соответствует: все методы из документации реализованы
- ✅ Дополнительно: `getTodayTaskIds(): List<Int>` - не упомянут в документации, но используется

**Статус:** ✅ Полное соответствие

---

## 2. TasksApiOffline

### Описание в документации

**OFFLINE_FIRST_ARCHITECTURE.md:**
- Упоминается в разделе "Миграция с TasksApiOffline на LocalApi"
- Описывается как обёртка над `TasksApi` с логикой кэширования и синхронизации
- Статус: ⚠️ Используется в `MainActivity`, требует миграции

**OFFLINE_CLIENT_DESIGN.md:**
- **TasksApiOffline**
  - Прослойка над обычным API задач
  - При наличии сети использует онлайн‑API и обновляет кэш
  - При отсутствии сети работает только с `OfflineRepository` и очередью операций

### Фактическая реализация

**Код (TasksApiOffline.kt):**
- ✅ Соответствует: обёртка над `TasksApi`
- ✅ Соответствует: работает с `OfflineRepository` и `SyncService`
- ✅ Соответствует: offline-first стратегия (сначала кэш, затем синхронизация)
- ✅ Соответствует: оптимистичные обновления
- ✅ Соответствует: добавление операций в очередь синхронизации
- ⚠️ **Несоответствие:** Выполняет синхронизацию внутри себя (вызывает `syncService.syncQueue()` в фоне)
  - Документация говорит, что `SyncService` должен использоваться отдельно
  - `TasksApiOffline` сам запускает синхронизацию в корутинах

**Методы:**
- `suspend fun getTasks(activeOnly: Boolean = true): List<Task>` ✅
- `suspend fun getTodayTaskIds(): List<Int>` ✅
- `suspend fun createTask(task: Task, assignedUserIds: List<Int> = emptyList()): Task` ✅
- `suspend fun updateTask(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Task` ✅
- `suspend fun completeTask(taskId: Int): Task` ✅
- `suspend fun uncompleteTask(taskId: Int): Task` ✅
- `suspend fun deleteTask(taskId: Int)` ✅

**Разделение обязанностей:**
- **Фактически:** `TasksApiOffline` объединяет обязанности:
  - Работа с кэшем (как `LocalApi`)
  - Добавление в очередь синхронизации
  - Запуск синхронизации в фоне (частично как `SyncService`)
  - Оптимистичные обновления

**Статус:** ✅ Соответствует описанию в `OFFLINE_CLIENT_DESIGN.md`, но ⚠️ не соответствует разделению обязанностей в `OFFLINE_FIRST_ARCHITECTURE.md`

---

## 3. LocalApi

### Описание в документации

**OFFLINE_FIRST_ARCHITECTURE.md:**
- **Назначение:** Работа с локальным хранилищем для UI
- **Расположение:** `android/app/src/main/java/com/homeplanner/api/LocalApi.kt` (планируется)
- **Ответственность:**
  - Загрузка данных из локального кэша (Room database)
  - Сохранение изменений в локальный кэш
  - Добавление операций в очередь синхронизации
  - Оптимистичные обновления для мгновенного отклика UI
- **Особенности:**
  - Все методы работают только с локальным хранилищем
  - Не выполняет запросы к серверу
  - Все изменения автоматически добавляются в очередь синхронизации

**Методы (планируемые):**
- `suspend fun getTasks(activeOnly: Boolean = true): List<Task>`
- `suspend fun createTask(task: Task, assignedUserIds: List<Int> = emptyList()): Task`
- `suspend fun updateTask(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Task`
- `suspend fun completeTask(taskId: Int): Task`
- `suspend fun uncompleteTask(taskId: Int): Task`
- `suspend fun deleteTask(taskId: Int)`

### Фактическая реализация

**Код:**
- ❌ **НЕ РЕАЛИЗОВАН** - класс `LocalApi` отсутствует в коде
- Вместо него используется `TasksApiOffline`

**Статус:** ⚠️ Несоответствие - документирован как планируемый, но миграция не выполнена

---

## 4. SyncService

### Описание в документации

**OFFLINE_FIRST_ARCHITECTURE.md:**
- **Назначение:** Синхронизация данных между локальным хранилищем и сервером
- **Ответственность:**
  - Синхронизация очереди операций с сервером
  - Загрузка актуальных данных с сервера и сохранение в кэш
  - Обработка конфликтов
  - Проверка наличия интернет-соединения
- **Особенности:**
  - Использует `TasksApi` для запросов к серверу
  - Работает только при наличии интернета
  - Обрабатывает ошибки и конфликты

**Методы:**
- `fun isOnline(): Boolean`
- `suspend fun syncStateBeforeRecalculation(): Boolean`
- `suspend fun syncQueue(): Result<SyncResult>`

**OFFLINE_CLIENT_DESIGN.md:**
- **SyncService**
  - Обрабатывает очередь синхронизации
  - Отправляет операции на сервер и получает актуальное состояние задач
  - Применяет полученное состояние к кэшу через `OfflineRepository`
  - Не реализует логику пересчёта по новому дню

### Фактическая реализация

**Код (SyncService.kt):**
- ✅ Соответствует: `isOnline(): Boolean` - проверка наличия интернета
- ✅ Соответствует: `syncStateBeforeRecalculation(): Boolean` - синхронизация перед пересчётом
- ✅ Соответствует: `syncQueue(): Result<SyncResult>` - синхронизация очереди
- ✅ Соответствует: использует `TasksApi` для запросов
- ✅ Соответствует: работает только при наличии интернета
- ✅ Соответствует: обрабатывает ошибки (возвращает `Result`)
- ✅ Соответствует: не реализует логику пересчёта (это в `OfflineRepository`)

**Дополнительно:**
- ✅ Реализован `data class SyncResult` для результата синхронизации

**Статус:** ✅ Полное соответствие

---

## 5. OfflineRepository

### Описание в документации

**OFFLINE_FIRST_ARCHITECTURE.md:**
- **Назначение:** Низкоуровневая работа с Room database и очередью синхронизации
- **Ответственность:**
  - Сохранение/загрузка задач в/из Room database
  - Управление очередью синхронизации (SyncQueue)
  - Очистка старых данных
  - Управление метаданными хранилища

**Основные методы:**
- `suspend fun saveTasksToCache(tasks: List<Task>): Result<Unit>`
- `suspend fun loadTasksFromCache(): List<Task>`
- `suspend fun getTaskFromCache(id: Int): Task?`
- `suspend fun deleteTaskFromCache(id: Int)`
- `suspend fun addToSyncQueue(action: String, entityType: String, entityId: Int?, entity: Task?)`
- `suspend fun getPendingQueueItems(): List<SyncQueueItem>`
- `suspend fun clearAllQueue()`
- `suspend fun updateRecurringTasksForNewDay(dayStartHour: Int): Boolean`

**OFFLINE_CLIENT_DESIGN.md:**
- **OfflineRepository**
  - Локальное хранилище (Room/SQLite, SharedPreferences и т.п.)
  - CRUD по задачам, чтение/запись `last_update` и связанных метаданных
  - Работа с очередью операций (добавление/чтение/удаление)
  - Не содержит логики UI и не управляет уведомлениями

### Фактическая реализация

**Код (OfflineRepository.kt):**
- ✅ Соответствует: работа с Room database
- ✅ Соответствует: работа с очередью синхронизации
- ✅ Соответствует: управление метаданными хранилища
- ✅ Соответствует: все основные методы реализованы
- ✅ Соответствует: не содержит логики UI
- ✅ Соответствует: не управляет уведомлениями
- ✅ Дополнительно: методы для работы с `last_update` и `dayStartHour`
- ✅ Дополнительно: методы для расчета размера хранилища
- ✅ Дополнительно: методы для очистки кэша и очереди

**Методы (реализованные):**
- `saveTasksToCache(tasks: List<Task>): Result<Unit>` ✅
- `loadTasksFromCache(): List<Task>` ✅
- `getTaskFromCache(id: Int): Task?` ✅
- `deleteTaskFromCache(id: Int)` ✅
- `addToSyncQueue(...)` ✅ (сигнатура немного отличается: `payload: Any?`, `revision: Int?`)
- `getPendingQueueItems(): List<SyncQueueItem>` ✅
- `clearAllQueue()` ✅
- `updateRecurringTasksForNewDay(dayStartHour: Int): Boolean` ✅

**Дополнительные методы (не упомянуты в документации):**
- `getPendingOperationsCount(): Int`
- `getStoragePercentage(): Float`
- `getQueueSizeBytes(): Long`
- `getCacheSizeBytes(): Long`
- `getCachedTasksCount(): Int`
- `getLastUpdateTimestamp(): Long?`
- `getLastDayStartHour(): Int?`
- `setLastUpdateTimestamp(timestamp: Long, dayStartHour: Int)`
- `clearAllCache()`

**Статус:** ✅ Соответствует, но содержит дополнительные методы, не описанные в документации

---

## 6. TodayTaskFilter

### Описание в документации

**OFFLINE_CLIENT_DESIGN.md:**
- **TodayTaskFilter**
  - Локальная фильтрация задач для вкладки «Сегодня»
  - Метод `filterTodayTasks(tasks, selectedUser, dayStartHour)`
  - Логика строго следует «Каноническим правилам фильтрации задач» (`OFFLINE_REQUIREMENTS.md`)

### Фактическая реализация

**Код (TodayTaskFilter.kt):**
- ✅ Соответствует: объект `TodayTaskFilter` (singleton)
- ✅ Соответствует: метод `filterTodayTasks(tasks, selectedUser, dayStartHour)` реализован
- ✅ Соответствует: логика фильтрации соответствует каноническим правилам
- ✅ Соответствует: поддержка `one_time`, `recurring`, `interval`
- ✅ Дополнительно: приватный метод `getDayStart()` для определения начала дня
- ✅ Дополнительно: приватный метод `parseReminderTime()` для парсинга времени

**Статус:** ✅ Полное соответствие

---

## 7. TaskDateCalculator

### Описание в документации

**OFFLINE_CLIENT_DESIGN.md:**
- **TaskDateCalculator**
  - Реализует логику `_calculate_next_due_date` с бэкенда для всех типов повторений
  - Предоставляет `getDayStart` и `isNewDay` с учётом `day_start_hour`

### Фактическая реализация

**Код (TaskDateCalculator.kt):**
- ✅ Соответствует: объект `TaskDateCalculator` (singleton)
- ✅ Соответствует: метод `getDayStart(time, dayStartHour)` реализован
- ✅ Соответствует: метод `isNewDay(...)` реализован
- ✅ Соответствует: метод `calculateNextReminderTime(...)` реализован
- ✅ Соответствует: поддержка типов повторений (DAILY, WEEKLY, MONTHLY, YEARLY)
- ✅ Соответствует: поддержка `interval` задач
- ✅ Соответствует: `one_time` задачи не пересчитываются

**Методы:**
- `getDayStart(time: LocalDateTime, dayStartHour: Int): LocalDateTime` ✅
- `isNewDay(lastUpdateMillis: Long?, nowMillis: Long, lastDayStartHour: Int?, currentDayStartHour: Int): Boolean` ✅
- `calculateNextReminderTime(task: Task, nowMillis: Long, dayStartHour: Int): String` ✅

**Статус:** ✅ Полное соответствие

---

## 8. ReminderScheduler

### Описание в документации

**OFFLINE_CLIENT_DESIGN.md:**
- **ReminderScheduler**
  - Планирование и отмена локальных уведомлений
  - Работает только с уже пересчитанными данными из кэша, сам пересчёт дат не выполняет

### Фактическая реализация

**Код (ReminderScheduler.kt):**
- ✅ Соответствует: класс `ReminderScheduler` реализован
- ✅ Соответствует: планирование уведомлений через `AlarmManager`
- ⚠️ Частичное несоответствие: **использует `TaskDateCalculator` для пересчёта дат**, если время в прошлом
  - Документация: "сам пересчёт дат не выполняет"
  - Код: вызывает `TaskDateCalculator.calculateNextReminderTime()` при времени в прошлом
- ✅ Соответствует: отмена уведомлений через `cancelForTask()` и `cancelAll()`
- ✅ Соответствует: планирование только для активных и незавершённых задач

**Методы:**
- `scheduleForTasks(tasks: List<Task>)` ✅
- `scheduleForTaskIfUpcoming(task: Task)` ✅
- `cancelForTask(task: Task)` ✅
- `cancelAll(tasks: List<Task>)` ✅

**Статус:** ⚠️ Частичное несоответствие - использует пересчёт дат, хотя документация говорит, что не должен

---

## 9. Несоответствия между документами

### 9.1. Описание TasksApiOffline

**OFFLINE_FIRST_ARCHITECTURE.md:**
- Описывается как компонент, требующий миграции
- Упоминается в контексте миграции на `LocalApi`

**OFFLINE_CLIENT_DESIGN.md:**
- Описывается как актуальный компонент архитектуры
- Не упоминается как устаревший

**Проблема:** Разные статусы в разных документах

**Рекомендация:** Уточнить статус `TasksApiOffline` - является ли он временным решением или постоянным компонентом

### 9.2. Описание LocalApi

**OFFLINE_FIRST_ARCHITECTURE.md:**
- Описывается как планируемый компонент
- Указывается расположение: `(планируется)`
- Описывается в разделе "Миграция с TasksApiOffline на LocalApi"

**OFFLINE_CLIENT_DESIGN.md:**
- Не упоминается `LocalApi`
- Описывается только `TasksApiOffline`

**Проблема:** `LocalApi` упоминается только в одном документе

**Рекомендация:** Либо реализовать `LocalApi`, либо удалить упоминания из документации

### 9.3. Разделение обязанностей

**OFFLINE_FIRST_ARCHITECTURE.md:**
- Чёткое разделение: `LocalApi` (UI) → `OfflineRepository` (хранилище), `SyncService` (синхронизация) → `TasksApi` (HTTP)
- Описывает потоки данных через `LocalApi`

**OFFLINE_CLIENT_DESIGN.md:**
- Описывается `TasksApiOffline` как прослойка над API
- Не упоминается разделение на `LocalApi` + `SyncService`

**Проблема:** Разные архитектурные видения в документах

**Рекомендация:** Привести документацию к единому видению архитектуры

---

## 10. Несоответствия между документацией и кодом

### 10.1. LocalApi не реализован

**Документация:** Описывает `LocalApi` как планируемый компонент  
**Код:** `LocalApi` отсутствует, используется `TasksApiOffline`

**Проблема:** Документация описывает несуществующий компонент

**Рекомендация:** 
- Либо реализовать `LocalApi` согласно плану миграции
- Либо обновить документацию, убрав упоминания `LocalApi` и описав текущую архитектуру с `TasksApiOffline`

### 10.2. TasksApiOffline - статус неясен

**Документация:** 
- `OFFLINE_FIRST_ARCHITECTURE.md`: описывается как требующий миграции
- `OFFLINE_CLIENT_DESIGN.md`: описывается как актуальный компонент

**Код:** Активно используется в `MainActivity`

**Проблема:** Неясно, является ли `TasksApiOffline` временным или постоянным решением

**Рекомендация:** Уточнить статус и обновить документацию

### 10.3. Дополнительные методы OfflineRepository

**Документация:** Описывает основные методы  
**Код:** Содержит дополнительные методы для работы с метаданными

**Проблема:** Документация неполная

**Рекомендация:** Обновить документацию, добавив описание всех методов

### 10.4. Потоки данных в документации не соответствуют коду

**Документация (OFFLINE_FIRST_ARCHITECTURE.md):**
- Описывает потоки данных через `LocalApi`:
  - `UI → LocalApi.getTasks()`
  - `UI → LocalApi.createTask(task)`
  - `UI → LocalApi.updateTask(id, task)`

**Код (MainActivity.kt):**
- Фактически используется `TasksApiOffline`:
  - `tasksApiOffline.completeTask(task.id)`
  - `tasksApiOffline.uncompleteTask(task.id)`
  - `tasksApiOffline.updateTask(base.id, finalPayload, editAssignedUserIds)`
  - `tasksApiOffline.deleteTask(toDelete)`

**Проблема:** Документация описывает несуществующий компонент `LocalApi` в потоках данных

**Рекомендация:** Обновить потоки данных в `OFFLINE_FIRST_ARCHITECTURE.md`, заменив `LocalApi` на `TasksApiOffline`

### 10.5. Примеры кода в документации не соответствуют реализации

**Документация (OFFLINE_FIRST_ARCHITECTURE.md):**
```kotlin
// Пример создания задачи
val newTask = localApi.createTask(task, assignedUserIds)
```

**Код (MainActivity.kt):**
- Используется `tasksApiOffline.updateTask()`, `tasksApiOffline.completeTask()` и т.д.
- `TasksApiOffline` используется напрямую в UI, без промежуточного слоя `LocalApi`

**Проблема:** Примеры кода описывают несуществующий API

**Рекомендация:** Обновить примеры кода, заменив `localApi` на `tasksApiOffline`

### 10.6. Разделение обязанностей не соответствует документации

**Документация (OFFLINE_FIRST_ARCHITECTURE.md):**
- Описывает разделение: `LocalApi` (UI) → `OfflineRepository` (хранилище), `SyncService` (синхронизация) → `TasksApi` (HTTP)
- `LocalApi` не выполняет запросы к серверу
- `SyncService` выполняет синхронизацию отдельно

**Код:**
- `TasksApiOffline` объединяет обязанности:
  - Работа с кэшем (как `LocalApi`)
  - Добавление в очередь синхронизации
  - **Запуск синхронизации в фоне** (вызывает `syncService.syncQueue()` внутри себя)
  - Оптимистичные обновления

**Проблема:** `TasksApiOffline` выполняет синхронизацию внутри себя, что не соответствует описанию разделения обязанностей

**Рекомендация:** 
- Либо обновить документацию, описав фактическое разделение обязанностей с `TasksApiOffline`
- Либо изменить код, убрав синхронизацию из `TasksApiOffline` (оставить только работу с кэшем и очередью)

---

## 11. Рекомендации

### Высокий приоритет

1. **Уточнить статус миграции LocalApi**
   - Если миграция отменена → обновить `OFFLINE_FIRST_ARCHITECTURE.md`, убрав упоминания `LocalApi`
   - Если миграция запланирована → обновить статус в документации и начать реализацию

2. **Привести документацию к единому видению**
   - Синхронизировать описание архитектуры между `OFFLINE_FIRST_ARCHITECTURE.md` и `OFFLINE_CLIENT_DESIGN.md`
   - Уточнить статус `TasksApiOffline` (временный или постоянный)
   - **КРИТИЧНО:** Обновить потоки данных в `OFFLINE_FIRST_ARCHITECTURE.md` - использовать `TasksApiOffline` вместо `LocalApi`
   - **КРИТИЧНО:** Обновить примеры кода в `OFFLINE_FIRST_ARCHITECTURE.md` - использовать `tasksApiOffline` вместо `localApi`
   - Обновить раздел "Разделение ответственности" - описать фактическое разделение с `TasksApiOffline`

3. **Обновить документацию OfflineRepository**
   - Добавить описание всех методов, включая работу с метаданными
   - Описать методы для работы с `last_update` и `dayStartHour`
   - Добавить описание методов: `getPendingOperationsCount()`, `getStoragePercentage()`, `getQueueSizeBytes()`, `getCacheSizeBytes()`, `getCachedTasksCount()`, `getLastUpdateTimestamp()`, `getLastDayStartHour()`, `setLastUpdateTimestamp()`, `clearAllCache()`

4. **Уточнить поведение ReminderScheduler**
   - Обновить документацию: `ReminderScheduler` может использовать `TaskDateCalculator` для пересчёта дат в прошлом
   - Или изменить код: убрать пересчёт дат из `ReminderScheduler` (оставить только планирование)

5. **Уточнить разделение обязанностей TasksApiOffline**
   - Обновить документацию: описать, что `TasksApiOffline` выполняет синхронизацию в фоне
   - Или изменить код: убрать синхронизацию из `TasksApiOffline`, оставить только работу с кэшем и очередью

### Средний приоритет

6. **Дополнить документацию методами TasksApi**
   - Добавить описание `getTodayTaskIds()` в документацию `OFFLINE_FIRST_ARCHITECTURE.md`

7. **Обновить потоки данных в документации**
   - Заменить `LocalApi` на `TasksApiOffline` в диаграммах потоков данных в `OFFLINE_FIRST_ARCHITECTURE.md`
   - Обновить примеры использования

### Низкий приоритет

8. **Создать диаграммы взаимодействия**
   - Визуализировать потоки данных между компонентами
   - Показать разделение обязанностей

---

## 12. Выводы

1. **TasksApi** - ✅ Полное соответствие документации и кода
2. **SyncService** - ✅ Полное соответствие документации и кода
3. **OfflineRepository** - ✅ Соответствует, но документация неполная (не описаны методы работы с метаданными)
4. **TasksApiOffline** - ✅ Соответствует коду, но статус неясен в документации (временный или постоянный)
5. **LocalApi** - ❌ Описан в документации, но не реализован в коде
6. **TodayTaskFilter** - ✅ Полное соответствие документации и кода
7. **TaskDateCalculator** - ✅ Полное соответствие документации и кода
8. **ReminderScheduler** - ⚠️ Частичное несоответствие (использует пересчёт дат, хотя документация говорит, что не должен)

**Основная проблема:** Несоответствие между описанием целевой архитектуры (с `LocalApi`) и фактической реализацией (с `TasksApiOffline`). Требуется либо реализация миграции, либо обновление документации.

**Критические несоответствия:**
1. **Потоки данных описывают `LocalApi`, но код использует `TasksApiOffline`**
   - Все диаграммы потоков данных в `OFFLINE_FIRST_ARCHITECTURE.md` используют `LocalApi`
   - Фактически в коде используется `TasksApiOffline`
   - Примеры кода используют `localApi`, но такого класса нет

2. **Разделение обязанностей не соответствует документации**
   - Документация: `LocalApi` (UI) → `OfflineRepository` (хранилище), `SyncService` (синхронизация) → `TasksApi` (HTTP)
   - Код: `TasksApiOffline` (UI + кэш + синхронизация) → `OfflineRepository` (хранилище), `SyncService` (синхронизация) → `TasksApi` (HTTP)
   - `TasksApiOffline` объединяет обязанности `LocalApi` и частично `SyncService`

3. **TasksApiOffline выполняет синхронизацию внутри себя**
   - Документация говорит, что `SyncService` должен использоваться отдельно
   - Код: `TasksApiOffline` сам вызывает `syncService.syncQueue()` в фоне

**Дополнительные проблемы:**
- `ReminderScheduler` использует пересчёт дат через `TaskDateCalculator`, хотя документация говорит, что не должен
- Документация `OfflineRepository` неполная (не описаны методы работы с метаданными)

---

## 13. План действий

### Этап 1: Уточнение статуса миграции (КРИТИЧНО)
- [ ] Решить: выполняется ли миграция на `LocalApi` или отменена
- [ ] Если отменена → обновить `OFFLINE_FIRST_ARCHITECTURE.md`, убрав упоминания `LocalApi`
- [ ] Если запланирована → обновить статус и начать реализацию

### Этап 2: Обновление документации под фактическую реализацию (КРИТИЧНО)
- [ ] Обновить потоки данных в `OFFLINE_FIRST_ARCHITECTURE.md` - заменить `LocalApi` на `TasksApiOffline`
- [ ] Обновить примеры кода - заменить `localApi` на `tasksApiOffline`
- [ ] Обновить раздел "Разделение ответственности" - описать фактическое разделение с `TasksApiOffline`
- [ ] Описать, что `TasksApiOffline` выполняет синхронизацию в фоне

### Этап 3: Синхронизация документов
- [ ] Привести `OFFLINE_FIRST_ARCHITECTURE.md` и `OFFLINE_CLIENT_DESIGN.md` к единому видению
- [ ] Уточнить статус `TasksApiOffline` во всех документах

### Этап 4: Дополнение документации
- [ ] Добавить описание всех методов `OfflineRepository`
- [ ] Добавить описание `getTodayTaskIds()` в `TasksApi`
- [ ] Уточнить поведение `ReminderScheduler` (пересчёт дат)
