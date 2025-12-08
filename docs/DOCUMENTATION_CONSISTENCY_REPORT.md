# Отчет о согласованности документации проекта HomePlanner

**Дата анализа:** 2025-01-XX  
**Аналитик:** AI Assistant  
**Область анализа:** Только документация (без сравнения с кодом)

## Резюме

Проведен анализ согласованности описания интерфейсов, разделения обязанностей и архитектуры между различными документами проекта:
- `OFFLINE_FIRST_ARCHITECTURE.md` - основная архитектурная документация
- `OFFLINE_CLIENT_DESIGN.md` - высокоуровневая архитектура клиента
- `MIGRATION_TO_LOCAL_API.md` - план миграции

**Критическое несоответствие:** Документация описывает две разные архитектуры:
1. **Целевая архитектура** (OFFLINE_FIRST_ARCHITECTURE.md): `LocalApi` + `SyncService` + `TasksApi`
2. **Текущая архитектура** (OFFLINE_CLIENT_DESIGN.md): `TasksApiOffline` + `SyncService` + `TasksApi`

---

## 1. Согласованность описания компонентов

### 1.1. TasksApi

**OFFLINE_FIRST_ARCHITECTURE.md:**
- **Назначение:** Выполнение HTTP-запросов к REST API сервера
- **Ответственность:**
  - Выполнение GET/POST/PUT/DELETE запросов к серверу
  - Парсинг JSON-ответов
  - Обработка HTTP-ошибок
  - Передача cookies для выбранного пользователя
- **Методы:**
  - `getTasks(activeOnly: Boolean = true): List<Task>`
  - `createTask(task: Task, assignedUserIds: List<Int> = emptyList()): Task`
  - `updateTask(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Task`
  - `completeTask(taskId: Int): Task`
  - `uncompleteTask(taskId: Int): Task`
  - `deleteTask(taskId: Int)`
  - `syncQueue(queueItems: List<SyncQueueItem>): List<Task>`
- **Особенности:**
  - Не содержит логики кэширования
  - Не зависит от наличия интернета (выбрасывает исключения при отсутствии)
  - Используется только `SyncService` для синхронизации

**OFFLINE_CLIENT_DESIGN.md:**
- Не описывает `TasksApi` напрямую
- Упоминается только в контексте `TasksApiOffline` как "обычный API задач"

**MIGRATION_TO_LOCAL_API.md:**
- Описывает `TasksApi` как реализованный компонент ✅
- Указывает, что используется только `SyncService` для синхронизации

**Статус:** ✅ Согласованно описано в `OFFLINE_FIRST_ARCHITECTURE.md` и `MIGRATION_TO_LOCAL_API.md`

---

### 1.2. LocalApi

**OFFLINE_FIRST_ARCHITECTURE.md:**
- **Назначение:** Работа с локальным хранилищем для UI
- **Расположение:** `android/app/src/main/java/com/homeplanner/api/LocalApi.kt` (планируется)
- **Ответственность:**
  - Загрузка данных из локального кэша (Room database)
  - Сохранение изменений в локальный кэш
  - Добавление операций в очередь синхронизации
  - Оптимистичные обновления для мгновенного отклика UI
- **Методы:**
  - `suspend fun getTasks(activeOnly: Boolean = true): List<Task>`
  - `suspend fun createTask(task: Task, assignedUserIds: List<Int> = emptyList()): Task`
  - `suspend fun updateTask(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Task`
  - `suspend fun completeTask(taskId: Int): Task`
  - `suspend fun uncompleteTask(taskId: Int): Task`
  - `suspend fun deleteTask(taskId: Int)`
- **Особенности:**
  - Все методы работают только с локальным хранилищем
  - Не выполняет запросы к серверу
  - Все изменения автоматически добавляются в очередь синхронизации
  - Возвращает данные немедленно (оптимистичные обновления)

**OFFLINE_CLIENT_DESIGN.md:**
- ❌ **НЕ УПОМИНАЕТСЯ** - в документе описывается только `TasksApiOffline`

**MIGRATION_TO_LOCAL_API.md:**
- Описывает `LocalApi` как планируемый компонент ❌
- Указывает, что должен быть создан на основе логики из `TasksApiOffline`
- Описывает те же методы и ответственность, что и `OFFLINE_FIRST_ARCHITECTURE.md`

**Статус:** ⚠️ Несогласованность - `OFFLINE_CLIENT_DESIGN.md` не упоминает `LocalApi`, хотя он описан в других документах как целевой компонент

---

### 1.3. TasksApiOffline

**OFFLINE_FIRST_ARCHITECTURE.md:**
- Упоминается только в разделе "Миграция с TasksApiOffline на LocalApi"
- Описывается как компонент, требующий миграции
- Статус: ⚠️ Используется в `MainActivity`, требует миграции

**OFFLINE_CLIENT_DESIGN.md:**
- **TasksApiOffline**
  - Прослойка над обычным API задач
  - При наличии сети использует онлайн‑API и обновляет кэш
  - При отсутствии сети работает только с `OfflineRepository` и очередью операций
- Описывается как **актуальный компонент** архитектуры

**MIGRATION_TO_LOCAL_API.md:**
- Описывает `TasksApiOffline` как текущий компонент ⚠️
- Указывает проблемы текущего подхода:
  - Смешение ответственности (работа с кэшем + синхронизация)
  - Сложность тестирования
  - Дублирование логики синхронизации
- Планируется удаление после миграции

**Статус:** ⚠️ **Критическое несоответствие** - разные статусы в разных документах:
- `OFFLINE_FIRST_ARCHITECTURE.md`: требует миграции
- `OFFLINE_CLIENT_DESIGN.md`: актуальный компонент
- `MIGRATION_TO_LOCAL_API.md`: текущий компонент, планируется удаление

---

### 1.4. SyncService

**OFFLINE_FIRST_ARCHITECTURE.md:**
- **Назначение:** Синхронизация данных между локальным хранилищем и сервером
- **Ответственность:**
  - Синхронизация очереди операций с сервером
  - Загрузка актуальных данных с сервера и сохранение в кэш
  - Обработка конфликтов
  - Проверка наличия интернет-соединения
- **Методы:**
  - `fun isOnline(): Boolean`
  - `suspend fun syncStateBeforeRecalculation(): Boolean`
  - `suspend fun syncQueue(): Result<SyncResult>`
- **Алгоритм синхронизации:**
  1. Проверка наличия интернет-соединения
  2. Получение операций из очереди (`OfflineRepository.getPendingQueueItems()`)
  3. Отправка операций на сервер через `TasksApi.syncQueue()`
  4. Очистка очереди при успешной синхронизации
  5. Обновление кэша данными с сервера
- **Особенности:**
  - Использует `TasksApi` для запросов к серверу
  - Работает только при наличии интернета
  - Обрабатывает ошибки и конфликты
  - Возвращает результат синхронизации

**OFFLINE_CLIENT_DESIGN.md:**
- **SyncService**
  - Обрабатывает очередь синхронизации
  - Отправляет операции на сервер и получает актуальное состояние задач
  - Применяет полученное состояние к кэшу через `OfflineRepository`
  - Не реализует логику пересчёта по новому дню

**MIGRATION_TO_LOCAL_API.md:**
- Описывает `SyncService` как реализованный компонент ✅
- Указывает, что используется только частично
- Планируется полное использование после миграции

**Статус:** ✅ Согласованно описано во всех документах

---

### 1.5. OfflineRepository

**OFFLINE_FIRST_ARCHITECTURE.md:**
- **Назначение:** Низкоуровневая работа с Room database и очередью синхронизации
- **Ответственность:**
  - Сохранение/загрузка задач в/из Room database
  - Управление очередью синхронизации (SyncQueue)
  - Очистка старых данных
  - Управление метаданными хранилища
- **Основные методы:**
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

**MIGRATION_TO_LOCAL_API.md:**
- Описывает `OfflineRepository` как реализованный компонент ✅
- Указывает, что работает корректно

**Статус:** ✅ Согласованно описано во всех документах

---

### 1.6. Вспомогательные компоненты

#### TodayTaskFilter

**OFFLINE_CLIENT_DESIGN.md:**
- Локальная фильтрация задач для вкладки «Сегодня»
- Метод `filterTodayTasks(tasks, selectedUser, dayStartHour)`
- Логика строго следует «Каноническим правилам фильтрации задач» (`OFFLINE_REQUIREMENTS.md`)

**OFFLINE_FIRST_ARCHITECTURE.md:**
- ❌ Не упоминается

**Статус:** ⚠️ Несогласованность - описан только в `OFFLINE_CLIENT_DESIGN.md`

#### TaskDateCalculator

**OFFLINE_CLIENT_DESIGN.md:**
- Реализует логику `_calculate_next_due_date` с бэкенда для всех типов повторений
- Предоставляет `getDayStart` и `isNewDay` с учётом `day_start_hour`

**OFFLINE_FIRST_ARCHITECTURE.md:**
- ❌ Не упоминается

**Статус:** ⚠️ Несогласованность - описан только в `OFFLINE_CLIENT_DESIGN.md`

#### ReminderScheduler

**OFFLINE_CLIENT_DESIGN.md:**
- Планирование и отмена локальных уведомлений
- Работает только с уже пересчитанными данными из кэша, сам пересчёт дат не выполняет

**OFFLINE_FIRST_ARCHITECTURE.md:**
- ❌ Не упоминается

**Статус:** ⚠️ Несогласованность - описан только в `OFFLINE_CLIENT_DESIGN.md`

---

## 2. Согласованность разделения обязанностей

### 2.1. Описание в OFFLINE_FIRST_ARCHITECTURE.md

**Целевая архитектура:**
```
UI → LocalApi (работа с кэшем для UI)
     ↓
     OfflineRepository (хранилище)
     
UI → SyncService (синхронизация)
     ↓
     TasksApi (HTTP-запросы)
     ↓
     OfflineRepository (очередь)
```

**Разделение:**
- `LocalApi` - только работа с локальным хранилищем, не выполняет запросы к серверу
- `SyncService` - только синхронизация, использует `TasksApi` для запросов
- `TasksApi` - только HTTP-запросы, не содержит логики кэширования

### 2.2. Описание в OFFLINE_CLIENT_DESIGN.md

**Текущая архитектура:**
```
UI → TasksApiOffline (прослойка над API)
     ↓
     ├──► OfflineRepository (кэш и очередь)
     └──► TasksApi (HTTP-запросы при наличии сети)
     
UI → SyncService (синхронизация очереди)
     ↓
     TasksApi (HTTP-запросы)
```

**Разделение:**
- `TasksApiOffline` - объединяет работу с кэшем и синхронизацию
- `SyncService` - обрабатывает очередь синхронизации
- `TasksApi` - только HTTP-запросы

### 2.3. Описание в MIGRATION_TO_LOCAL_API.md

**Целевая архитектура (после миграции):**
```
UI → LocalApi (работа с кэшем для UI)
     ↓
     OfflineRepository (Room DB)
     
UI → SyncService (синхронизация)
     ↓
     ├──► TasksApi (HTTP-запросы)
     └──► OfflineRepository (очередь)
```

**Разделение:**
- `LocalApi` - только работа с локальным хранилищем, не выполняет запросы к серверу
- `SyncService` - только синхронизация, использует `TasksApi` для запросов
- `TasksApi` - только HTTP-запросы

**Статус:** ⚠️ **Критическое несоответствие** - разные архитектурные видения:
- `OFFLINE_FIRST_ARCHITECTURE.md` и `MIGRATION_TO_LOCAL_API.md`: целевая архитектура с `LocalApi`
- `OFFLINE_CLIENT_DESIGN.md`: текущая архитектура с `TasksApiOffline`

---

## 3. Согласованность потоков данных

### 3.1. Загрузка задач для UI

**OFFLINE_FIRST_ARCHITECTURE.md:**
```
UI → LocalApi.getTasks()
     ↓
OfflineRepository.loadTasksFromCache()
     ↓
Room Database
     ↓
UI отображает данные (мгновенно)

[В фоне, если есть интернет]
SyncService.syncCacheWithServer()
     ↓
TasksApi.getTasks() → Server
     ↓
OfflineRepository.saveTasksToCache()
```

**OFFLINE_CLIENT_DESIGN.md:**
- Описывает сценарий через фасад:
  - При наличии сети: синхронизировать очередь операций через `SyncService`
  - Проверить наступление нового логического дня
  - Обновить `last_update` и сохранить актуальные задачи в кэше
  - Перепланировать уведомления через `ReminderScheduler`
  - Вернуть задачи UI‑слою для отображения

**Статус:** ⚠️ Несогласованность - разные описания потоков:
- `OFFLINE_FIRST_ARCHITECTURE.md`: использует `LocalApi`
- `OFFLINE_CLIENT_DESIGN.md`: использует фасад и не упоминает `LocalApi`

### 3.2. Создание задачи

**OFFLINE_FIRST_ARCHITECTURE.md:**
```
UI → LocalApi.createTask(task)
     ↓
OfflineRepository.saveTasksToCache([task])  // Оптимистичное обновление
     ↓
OfflineRepository.addToSyncQueue("create", "task", null, task)
     ↓
UI отображает новую задачу (мгновенно)

[В фоне, если есть интернет]
SyncService.syncQueue()
     ↓
TasksApi.syncQueue([queueItem])
```

**OFFLINE_CLIENT_DESIGN.md:**
- Описывает через фасад:
  - Обновить задачу в кэше через `OfflineRepository`
  - Добавить соответствующую операцию в очередь синхронизации
  - Перепланировать уведомления через `ReminderScheduler`

**Статус:** ⚠️ Несогласованность - разные описания:
- `OFFLINE_FIRST_ARCHITECTURE.md`: использует `LocalApi`
- `OFFLINE_CLIENT_DESIGN.md`: использует фасад и не упоминает `LocalApi`

---

## 4. Согласованность интерфейсов и методов

### 4.1. Методы LocalApi

**OFFLINE_FIRST_ARCHITECTURE.md:**
```kotlin
suspend fun getTasks(activeOnly: Boolean = true): List<Task>
suspend fun createTask(task: Task, assignedUserIds: List<Int> = emptyList()): Task
suspend fun updateTask(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Task
suspend fun completeTask(taskId: Int): Task
suspend fun uncompleteTask(taskId: Int): Task
suspend fun deleteTask(taskId: Int)
```

**MIGRATION_TO_LOCAL_API.md:**
```kotlin
suspend fun getTasks(activeOnly: Boolean = true): List<Task>
suspend fun createTask(task: Task, assignedUserIds: List<Int> = emptyList()): Task
suspend fun updateTask(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Task
suspend fun completeTask(taskId: Int): Task
suspend fun uncompleteTask(taskId: Int): Task
suspend fun deleteTask(taskId: Int)
```

**Статус:** ✅ Согласованно описано

### 4.2. Методы SyncService

**OFFLINE_FIRST_ARCHITECTURE.md:**
```kotlin
fun isOnline(): Boolean
suspend fun syncStateBeforeRecalculation(): Boolean
suspend fun syncQueue(): Result<SyncResult>
```

**OFFLINE_CLIENT_DESIGN.md:**
- Не описывает методы детально, только общую ответственность

**MIGRATION_TO_LOCAL_API.md:**
- Упоминает `syncQueue()` и планирует `syncCacheWithServer()`

**Статус:** ⚠️ Частичное несоответствие - `MIGRATION_TO_LOCAL_API.md` упоминает `syncCacheWithServer()`, которого нет в `OFFLINE_FIRST_ARCHITECTURE.md`

### 4.3. Методы OfflineRepository

**OFFLINE_FIRST_ARCHITECTURE.md:**
```kotlin
suspend fun saveTasksToCache(tasks: List<Task>): Result<Unit>
suspend fun loadTasksFromCache(): List<Task>
suspend fun getTaskFromCache(id: Int): Task?
suspend fun deleteTaskFromCache(id: Int)
suspend fun addToSyncQueue(action: String, entityType: String, entityId: Int?, entity: Task?)
suspend fun getPendingQueueItems(): List<SyncQueueItem>
suspend fun clearAllQueue()
suspend fun updateRecurringTasksForNewDay(dayStartHour: Int): Boolean
```

**OFFLINE_CLIENT_DESIGN.md:**
- Не описывает методы детально, только общую ответственность

**Статус:** ✅ Согласованно описано в `OFFLINE_FIRST_ARCHITECTURE.md`

---

## 5. Критические несоответствия

### 5.1. Две разные архитектуры в документации

**Проблема:**
- `OFFLINE_FIRST_ARCHITECTURE.md` описывает **целевую архитектуру** с `LocalApi`
- `OFFLINE_CLIENT_DESIGN.md` описывает **текущую архитектуру** с `TasksApiOffline`
- `MIGRATION_TO_LOCAL_API.md` описывает план миграции с `TasksApiOffline` на `LocalApi`

**Последствия:**
- Неясно, какая архитектура актуальна
- Разные документы описывают разные компоненты
- Потоки данных не согласованы между документами

**Рекомендация:**
- Уточнить статус миграции
- Если миграция запланирована → обновить `OFFLINE_CLIENT_DESIGN.md`, добавив раздел о целевой архитектуре
- Если миграция отменена → обновить `OFFLINE_FIRST_ARCHITECTURE.md`, убрав упоминания `LocalApi`

### 5.2. TasksApiOffline - разные статусы

**Проблема:**
- `OFFLINE_FIRST_ARCHITECTURE.md`: требует миграции
- `OFFLINE_CLIENT_DESIGN.md`: актуальный компонент
- `MIGRATION_TO_LOCAL_API.md`: текущий компонент, планируется удаление

**Рекомендация:**
- Уточнить статус `TasksApiOffline` во всех документах
- Если миграция запланирована → обновить `OFFLINE_CLIENT_DESIGN.md`, указав, что это временный компонент
- Если миграция отменена → обновить `OFFLINE_FIRST_ARCHITECTURE.md`, описав `TasksApiOffline` как постоянный компонент

### 5.3. LocalApi - не упоминается в OFFLINE_CLIENT_DESIGN.md

**Проблема:**
- `OFFLINE_FIRST_ARCHITECTURE.md` и `MIGRATION_TO_LOCAL_API.md` описывают `LocalApi` как целевой компонент
- `OFFLINE_CLIENT_DESIGN.md` не упоминает `LocalApi` вообще

**Рекомендация:**
- Добавить описание `LocalApi` в `OFFLINE_CLIENT_DESIGN.md` с указанием статуса (планируется)
- Или убрать упоминания `LocalApi` из других документов, если миграция отменена

### 5.4. Вспомогательные компоненты не описаны в OFFLINE_FIRST_ARCHITECTURE.md

**Проблема:**
- `TodayTaskFilter`, `TaskDateCalculator`, `ReminderScheduler` описаны только в `OFFLINE_CLIENT_DESIGN.md`
- `OFFLINE_FIRST_ARCHITECTURE.md` не упоминает эти компоненты

**Рекомендация:**
- Добавить описание вспомогательных компонентов в `OFFLINE_FIRST_ARCHITECTURE.md`
- Или уточнить, что они не являются частью основной архитектуры

---

## 6. Рекомендации

### Высокий приоритет

1. **Уточнить статус миграции LocalApi**
   - Определить: выполняется ли миграция или отменена
   - Обновить все документы с единым статусом

2. **Синхронизировать описание архитектуры**
   - Привести `OFFLINE_FIRST_ARCHITECTURE.md` и `OFFLINE_CLIENT_DESIGN.md` к единому видению
   - Уточнить, какая архитектура является целевой, а какая текущей

3. **Обновить OFFLINE_CLIENT_DESIGN.md**
   - Добавить описание `LocalApi` (если миграция запланирована)
   - Указать статус `TasksApiOffline` (временный или постоянный)
   - Синхронизировать потоки данных с `OFFLINE_FIRST_ARCHITECTURE.md`

4. **Обновить OFFLINE_FIRST_ARCHITECTURE.md**
   - Добавить описание вспомогательных компонентов (`TodayTaskFilter`, `TaskDateCalculator`, `ReminderScheduler`)
   - Уточнить статус `TasksApiOffline` (если миграция отменена)

### Средний приоритет

5. **Дополнить описание методов**
   - Добавить описание всех методов `SyncService` в `OFFLINE_CLIENT_DESIGN.md`
   - Уточнить наличие метода `syncCacheWithServer()` в `OFFLINE_FIRST_ARCHITECTURE.md`

6. **Создать единый индекс компонентов**
   - Создать таблицу со всеми компонентами и их статусами
   - Указать, в каких документах описан каждый компонент

---

## 7. Выводы

1. **TasksApi** - ✅ Согласованно описано
2. **SyncService** - ✅ Согласованно описано
3. **OfflineRepository** - ✅ Согласованно описано
4. **LocalApi** - ⚠️ Описан в `OFFLINE_FIRST_ARCHITECTURE.md` и `MIGRATION_TO_LOCAL_API.md`, но не упоминается в `OFFLINE_CLIENT_DESIGN.md`
5. **TasksApiOffline** - ⚠️ **Критическое несоответствие** - разные статусы в разных документах
6. **Вспомогательные компоненты** - ⚠️ Описаны только в `OFFLINE_CLIENT_DESIGN.md`

**Основная проблема:** Документация описывает две разные архитектуры без четкого указания, какая является целевой, а какая текущей. Требуется синхронизация документов и уточнение статуса миграции.

