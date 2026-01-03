# Спецификация классов и API Android приложения HomePlanner

## КРИТИЧНЫЕ ТРЕБОВАНИЯ АРХИТЕКТУРЫ

## ВСЯ ИНИЦИАЛИЗАЦИЯ КОМПОНЕНТОВ ДОЛЖНА ПРОИСХОДИТЬ В APPLICATION LEVEL

Application.onCreate() отвечает за создание всех компонентов всех слоёв в правильном порядке:
- DI container → Business Logic + Data Layer → Network Layer → Logging Layer

UI Layer получает готовые компоненты через DI и НЕ занимается созданием бизнес-логики.

## Обзор

Этот документ содержит полную спецификацию всех классов, API и методов в новой 4-слойной архитектуре Android приложения HomePlanner.

Архитектура состоит из:
0. **Application Level** - глобальная инициализация
1. **UI Layer** - пользовательский интерфейс
2. **Business Logic Layer** - бизнес-логика
3. **Data Layer** - доступ к данным
4. **Network Layer** - сетевая коммуникация

## 0. Application Level

### Application.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/Application.kt`
**Ответственность**: Глобальная инициализация приложения и компонентов

**Методы**:
- `onCreate()` - инициализация DI контейнера, создание компонентов всех слоев (Business Logic, Data Layer, Network Layer, Logging Layer)

**Зависимости**: Android Application class

---

## 1. UI Layer

### MainActivity.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/MainActivity.kt`
**Ответственность**: Главный экран приложения, управление жизненным циклом

**Жизненный цикл**:
- `onCreate()` - настройка Compose UI, получение TaskViewModel из DI
- `onResume()`, `onPause()`, `onDestroy()` - стандартные методы жизненного цикла

**Навигация**:
- `navigateToTodayTab()`, `navigateToAllTasksTab()`, `navigateToSettingsTab()` - методы навигации между вкладками
- `handleUiEvents()` - обработка событий UI

**UI состояние**:
- Наблюдение за TaskViewModel.state через StateFlow

**Зависимости**:
- `TaskViewModel` - получение через Koin DI с помощью `koinViewModel()`
- `TaskViewModel.state` - наблюдение за состоянием через StateFlow
- Compose Navigation API для навигации

---

### TaskListScreen.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/ui/tasks/TaskListScreen.kt`
**Ответственность**: Экран списка задач

**Функции**:
- `TaskListScreen()` - главный экран списка задач
- `TaskListContent()`, `TodayTasksList()`, `AllTasksList()` - компоненты отображения списков
- `LoadingIndicator()`, `ErrorMessage()`, `EmptyState()` - компоненты состояний UI

**Параметры**:
- `state: TaskScreenState` - состояние экрана задач
- `onTaskClick`, `onTaskComplete`, `onTaskDelete`, `onCreateTask` - обработчики событий

**Зависимости**: Нет (параметры передаются извне)

---

### TaskItem.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/ui/tasks/TaskItem.kt`
**Ответственность**: Элемент списка задач

**Функции**:
- `TaskItem()` - элемент списка задач с swipe действиями
- `TaskItemContent()`, `SwipeActions()`, `TaskContent()` - компоненты отображения задачи
- `TaskTitle()`, `TaskDescription()`, `TaskTime()` - компоненты полей задачи

**Параметры**:
- `task: Task` - данные задачи
- `onComplete`, `onClick`, `onDelete` - обработчики действий

**Зависимости**: Нет

---

### TaskDialogs.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/ui/tasks/TaskDialogs.kt`
**Ответственность**: Диалоги для работы с задачами

**Диалоги**:
- `CreateTaskDialog()` - диалог создания новой задачи
- `EditTaskDialog()` - диалог редактирования задачи
- `DeleteTaskDialog()` - диалог подтверждения удаления задачи

**Компоненты**:
- `DialogContent()`, `TaskForm()`, `ConfirmationMessage()` - внутренние компоненты диалогов

**Зависимости**: Нет

---

## 2. Business Logic Layer

### TaskViewModel.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/viewmodel/TaskViewModel.kt`
**Ответственность**: Управление состоянием UI и координация бизнес-логики

**Ответственность**: Управление состоянием UI и координация бизнес-логики

**Конструктор**: Принимает Application, LocalApi и NetworkSettings

**Инициализация**: Происходит автоматически в init блоке через networkSettings.configFlow для загрузки сетевых настроек и начальных данных

**Методы**:

- `getFilteredTasks(tasks: List<Task>, filter: TaskFilterType): List<Task>` - Фильтрует список задач по заданному типу фильтра
- `updateSelectedTab(tab: ViewTab)` - Обновляет выбранную вкладку в состоянии

**Внутренние методы**:

- `updateState(newState: TaskScreenState)` - Обновляет внутреннее состояние ViewModel
- `handleError(error: Throwable)` - Обрабатывает исключения и обновляет состояние с сообщением об ошибке
- `loadInitialData()` - Загружает начальные данные из локального хранилища
- `performInitialSyncIfNeeded()` - Выполняет начальную синхронизацию при необходимости

**Зависимости**:
- `TaskFilter.filterTasks()`

---

### TaskFilter.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/utils/TaskFilter.kt`
**Ответственность**: Универсальная фильтрация задач по различным критериям

**Методы**:
- `filterTasks()` - фильтрация задач по типу, пользователю и времени
- `isTaskVisibleToday()` - проверка видимости задачи сегодня
- `getDayStartTime()` - вычисление начала дня
- `filterByUser()`, `filterByTime()`, `filterByCompletion()` - вспомогательные методы фильтрации

**Зависимости**:
- `TodayTaskFilter.filterTodayTasks()`

---

### TaskDateCalculator.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/utils/TaskDateCalculator.kt`
**Ответственность**: Вычисление дат для задач

**Методы**:
- `getDayStart()` - вычисление начала дня
- `isNewDay()` - проверка наступления нового дня
- `calculateNextReminderTime()` - расчет следующего напоминания
- `shouldRecalculateTask()` - проверка необходимости пересчета
- `getNextOccurrence()`, `adjustForDayStart()` - вспомогательные методы

**Зависимости**: Нет

---

### ReminderScheduler.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/ReminderScheduler.kt`
**Ответственность**: Планирование уведомлений

**Методы**:
- `scheduleForTasks()`, `scheduleForTaskIfUpcoming()` - планирование уведомлений
- `cancelForTask()`, `cancelAll()` - отмена уведомлений
- `updateNotificationSettings()` - обновление настроек уведомлений
- `createPendingIntent()`, `calculateTriggerTime()`, `shouldScheduleNotification()` - вспомогательные методы

**Зависимости**: Android AlarmManager API

---

## 3. Data Layer

### LocalApi.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/api/LocalApi.kt`
**Ответственность**: Работа с локальным хранилищем для UI и Business Logic

**Методы для задач**:
- `getTasksLocal()` - получение задач из кэша
- `createTaskLocal()`, `updateTaskLocal()` - создание и обновление задач
- `completeTaskLocal()`, `uncompleteTaskLocal()` - управление статусом выполнения
- `deleteTaskLocal()` - удаление задач

**Методы для групп и пользователей**:
- `getGroupsLocal()`, `getUsersLocal()` - получение списков
- `createGroupLocal()`, `createUserLocal()` - создание сущностей
- `updateGroupLocal()`, `updateUserLocal()` - обновление сущностей
- `deleteGroupLocal()`, `deleteUserLocal()` - удаление сущностей

**Внутренние методы**:
- `updateRecurringTasksIfNeeded()` - обновление повторяющихся задач
- `addToSyncQueue()` - добавление операций в очередь синхронизации

**Зависимости**:
- `OfflineRepository.saveTasksToCache()` → `loadTasksFromCache()` → `getTaskFromCache()` → `deleteTaskFromCache()` → `addToSyncQueue()` → `requestSync = true`
- `TaskDateCalculator.shouldRecalculateTask()`

---

### GroupsLocalApi.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/api/GroupsLocalApi.kt`
**Ответственность**: Локальный API для групп

**Методы**:
- `getGroupsLocal()` - получение групп из кэша
- `createGroupLocal()`, `updateGroupLocal()` - создание и обновление групп
- `deleteGroupLocal()` - удаление групп
- `addGroupToSyncQueue()` - добавление операций в очередь синхронизации

**Зависимости**:
- `OfflineRepository` (методы для групп)

---

### UsersLocalApi.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/api/UsersLocalApi.kt`
**Ответственность**: Локальный API для пользователей

**Методы**:
- `getUsersLocal()` - получение пользователей из кэша
- `createUserLocal()`, `updateUserLocal()` - создание и обновление пользователей
- `deleteUserLocal()` - удаление пользователей
- `addUserToSyncQueue()` - добавление операций в очередь синхронизации

**Зависимости**:
- `OfflineRepository` (методы для пользователей)

---

### OfflineRepository.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/repository/OfflineRepository.kt`
**Ответственность**: Низкоуровневая работа с Room database

**Операции с задачами**:
- `saveTasksToCache()`, `loadTasksFromCache()` - сохранение и загрузка задач
- `getTaskFromCache()`, `deleteTaskFromCache()` - операции с отдельными задачами

**Очередь синхронизации**:
- `addToSyncQueue()` - добавление операций
- `getPendingQueueItems()`, `clearAllQueue()` - управление очередью

**Пересчет задач**:
- `updateRecurringTasksForNewDay()` - обновление повторяющихся задач

**Группы и пользователи**:
- Методы для кэширования групп и пользователей аналогично задачам

**Флаги синхронизации**:
- `requestSync` - флаг необходимости синхронизации

**Зависимости**:
- `TaskCacheDao` - операции с задачами
- `SyncQueueDao` - операции с очередью
- `MetadataDao` - метаданные

---

## 4. Network Layer

### TaskSyncManager.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/services/TaskSyncManager.kt`
**Ответственность**: Синхронизация задач между локальным кэшем и сервером

**Основные методы**:
- `syncTasksServer()` - синхронизация задач с сервером
- `syncGroupsAndUsersServer()` - синхронизация групп и пользователей
- `performFullSync()` - полная синхронизация всех данных
- `observeSyncRequests()` - наблюдение за запросами синхронизации

**Внутренние методы**:
- `sendQueueToServer()` - отправка очереди операций
- `updateLocalCache()` - обновление локального кэша
- `validateTasksBeforeSync()` - валидация перед синхронизацией

**Зависимости**:
- `ServerApi.syncQueueServer()` → `getTasksServer()`
- `OfflineRepository.getPendingQueueItems()` → `clearAllQueue()` → `saveTasksToCache()`
- `SyncService.sendOperations()`
- `TaskValidationService.validateTaskBeforeSend()`
- `observeSyncRequests()` - автоматическая синхронизация при изменениях данных

---

### SyncService.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/services/SyncService.kt`
**Ответственность**: Управление очередью синхронизации

**Методы управления очередью**:
- `sendOperations()` - отправка операций на сервер
- `addOperationToQueue()` - добавление операций в очередь
- `getPendingOperations()` - получение ожидающих операций
- `clearProcessedOperations()` - очистка обработанных операций

**Логика повторных попыток**:
- `retryWithBackoff()` - повтор с экспоненциальной задержкой
- `shouldRetry()`, `calculateBackoffDelay()` - вспомогательные методы

**Зависимости**:
- `OfflineRepository.addToSyncQueue()` → `getPendingQueueItems()` → `clearAllQueue()`

---

### ServerApi.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/api/ServerApi.kt`
**Ответственность**: HTTP запросы к REST API сервера

**HTTP методы для задач**:
- `getTasksServer()` - получение задач с сервера
- `createTaskServer()`, `updateTaskServer()` - создание и обновление задач
- `completeTaskServer()`, `uncompleteTaskServer()` - управление статусом выполнения
- `deleteTaskServer()` - удаление задач
- `syncQueueServer()` - синхронизация очереди операций

**HTTP методы для групп и пользователей**:
- Аналогичные методы для групп и пользователей

**Внутренние методы**:
- `executeRequest()` - выполнение HTTP запросов
- `buildRequest()` - построение запросов
- `parseResponse()` - парсинг JSON ответов

**Зависимости**: OkHttpClient для HTTP запросов

---

### GroupsServerApi.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/api/GroupsServerApi.kt`
**Ответственность**: API для групп (сервер)

**HTTP методы для групп**:
- `getGroupsServer()` - получение групп с сервера
- `createGroupServer()`, `updateGroupServer()` - создание и обновление групп
- `deleteGroupServer()` - удаление групп
- `executeGroupRequest()` - выполнение HTTP запросов для групп

**Зависимости**: OkHttpClient

---

### UsersServerApi.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/api/UsersServerApi.kt`
**Ответственность**: API для пользователей (сервер)

**HTTP методы для пользователей**:
- `getUsersServer()` - получение пользователей с сервера
- `createUserServer()`, `updateUserServer()` - создание и обновление пользователей
- `deleteUserServer()` - удаление пользователей
- `executeUserRequest()` - выполнение HTTP запросов для пользователей

**Зависимости**: OkHttpClient

---

### TaskValidationService.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/services/TaskValidationService.kt`
**Ответственность**: Валидация данных задач перед отправкой

**Методы валидации**:
- `validateTaskBeforeSend()` - общая валидация задачи перед отправкой
- `validateTaskFields()` - валидация полей задачи
- `validateBusinessRules()` - валидация бизнес-правил

**Классы данных**:
- `ValidationResult` - результат валидации
- `ValidationError` - описание ошибки валидации

**Вспомогательные методы**:
- `validateTitle()`, `validateDescription()`, `validateReminderTime()` - валидация отдельных полей
- `validateBusinessLogic()` - валидация бизнес-логики

**Зависимости**: Нет

---

### ConnectionStatusManager.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/services/ConnectionStatusManager.kt`
**Ответственность**: Определение состояния сетевого соединения

**Методы управления статусом соединения**:
- `getConnectionStatus()` - получение текущего статуса соединения
- `updateConnectionStatusFromResponse()` - обновление статуса по HTTP ответу
- `updateConnectionStatusFromError()` - обновление статуса по ошибке
- `isCriticalError()`, `shouldRetry()` - анализ ошибок

**Вспомогательные методы**:
- `analyzeHttpResponse()`, `analyzeNetworkError()` - анализ ответов и ошибок
- `updateLastSuccessfulRequest()` - обновление времени последнего успешного запроса

**Перечисление статусов**:
- `ConnectionStatus` - UNKNOWN, ONLINE, DEGRADED, OFFLINE

**Зависимости**: Нет (работает с HTTP ответами)

---

## 5. Logging Layer

### BinaryLogger.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/debug/BinaryLogger.kt`
**Ответственность**: Бинарное логирование

**Методы логирования**:
- `logEvent()`, `logError()`, `logNetworkRequest()`, `logTaskOperation()` - различные типы логирования
- `flushLogs()` - отправка логов
- `getLogLevel()`, `setLogLevel()` - управление уровнем логирования

**Вспомогательные методы**:
- `createLogEntry()` - создание записи лога
- `shouldLog()` - проверка необходимости логирования

**Зависимости**:
- `BinaryLogStorage.saveLogEntry()`
- `BinaryLogStorage.flushLogs()`

---

### BinaryLogStorage.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/debug/BinaryLogStorage.kt`
**Ответственность**: Хранилище бинарных логов

**Методы управления хранилищем логов**:
- `saveLogEntry()`, `getPendingLogs()` - сохранение и получение логов
- `clearSentLogs()`, `cleanupOldLogs()` - очистка отправленных и старых логов
- `getLogFileSize()` - получение размера файла логов

**Вспомогательные методы**:
- `encodeEntry()` - кодирование записей
- `writeToFile()`, `readFromFile()` - работа с файлами

**Зависимости**: Android Context (file system operations)

---

### ChunkSender.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/debug/ChunkSender.kt`
**Ответственность**: Отправка чанков логов на сервер

**Методы отправки чанков**:
- `sendPendingChunks()` - отправка ожидающих чанков
- `sendChunk()` - отправка конкретного чанка
- `retryFailedChunks()` - повторная отправка неудачных чанков

**Вспомогательные методы**:
- `createChunk()` - создание чанка из логов
- `compressChunk()` - сжатие чанка
- `handleSendResult()` - обработка результата отправки

**Зависимости**:
- `BinaryLogStorage.getPendingLogs()`
- `BinaryLogStorage.clearSentLogs()`
- OkHttpClient

---

### LogCleanupManager.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/debug/LogCleanupManager.kt`
**Ответственность**: Очистка старых логов

**Методы очистки логов**:
- `cleanupOldLogs()`, `cleanupSentLogs()` - очистка старых и отправленных логов
- `schedulePeriodicCleanup()` - планирование периодической очистки

**Вспомогательные методы**:
- `calculateCleanupThreshold()` - расчет порога очистки
- `shouldCleanup()` - проверка необходимости очистки
- `cancelScheduledCleanup()` - отмена запланированной очистки

**Зависимости**:
- `BinaryLogStorage.cleanupOldLogs()`
- `BinaryLogStorage.clearSentLogs()`
- Android Context (для scheduling)

---

## Data Classes и Models

### Task.kt
```kotlin
data class Task(
    val id: Int,
    val title: String,
    val description: String?,
    val taskType: String,
    val recurrenceType: String?,
    val recurrenceInterval: Int?,
    val reminderTime: String,
    val groupId: Int?,
    val active: Boolean,
    val completed: Boolean,
    val assignedUserIds: List<Int>,
    val updatedAt: Long,
    val lastAccessed: Long
)
```

### SyncQueueItem.kt
```kotlin
data class SyncQueueItem(
    val id: Long,
    val operation: String,
    val entityType: String,
    val entityId: Int?,
    val payload: String?,
    val timestamp: Long,
    val retryCount: Int,
    val lastRetry: Long?,
    val status: String,
    val sizeBytes: Long
)
```

### Group.kt
```kotlin
data class Group(
    val id: Int,
    val name: String,
    val description: String?,
    val createdBy: Int,
    val updatedAt: Long
)
```

### User.kt
```kotlin
data class User(
    val id: Int,
    val name: String,
    val email: String,
    val role: String,
    val status: String,
    val updatedAt: Long
)
```

### TaskFilterType.kt
```kotlin
enum class TaskFilterType {
    TODAY, ALL, COMPLETED, PENDING
}
```

---

**Последнее обновление**: 2026-01-03 16:20 UTC
**Создатель**: AI Assistant