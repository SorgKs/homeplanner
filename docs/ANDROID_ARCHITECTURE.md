# Архитектура Android приложения HomePlanner

Подробное описание архитектуры Android приложения HomePlanner, компонентов, потоков данных и принципов проектирования.

> **Важное примечание об архитектуре:**  
> В текущей реализации WebSocket подключение управляется в `MainActivity.kt` (UI слой), что является нарушением принципов разделения ответственности.  
> **Правильная архитектура:** WebSocket должен работать на уровне синхронизации/сети, обновляя локальный кэш через `OfflineRepository`, а UI должен обновляться через стандартный механизм наблюдения за кэшем (Flow/State).  
> В документе описана **правильная архитектура** и отмечены места, где текущая реализация отличается от неё.

## Содержание

1. [Обзор архитектуры](#обзор-архитектуры)
2. [Принципы проектирования](#принципы-проектирования)
3. [Структура проекта](#структура-проекта)
4. [Компоненты приложения](#компоненты-приложения)
5. [Потоки данных](#потоки-данных)
6. [Хранилище данных](#хранилище-данных)
7. [Синхронизация](#синхронизация)
8. [UI компоненты](#ui-компоненты)
9. [Работа с сетью](#работа-с-сетью)
10. [Обработка ошибок](#обработка-ошибок)
11. [Тестирование](#тестирование)

---

## Обзор архитектуры

Android приложение HomePlanner построено на принципах **offline-first** архитектуры, которая обеспечивает:

- Мгновенную работу приложения даже без интернета
- Автоматическую синхронизацию данных при появлении соединения
- Сохранение всех пользовательских действий в локальном хранилище
- Оптимистичные обновления UI для лучшего UX

### Основные части

Архитектура состоит из следующих основных частей:

1. **UI** (Пользовательский интерфейс)
    - Берет данные из LocalCache и отображает интерфейс
    - Модифицирует LocalCache в соответствии с действиями пользователя
    - Никоим образом не вмешивается в общение с сервером

2. **Business Logic Layer**
   - `LocalApi` — работа с локальным хранилищем для UI
   - `SyncService` — синхронизация с сервером
   - `ReminderScheduler` — планирование уведомлений

3. **Data Layer**
   - `OfflineRepository` — управление локальным хранилищем
   - `TasksApi` / `ServerApi` — запросы к серверу
   - Room Database — локальное хранилище данных

4. **Network Layer**
   - HTTP клиент (OkHttp)
   - WebSocket сервис (⚠️ должен быть здесь, но сейчас реализован в UI слое)
   - Обработка сетевых запросов

---

## Принципы проектирования

### 1. Offline-First

**Принцип:** Приложение работает полностью автономно, используя локальный кэш как основной источник данных.

**Реализация:**
- Все данные для UI загружаются из локального кэша
- Изменения применяются локально немедленно (оптимистичные обновления)
- Синхронизация с сервером происходит в фоне

**Преимущества:**
- Мгновенный отклик UI
- Работа без интернета
- Лучший пользовательский опыт

### 2. Сервер — источник истины

**Принцип:** Сервер является единственным источником истины для всех данных.

**Реализация:**
- Все конфликты разрешаются на сервере
- После синхронизации локальный кэш обновляется данными с сервера
- Клиент отправляет операции на сервер, сервер решает, какие применить

**Преимущества:**
- Согласованность данных
- Централизованное разрешение конфликтов
- Надёжность системы

### 3. Разделение ответственности

**Принцип:** Каждый компонент имеет чётко определённую ответственность.

**Реализация:**
- `LocalApi` — работа с локальным хранилищем для UI
- `SyncService` — синхронизация с сервером
- `OfflineRepository` — низкоуровневая работа с БД
- `TasksApi` — HTTP запросы к серверу

**Преимущества:**
- Упрощение тестирования
- Легкость поддержки
- Переиспользование компонентов

### 4. Оптимистичные обновления

**Принцип:** UI обновляется немедленно, синхронизация происходит в фоне.

**Реализация:**
- Пользовательские действия применяются локально сразу
- Операции добавляются в очередь синхронизации
- После синхронизации UI обновляется данными с сервера

**Преимущества:**
- Мгновенный отклик UI
- Плавная работа приложения
- Улучшенный UX

---

## Структура проекта

```
android/app/src/main/java/com/homeplanner/
├── MainActivity.kt                    # Главный экран приложения
├── NetworkConfig.kt                   # Конфигурация сети
├── NetworkSettings.kt                 # Управление настройками сети
├── UserSettings.kt                    # Управление настройками пользователя
├── ReminderScheduler.kt               # Планирование уведомлений
├── ReminderActivity.kt                # Activity для уведомлений
├── ReminderReceiver.kt                # Receiver для уведомлений
├── NotificationHelper.kt              # Вспомогательные функции для уведомлений
├── QrCodeScanner.kt                   # Сканер QR-кодов
│
├── api/                               # API клиенты
│   ├── LocalApi.kt                    # Работа с локальным хранилищем для UI
│   ├── ServerApi.kt                   # HTTP запросы к серверу
│   ├── GroupsApi.kt                   # API для групп
│   ├── GroupsLocalApi.kt              # Локальный API для групп
│   ├── UsersApi.kt                    # API для пользователей
│   └── UsersLocalApi.kt               # Локальный API для пользователей
│
├── database/                          # Работа с базой данных
│   ├── AppDatabase.kt                 # Room Database
│   ├── dao/                           # Data Access Objects
│   │   ├── TaskCacheDao.kt            # DAO для задач
│   │   ├── SyncQueueDao.kt            # DAO для очереди синхронизации
│   │   └── MetadataDao.kt             # DAO для метаданных
│   └── entity/                        # Entity классы
│       ├── TaskCache.kt               # Entity для кэша задач
│       ├── SyncQueueItem.kt           # Entity для очереди синхронизации
│       └── Metadata.kt                # Entity для метаданных
│
├── repository/                        # Репозитории
│   └── OfflineRepository.kt           # Репозиторий для работы с локальным хранилищем
│
├── sync/                              # Синхронизация
│   └── SyncService.kt                 # Сервис синхронизации
│
├── model/                             # Модели данных
│   └── Task.kt                        # Модель задачи
│
├── utils/                             # Утилиты
│   ├── TaskDateCalculator.kt          # Калькулятор дат для задач
│   └── TodayTaskFilter.kt             # Фильтр задач для вкладки "Сегодня"
│
├── ui/                                # UI компоненты
│   ├── StatusBar.kt                   # Компонент строки состояния
│   └── tasks/                         # Экраны задач
│       ├── MainScreen.kt              # Главный экран с навигацией
│       ├── TodayScreen.kt             # Экран задач на сегодня
│       ├── AllTasksScreen.kt          # Экран всех задач
│       ├── SettingsScreen.kt          # Экран настроек
│       ├── TaskListContent.kt         # Переиспользуемый компонент списка задач
│       └── TaskItem.kt                # Элемент задачи
│
└── debug/                             # Отладочные инструменты
    ├── BinaryLogger.kt                # Бинарное логирование
    ├── BinaryLogStorage.kt            # Хранилище логов
    ├── BinaryLogEncoder.kt            # Кодировщик логов
    ├── BinaryLogEntry.kt              # Запись лога
    ├── ChunkSender.kt                 # Отправка чанков логов
    ├── LogCleanupManager.kt           # Очистка старых логов
    ├── LogLevel.kt                    # Уровни логирования
    ├── DictionaryRevision.kt          # Ревизия словаря логов
    └── DeviceIdHelper.kt              # Генерация ID устройства
```

---

## Компоненты приложения

### MainActivity

**Расположение:** `MainActivity.kt`

**Ответственность:**
- Главный экран приложения
- Управление навигацией между вкладками
- Координация работы компонентов
- Управление состоянием UI

**Основные функции:**
- Отображение трёх вкладок: "Сегодня", "Все задачи", "Настройки"
- Управление WebSocket соединением
- Обработка пользовательских действий (создание, редактирование, удаление задач)
- Координация синхронизации данных
- Управление уведомлениями

### LocalApi

**Расположение:** `api/LocalApi.kt`

**Ответственность:**
- Предоставление данных из локального хранилища для UI
- Сохранение изменений в локальный кэш
- Добавление операций в очередь синхронизации

**Методы:**
```kotlin
suspend fun getTasks(activeOnly: Boolean = true): List<Task>
suspend fun createTask(task: Task, assignedUserIds: List<Int> = emptyList()): Task
suspend fun updateTask(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Task
suspend fun completeTask(taskId: Int): Task
suspend fun uncompleteTask(taskId: Int): Task
suspend fun deleteTask(taskId: Int)
```

**Особенности:**
- Все методы работают только с локальным хранилищем
- Не выполняет запросы к серверу
- Все изменения автоматически добавляются в очередь синхронизации
- Возвращает данные немедленно (оптимистичные обновления)

### ServerApi / TasksApi

**Расположение:** `api/ServerApi.kt`

**Ответственность:**
- Выполнение HTTP-запросов к REST API сервера
- Парсинг JSON-ответов
- Обработка HTTP-ошибок

**Методы:**
```kotlin
fun getTasks(activeOnly: Boolean = true): List<Task>
fun createTask(task: Task, assignedUserIds: List<Int> = emptyList()): Task
fun updateTask(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Task
fun completeTask(taskId: Int): Task
fun uncompleteTask(taskId: Int): Task
fun deleteTask(taskId: Int)
fun syncQueue(queueItems: List<SyncQueueItem>): List<Task>
```

**Особенности:**
- Не содержит логики кэширования
- Не зависит от наличия интернета (выбрасывает исключения при отсутствии)
- Используется только `SyncService` для синхронизации

### SyncService

**Расположение:** `sync/SyncService.kt`

**Ответственность:**
- Синхронизация данных между локальным хранилищем и сервером
- Обработка очереди операций
- Загрузка актуальных данных с сервера

**Методы:**
```kotlin
fun isOnline(): Boolean
suspend fun syncStateBeforeRecalculation(): Boolean
suspend fun syncQueue(): Result<SyncResult>
suspend fun syncCacheWithServer(
    groupsApi: GroupsApi? = null,
    usersApi: UsersApi? = null
): Result<SyncCacheResult>
```

**Алгоритм синхронизации очереди:**
1. Проверка наличия интернет-соединения
2. Получение операций из очереди
3. Отправка операций на сервер
4. Очистка очереди при успешной синхронизации
5. Обновление кэша данными с сервера

### OfflineRepository

**Расположение:** `repository/OfflineRepository.kt`

**Ответственность:**
- Низкоуровневая работа с Room database
- Управление очередью синхронизации
- Очистка старых данных
- Управление метаданными хранилища

**Основные методы:**
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

### TaskDateCalculator

**Расположение:** `utils/TaskDateCalculator.kt`

**Ответственность:**
- Вычисление начала логического дня
- Проверка наступления нового логического дня
- Пересчёт дат для повторяющихся задач

**Методы:**
```kotlin
fun getDayStart(time: LocalDateTime, dayStartHour: Int): LocalDateTime
fun isNewDay(lastUpdateMillis: Long?, nowMillis: Long, lastDayStartHour: Int?, currentDayStartHour: Int): Boolean
fun calculateNextReminderTime(task: Task, nowMillis: Long, dayStartHour: Int): String
```

**Особенности:**
- Логика согласована с серверным `TaskService`
- Поддерживает все типы повторений (DAILY, WEEKLY, MONTHLY, YEARLY и т.д.)
- Поддерживает interval задачи

### TodayTaskFilter

**Расположение:** `utils/TodayTaskFilter.kt`

**Ответственность:**
- Фильтрация задач для вкладки "Сегодня"
- Учёт логического дня
- Фильтрация по выбранному пользователю

**Методы:**
```kotlin
fun filterTodayTasks(
    tasks: List<Task>,
    selectedUser: SelectedUser?,
    dayStartHour: Int
): List<Task>
```

**Правила фильтрации:**
- **one_time задачи**: Видны, если `reminder_time` сегодня или в прошлом, или если `completed = true`
- **recurring/interval задачи**: Видны, если `reminder_time` сегодня или в прошлом

### ReminderScheduler

**Расположение:** `ReminderScheduler.kt`

**Ответственность:**
- Планирование локальных уведомлений
- Отмена уведомлений
- Работа с AlarmManager

**Методы:**
```kotlin
fun scheduleForTasks(tasks: List<Task>)
fun scheduleForTaskIfUpcoming(task: Task)
fun cancelForTask(task: Task)
fun cancelAll(tasks: List<Task>)
```

---

## Потоки данных

### Поток создания задачи

```
1. Пользователь создаёт задачу в UI
   ↓
2. MainActivity вызывает LocalApi.createTask()
   ↓
3. LocalApi:
   - Сохраняет задачу в локальный кэш (OfflineRepository)
   - Добавляет операцию в очередь синхронизации
   - Возвращает задачу немедленно
   ↓
4. UI обновляется с новой задачей (оптимистичное обновление)
   ↓
5. SyncService в фоне:
   - Получает операцию из очереди
   - Отправляет на сервер через TasksApi
   - Получает обновлённую задачу с сервера
   - Обновляет локальный кэш
   ↓
6. UI обновляется данными с сервера
```

### Поток синхронизации

```
1. SyncService.syncQueue() вызывается (автоматически или вручную)
   ↓
2. Проверка наличия интернета
   ↓
3. Получение операций из очереди (OfflineRepository.getPendingQueueItems())
   ↓
4. Отправка операций на сервер (TasksApi.syncQueue())
   ↓
5. Сервер обрабатывает операции и возвращает обновлённые задачи
   ↓
6. Очистка очереди (OfflineRepository.clearAllQueue())
   ↓
7. Обновление локального кэша (OfflineRepository.saveTasksToCache())
   ↓
8. UI обновляется из кэша
```

### Поток загрузки задач

```
1. MainActivity вызывает loadTasksFromCacheLocal()
   ↓
2. OfflineRepository.loadTasksFromCache()
   ↓
3. Задачи загружаются из Room Database
   ↓
4. Фильтрация задач для вкладки "Сегодня" (TodayTaskFilter)
   ↓
5. UI отображает задачи
```

### Поток обновления через WebSocket (правильная архитектура)

```
1. WebSocketService получает событие от сервера
   ↓
2. WebSocketService парсит событие (task_update, task_created и т.д.)
   ↓
3. WebSocketService обновляет локальный кэш через OfflineRepository
   ↓
4. OfflineRepository сохраняет изменения в Room Database
   ↓
5. UI наблюдает за изменениями кэша (через Flow/State)
   ↓
6. UI автоматически обновляется с новыми данными из кэша
```

**Принципы:**
- WebSocket работает независимо от UI
- События сохраняются в локальный кэш
- UI обновляется через стандартный механизм наблюдения
- Нет прямой связи между WebSocket и UI

### Поток пересчёта дат

```
1. При загрузке задач проверяется наступление нового дня
   ↓
2. OfflineRepository.updateRecurringTasksForNewDay()
   ↓
3. TaskDateCalculator.isNewDay() проверяет, наступил ли новый день
   ↓
4. Если новый день:
   - Для завершённых recurring задач пересчитывается reminder_time
   - Для завершённых interval задач пересчитывается reminder_time
   - Для завершённых one_time задач устанавливается active = false
   - completed сбрасывается в false для повторяющихся задач
   ↓
5. Обновлённые задачи сохраняются в кэш
   ↓
6. UI обновляется
```

---

## Хранилище данных

### Room Database

Приложение использует Room Database для локального хранения данных.

**База данных:** `AppDatabase`
- Версия: 2
- Entities: `TaskCache`, `SyncQueueItem`, `Metadata`

### Таблицы

#### TaskCache

Хранит кэшированные задачи.

**Поля:**
- `id` (Int, Primary Key) — ID задачи
- `title` (String) — Название задачи
- `description` (String?) — Описание
- `taskType` (String) — Тип задачи (one_time, recurring, interval)
- `recurrenceType` (String?) — Тип повторения
- `recurrenceInterval` (Int?) — Интервал повторения
- `intervalDays` (Int?) — Интервал в днях
- `reminderTime` (String) — Время напоминания (ISO format)
- `groupId` (Int?) — ID группы
- `active` (Boolean) — Активна ли задача
- `completed` (Boolean) — Выполнена ли задача
- `assignedUserIds` (String) — JSON array пользователей
- `updatedAt` (Long) — Время последнего обновления
- `lastAccessed` (Long) — Время последнего доступа

**Индексы:**
- `reminderTime`
- `updatedAt`
- `taskType`

#### SyncQueueItem

Хранит операции в очереди синхронизации.

**Поля:**
- `id` (Long, Primary Key, AutoGenerate) — ID операции
- `operation` (String) — Тип операции (create, update, delete, complete, uncomplete)
- `entityType` (String) — Тип сущности (task)
- `entityId` (Int?) — ID сущности (null для create)
- `payload` (String?) — JSON payload (для create/update)
- `timestamp` (Long) — Время создания операции
- `retryCount` (Int) — Количество попыток
- `lastRetry` (Long?) — Время последней попытки
- `status` (String) — Статус (pending, syncing, failed)
- `sizeBytes` (Long) — Размер операции в байтах

**Индексы:**
- `status`
- `timestamp`
- `entityId`
- `operation`
- `sizeBytes`

#### Metadata

Хранит метаданные хранилища.

**Поля:**
- `key` (String, Primary Key) — Ключ метаданных
- `value` (String) — Значение (JSON)

### DAO (Data Access Objects)

#### TaskCacheDao

Методы для работы с задачами:
- `getAllTasks(): Flow<List<TaskCache>>`
- `getTaskById(id: Int): TaskCache?`
- `getTasksByDateRange(startDate: String, endDate: String): List<TaskCache>`
- `getTasksByType(taskType: String): List<TaskCache>`
- `getOldestTasks(cutoffTime: Long, limit: Int): List<TaskCache>`
- `getTaskCount(): Int`
- `getCacheSizeBytes(): Long?`
- `insertTask(task: TaskCache)`
- `insertTasks(tasks: List<TaskCache>)`
- `updateTask(task: TaskCache)`
- `updateLastAccessed(id: Int, timestamp: Long)`
- `deleteTask(task: TaskCache)`
- `deleteTaskById(id: Int)`

#### SyncQueueDao

Методы для работы с очередью синхронизации:
- `getAll(): Flow<List<SyncQueueItem>>`
- `getPending(): Flow<List<SyncQueueItem>>`
- `getByStatus(status: String): List<SyncQueueItem>`
- `insert(item: SyncQueueItem)`
- `insertAll(items: List<SyncQueueItem>)`
- `update(item: SyncQueueItem)`
- `delete(item: SyncQueueItem)`
- `deleteAll()`
- `getPendingCount(): Int`
- `getTotalSizeBytes(): Long?`

#### MetadataDao

Методы для работы с метаданными:
- `get(key: String): Metadata?`
- `getAll(): Flow<List<Metadata>>`
- `insert(metadata: Metadata)`
- `update(metadata: Metadata)`
- `delete(key: String)`
- `deleteAll()`

---

## Синхронизация

### Алгоритм синхронизации

Синхронизация происходит в несколько этапов:

1. **Проверка соединения**
   - Проверка наличия интернета
   - Проверка доступности сервера

2. **Синхронизация очереди**
   - Получение операций из очереди
   - Отправка операций на сервер
   - Обработка ответа сервера
   - Очистка очереди при успехе

3. **Обновление кэша**
   - Загрузка актуальных данных с сервера
   - Сохранение в локальный кэш
   - Обновление UI через наблюдение за кэшем

### Стратегия синхронизации

**Автоматическая синхронизация:**
- При восстановлении интернет-соединения
- При наличии операций в очереди

**Ручная синхронизация:**
- По нажатию на индикатор синхронизации
- По нажатию на количество ожидающих операций

### WebSocket синхронизация

**Правильная архитектура:**

WebSocket должен работать на уровне синхронизации/сети, независимо от UI:

1. **WebSocketService** (в слое синхронизации/сети)
   - Управляет WebSocket соединением
   - Получает события от сервера (`task_update`, `task_created`, `task_deleted` и т.д.)
   - Обновляет локальный кэш через `OfflineRepository.saveTasksToCache()`
   - Работает в фоне, независимо от UI

2. **Поток обновления через WebSocket:**
   ```
   Server → WebSocketService → OfflineRepository → Room Database
                                              ↓
   UI наблюдает за кэшем (Flow/State) → UI обновляется автоматически
   ```

3. **Принципы:**
   - WebSocket работает независимо от состояния UI
   - События сохраняются в локальный кэш
   - UI обновляется через стандартный механизм наблюдения за кэшем (Flow/State)
   - Нет прямой связи между WebSocket и UI
   - Все обновления проходят через локальный кэш

4. **Преимущества правильной архитектуры:**
   - Разделение ответственности: WebSocket не в UI слое
   - Независимость от UI: WebSocket работает даже когда UI неактивен
   - Единый источник данных: все обновления проходят через локальный кэш
   - Упрощение тестирования: можно тестировать WebSocket отдельно от UI

**Примечание:**
⚠️ **Текущая реализация**: WebSocket реализован в `MainActivity.kt` (UI слой), что нарушает принципы разделения ответственности. Требуется рефакторинг для перемещения WebSocket в слой синхронизации/сети (например, в `SyncService` или отдельный `WebSocketService`).

### Разрешение конфликтов

Конфликты разрешаются на сервере:

- Сервер является источником истины
- Клиент отправляет операции с timestamp
- Сервер сравнивает timestamp и решает, какие операции применить
- Клиент получает обновлённое состояние с сервера

---

## UI компоненты

### MainActivity

Главный экран приложения, построенный на Jetpack Compose с использованием Jetpack Navigation Compose.

**Навигационная структура:**
- Использует `NavHost` для управления экранами
- Три основных маршрута: "сегодня", "все задачи", "настройки"
- Нижняя навигационная панель (`NavigationBar`) отражает текущий маршрут

**Компоненты:**
- `MainScreen()` — контейнер с NavHost и нижней навигацией
- `TodayScreen()` — экран задач на сегодня
- `AllTasksScreen()` — экран всех задач
- `SettingsScreen()` — экран настроек
- `TaskListContent()` — переиспользуемый компонент отображения списка задач
- `AppStatusBar` — строка состояния

**Структура файлов UI:**
```
ui/tasks/
├── MainScreen.kt          # Навигационный контейнер с NavHost
├── TodayScreen.kt         # Экран задач на сегодня
├── AllTasksScreen.kt      # Экран всех задач
├── SettingsScreen.kt      # Экран настроек
├── TaskListContent.kt     # Переиспользуемый компонент списка задач
└── TaskItem.kt           # Элемент задачи
```

**Примечание об архитектуре:**
- ✅ **Обновлено**: Теперь используется Jetpack Navigation Compose для правильного управления навигацией и back stack
- ⚠️ **Текущая реализация**: WebSocket подключение управляется в `MainActivity.kt` — это нарушение архитектуры
- ✅ **Правильная архитектура**: WebSocket должен работать в слое синхронизации/сети, обновляя локальный кэш через `OfflineRepository`, а UI должен обновляться через наблюдение за кэшем

### AppStatusBar

Компонент строки состояния, показывает:
- Статус соединения (ONLINE/DEGRADED/OFFLINE/UNKNOWN)
- Индикатор синхронизации
- Использование хранилища
- Количество ожидающих операций

**Режимы:**
- Полный режим — все индикаторы
- Компактный режим — основные индикаторы

---

## Работа с сетью

### HTTP клиент

Используется OkHttp для HTTP запросов.

**Конфигурация:**
- Таймауты подключения и чтения
- Обработка ошибок
- Логирование запросов

### WebSocket

WebSocket используется для получения обновлений в реальном времени.

**Архитектурное расположение:**
- ⚠️ **Текущая реализация**: WebSocket реализован в `MainActivity.kt` (UI слой) — это нарушение архитектуры
- ✅ **Правильная архитектура**: WebSocket должен работать на уровне синхронизации/сети, а не UI

**Правильная архитектура WebSocket:**

WebSocket должен быть частью слоя синхронизации, работая между локальным кэшем и сервером:

```
┌─────────────────────────────────────┐
│           UI Layer                   │
│      (MainActivity, Compose)        │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│       Business Logic Layer          │
│         (LocalApi, SyncService)     │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│      Data / Network Layer           │
│  ┌──────────────┐  ┌─────────────┐ │
│  │OfflineRepo.  │  │  ServerApi  │ │
│  └──────────────┘  │   HTTP      │ │
│                    └─────────────┘ │
│  ┌───────────────────────────────┐ │
│  │   WebSocketService            │ │
│  │   (синхронизация кэша)        │ │
│  └───────────────────────────────┘ │
└─────────────────────────────────────┘
```

**Правильная реализация:**

1. **WebSocketService** — отдельный сервис в слое синхронизации/сети
   - Управляет WebSocket соединением
   - Получает события от сервера
   - Обновляет локальный кэш через `OfflineRepository`
   - НЕ обновляет UI напрямую

2. **Поток данных:**
   ```
   Server → WebSocketService → OfflineRepository → LocalApi → UI
   ```

3. **Принципы:**
   - WebSocket работает в фоне, независимо от UI
   - События сохраняются в локальный кэш
   - UI обновляется через стандартный механизм наблюдения за кэшем (Flow/State)
   - UI не зависит от WebSocket напрямую

**Особенности:**
- Автоматическое переподключение
- Обработка сообщений о событиях задач
- Обновление локального кэша при получении событий
- UI обновляется через наблюдение за кэшем

**События:**
- `task_update` — обновление задачи
- `task_created` — создание задачи
- `task_deleted` — удаление задачи
- `task_completed` — подтверждение задачи
- `task_uncompleted` — отмена подтверждения

**Примечание:**
Текущая реализация WebSocket в `MainActivity.kt` требует рефакторинга для соответствия правильной архитектуре. WebSocket должен быть перемещён в слой синхронизации/сети и работать через `OfflineRepository` для обновления локального кэша.

---

## Обработка ошибок

### Типы ошибок

1. **Сетевые ошибки**
   - Отсутствие интернета
   - Таймауты
   - Ошибки соединения

2. **HTTP ошибки**
   - 4xx — ошибки клиента
   - 5xx — ошибки сервера

3. **Ошибки данных**
   - Некорректный JSON
   - Отсутствующие поля
   - Ошибки валидации

### Стратегия обработки

**Оптимистичные обновления:**
- Ошибки не блокируют UI
- Операции остаются в очереди
- Повторная попытка при следующей синхронизации

**Логирование:**
- Все ошибки логируются
- Критические ошибки отмечаются в UI

---

## Тестирование

### Unit тесты

Тесты для утилит и бизнес-логики:
- `TaskDateCalculatorTest` — тесты калькулятора дат
- `TodayTaskFilterTest` — тесты фильтра задач

### Интеграционные тесты

Тесты интеграции компонентов:
- `OfflineRepositoryTest` — тесты репозитория
- `SyncServiceTest` — тесты синхронизации

### Инструментальные тесты

Тесты UI и взаимодействия:
- `TasksApiInstrumentedTest` — тесты API
- `OfflineModeIntegrationTest` — интеграционные тесты офлайн режима

---

## Диаграммы

### Архитектура компонентов

```
┌─────────────────────────────────────────────────┐
│                    UI Layer                      │
│  (Jetpack Compose - MainActivity)               │
│  - Отображение данных                           │
│  - Обработка пользовательских действий          │
│  - НЕ управляет WebSocket                       │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│              Business Logic Layer                │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │
│  │LocalApi  │  │SyncService│ │ReminderSched.│  │
│  └──────────┘  └──────────┘  └──────────────┘  │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│                  Data Layer                      │
│  ┌──────────────┐  ┌─────────────────────────┐ │
│  │OfflineRepo.  │  │ TasksApi / ServerApi    │ │
│  └──────────────┘  └─────────────────────────┘ │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│              Storage / Network                   │
│  ┌──────────┐  ┌──────────┐  ┌────────────────┐│
│  │Room DB   │  │  HTTP    │  │ WebSocketService│
│  │          │  │          │  │ (синхронизация)││
│  └──────────┘  └──────────┘  └────────────────┘│
└─────────────────────────────────────────────────┘
```

**Примечание:** WebSocket должен работать в слое Storage/Network, обновляя локальный кэш, а не напрямую в UI.

### Поток создания задачи

```
User Action
    │
    ▼
MainActivity.createTask()
    │
    ▼
LocalApi.createTask()
    │
    ├─► OfflineRepository.saveTaskToCache()
    │       │
    │       └─► Room Database (TaskCache)
    │
    └─► OfflineRepository.addToSyncQueue()
            │
            └─► Room Database (SyncQueueItem)
    │
    ▼
UI Updates (optimistic)
    │
    ▼
SyncService.syncQueue() (background)
    │
    ├─► TasksApi.syncQueue()
    │       │
    │       └─► HTTP POST /tasks/sync-queue
    │
    └─► OfflineRepository.saveTasksToCache()
            │
            └─► Room Database (TaskCache)
    │
    ▼
UI Updates (server data)
```

---

**Последнее обновление:** 2026-01-04

