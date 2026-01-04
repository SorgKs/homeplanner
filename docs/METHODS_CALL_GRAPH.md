# Граф вызовов методов Android приложения

Формат для каждого метода:
```
MethodName()
├── Вызывается:
│   ├── Caller1.Method1()
│   ├── Caller2.Method2()
│   └── ...
└── Вызывает:
    ├── Callee1.Method1()
    ├── Callee2.Method2()
    └── ...
```

## 0. Application Level

Application.onCreate()
├── Вызывается: Android System
└── Вызывает:
    ├── initializeDependencies() (DI container)
    ├── createTaskViewModel()
    ├── createTaskSyncManager()
    └── BinaryLogger.initialize()

## 1. UI Layer

### MainActivity

onCreate()
├── Вызывается: Android System
└── Вызывает:
    ├── getTaskViewModel() (из DI/Application)
    ├── setupComposeContent()
    └── TaskSyncManager.observeSyncRequests() (если нужно)

onResume()
├── Вызывается: Android System
└── Вызывает: нет

onPause()
├── Вызывается: Android System
└── Вызывает: нет

onDestroy()
├── Вызывается: Android System
└── Вызывает: нет

navigateToTodayTab()
├── Вызывается: handleUiEvents()
└── Вызывает: Compose Navigation API

navigateToAllTasksTab()
├── Вызывается: handleUiEvents()
└── Вызывает: Compose Navigation API

navigateToSettingsTab()
├── Вызывается: handleUiEvents()
└── Вызывает: Compose Navigation API

setupComposeContent()
├── Вызывается: onCreate()
└── Вызывает: Compose.setContent()

observeTaskState()
├── Вызывается: Compose.collectAsState()
└── Вызывает: нет

handleUiEvents()
├── Вызывается:
│   ├── TaskListScreen.onComplete()
│   ├── TaskListScreen.onDelete()
│   ├── CreateTaskDialog.onConfirm()
│   ├── EditTaskDialog.onConfirm()
│   └── DeleteTaskDialog.onConfirm()
└── Вызывает:
    ├── navigateToTodayTab()
    ├── navigateToAllTasksTab()
    └── navigateToSettingsTab()

### MainScreen

MainScreen()
├── Вызывается: MainActivity.setupComposeContent()
└── Вызывает:
    ├── NavHost()
    ├── NavigationBar()
    ├── TodayScreen()
    ├── AllTasksScreen()
    └── SettingsScreen()

### TodayScreen

TodayScreen()
├── Вызывается: NavHost composable
└── Вызывает:
    ├── TodayTaskFilter.filterTodayTasks()
    └── TaskListContent()

### AllTasksScreen

AllTasksScreen()
├── Вызывается: NavHost composable
└── Вызывает: TaskListContent()

### SettingsScreen

SettingsScreen()
├── Вызывается: NavHost composable
└── Вызывает:
    ├── NetworkSettings management
    ├── UserSettings management
    └── QR scanner dialogs

### TaskListContent

TaskListContent()
├── Вызывается:
│   ├── TodayScreen()
│   └── AllTasksScreen()
└── Вызывает:
    ├── TaskItem()
    ├── LoadingIndicator()
    ├── ErrorMessage()
    └── EmptyState()

LoadingIndicator()
├── Вызывается: TaskListContent()
└── Вызывает: нет

ErrorMessage()
├── Вызывается: TaskListContent()
└── Вызывает: нет

EmptyState()
├── Вызывается: TaskListContent()
└── Вызывает: нет

// Колбеки для TaskItem
onComplete()
├── Вызывается: TaskItem onComplete parameter (from TaskListContent)
└── Вызывает: LocalApi.completeTaskLocal()

onDelete()
├── Вызывается: TaskItem onDelete parameter (from TaskListContent)
└── Вызывает: LocalApi.deleteTaskLocal()

onCreateTask()
├── Вызывается: EmptyState button click
└── Вызывает: MainActivity.showCreateTaskDialog()

### TaskItem

TaskItemContent()
├── Вызывается: TaskItem()
└── Вызывает:
    ├── SwipeActions()
    └── TaskContent()

SwipeActions()
├── Вызывается: TaskItemContent()
└── Вызывает: нет

TaskContent()
├── Вызывается: TaskItemContent()
└── Вызывает:
    ├── TaskTitle()
    ├── TaskDescription()
    └── TaskTime()

TaskTitle()
├── Вызывается: TaskContent()
└── Вызывает: нет

TaskDescription()
├── Вызывается: TaskContent()
└── Вызывает: нет

TaskTime()
├── Вызывается: TaskContent()
└── Вызывает: нет

### TaskDialogs

CreateTaskDialog()
├── Вызывается: TaskListScreen.onCreateTask()
└── Вызывает: CreateTaskDialog.onConfirm()

EditTaskDialog()
├── Вызывается: TaskItem.onClick()
└── Вызывает: EditTaskDialog.onConfirm()

DeleteTaskDialog()
├── Вызывается: TaskItem.onDelete()
└── Вызывает: DeleteTaskDialog.onConfirm()

// Колбеки для диалогов
CreateTaskDialog.onConfirm()
├── Вызывается: User confirms in CreateTaskDialog
└── Вызывает: LocalApi.createTaskLocal()

EditTaskDialog.onConfirm()
├── Вызывается: User confirms in EditTaskDialog
└── Вызывает: LocalApi.updateTaskLocal()

DeleteTaskDialog.onConfirm()
├── Вызывается: User confirms in DeleteTaskDialog
└── Вызывает: LocalApi.deleteTaskLocal()

## 2. Business Logic Layer

### TaskViewModel

initialize()
├── Вызывается: Application.createTaskViewModel()
└── Вызывает: loadInitialData()

getFilteredTasks()
├── Вызывается:
│   ├── TodayTasksList()
│   └── AllTasksList()
└── Вызывает: TaskFilter.filterTasks()

updateState()
├── Вызывается:
│   ├── initialize()
│   ├── loadInitialData()
│   └── handleError()
└── Вызывает: MutableStateFlow.update()

handleError()
├── Вызывается: suspend functions on exception
└── Вызывает:
    ├── updateState()
    └── BinaryLogger.logError()

loadInitialData()
├── Вызывается: initialize()
└── Вызывает:
    ├── LocalApi.getTasksLocal()
    ├── LocalApi.getGroupsLocal()
    └── LocalApi.getUsersLocal()

### TaskFilter

filterTasks()
├── Вызывается: TaskViewModel.getFilteredTasks()
└── Вызывает:
    ├── filterByUser()
    ├── filterByTime()
    ├── filterByCompletion()
    └── isTaskVisibleToday()

isTaskVisibleToday()
├── Вызывается: filterTasks()
└── Вызывает: getDayStartTime()

getDayStartTime()
├── Вызывается: isTaskVisibleToday()
└── Вызывает: TaskDateCalculator.getDayStart()

filterByUser()
├── Вызывается: filterTasks()
└── Вызывает: нет

filterByTime()
├── Вызывается: filterTasks()
└── Вызывает: нет

filterByCompletion()
├── Вызывается: filterTasks()
└── Вызывает: нет

### TaskDateCalculator

getDayStart()
├── Вызывается: TaskFilter.getDayStartTime()
└── Вызывает: нет

isNewDay()
├── Вызывается: LocalApi.updateRecurringTasksIfNeeded()
└── Вызывает: нет

calculateNextReminderTime()
├── Вызывается: ReminderScheduler.calculateTriggerTime()
└── Вызывает: getNextOccurrence()

shouldRecalculateTask()
├── Вызывается: LocalApi.updateRecurringTasksIfNeeded()
└── Вызывает: нет

getNextOccurrence()
├── Вызывается: calculateNextReminderTime()
└── Вызывает: adjustForDayStart()

adjustForDayStart()
├── Вызывается: getNextOccurrence()
└── Вызывает: нет

### ReminderScheduler

scheduleForTasks()
├── Вызывается: TaskViewModel.loadInitialData()
└── Вызывает: scheduleForTaskIfUpcoming()

scheduleForTaskIfUpcoming()
├── Вызывается: scheduleForTasks()
└── Вызывает:
    ├── createPendingIntent()
    ├── calculateTriggerTime()
    └── shouldScheduleNotification()

cancelForTask()
├── Вызывается: LocalApi.deleteTaskLocal()
└── Вызывает: AlarmManager.cancel()

cancelAll()
├── Вызывается: MainActivity.onDestroy()
└── Вызывает: AlarmManager.cancelAll()

updateNotificationSettings()
├── Вызывается: UI settings change
└── Вызывает: нет

createPendingIntent()
├── Вызывается: scheduleForTaskIfUpcoming()
└── Вызывает: PendingIntent.getBroadcast()

calculateTriggerTime()
├── Вызывается: scheduleForTaskIfUpcoming()
└── Вызывает: TaskDateCalculator.calculateNextReminderTime()

shouldScheduleNotification()
├── Вызывается: scheduleForTaskIfUpcoming()
└── Вызывает: нет

## 3. Data Layer

### LocalApi

getTasksLocal()
├── Вызывается:
│   ├── TaskViewModel.loadInitialData()
│   └── UI Components
└── Вызывает:
    ├── updateRecurringTasksIfNeeded()
    └── OfflineRepository.loadTasksFromCache()

createTaskLocal()
├── Вызывается: CreateTaskDialog.onConfirm()
└── Вызывает:
    ├── OfflineRepository.saveTasksToCache()
    ├── addToSyncQueue()
    └── requestSync = true

updateTaskLocal()
├── Вызывается: EditTaskDialog.onConfirm()
└── Вызывает:
    ├── OfflineRepository.saveTasksToCache()
    ├── addToSyncQueue()
    └── requestSync = true

completeTaskLocal()
├── Вызывается: TaskListScreen.onComplete()
└── Вызывает:
    ├── OfflineRepository.saveTasksToCache()
    ├── addToSyncQueue()
    └── requestSync = true

uncompleteTaskLocal()
├── Вызывается: TaskItem.onUncomplete()
└── Вызывает:
    ├── OfflineRepository.saveTasksToCache()
    ├── addToSyncQueue()
    └── requestSync = true

deleteTaskLocal()
├── Вызывается: TaskListScreen.onDelete()
└── Вызывает:
    ├── OfflineRepository.deleteTaskFromCache()
    ├── addToSyncQueue()
    └── requestSync = true

getGroupsLocal()
├── Вызывается: TaskViewModel.loadInitialData()
└── Вызывает: OfflineRepository.loadGroupsFromCache()

createGroupLocal()
├── Вызывается: UI Components
└── Вызывает:
    ├── OfflineRepository.saveGroupsToCache()
    └── addToSyncQueue()

updateGroupLocal()
├── Вызывается: UI Components
└── Вызывает:
    ├── OfflineRepository.saveGroupsToCache()
    └── addToSyncQueue()

deleteGroupLocal()
├── Вызывается: UI Components
└── Вызывает:
    ├── OfflineRepository.deleteGroupFromCache()
    └── addToSyncQueue()

getUsersLocal()
├── Вызывается: TaskViewModel.loadInitialData()
└── Вызывает: OfflineRepository.loadUsersFromCache()

createUserLocal()
├── Вызывается: UI Components
└── Вызывает:
    ├── OfflineRepository.saveUsersToCache()
    └── addToSyncQueue()

updateUserLocal()
├── Вызывается: UI Components
└── Вызывает:
    ├── OfflineRepository.saveUsersToCache()
    └── addToSyncQueue()

deleteUserLocal()
├── Вызывается: UI Components
└── Вызывает:
    ├── OfflineRepository.deleteUserFromCache()
    └── addToSyncQueue()

updateRecurringTasksIfNeeded()
├── Вызывается: getTasksLocal()
└── Вызывает:
    ├── TaskDateCalculator.isNewDay()
    ├── TaskDateCalculator.shouldRecalculateTask()
    └── OfflineRepository.updateRecurringTasksForNewDay()

addToSyncQueue()
├── Вызывается: all CRUD methods
└── Вызывает: OfflineRepository.addToSyncQueue()

### OfflineRepository

saveTasksToCache()
├── Вызывается: LocalApi.create/update/complete/uncomplete()
└── Вызывает: TaskCacheDao.insert()

loadTasksFromCache()
├── Вызывается: LocalApi.getTasksLocal()
└── Вызывает: TaskCacheDao.getAll()

getTaskFromCache()
├── Вызывается: LocalApi methods by ID
└── Вызывает: TaskCacheDao.getById()

deleteTaskFromCache()
├── Вызывается: LocalApi.deleteTaskLocal()
└── Вызывает: TaskCacheDao.delete()

addToSyncQueue()
├── Вызывается: LocalApi.addToSyncQueue()
└── Вызывает: SyncQueueDao.insert()

getPendingQueueItems()
├── Вызывается: TaskSyncManager.sendQueueToServer()
└── Вызывает: SyncQueueDao.getPending()

clearAllQueue()
├── Вызывается: TaskSyncManager.updateLocalCache()
└── Вызывает: SyncQueueDao.clearAll()

updateRecurringTasksForNewDay()
├── Вызывается: LocalApi.updateRecurringTasksIfNeeded()
└── Вызывает: TaskCacheDao.updateRecurring()

saveGroupsToCache()
├── Вызывается: LocalApi.create/update group
└── Вызывает: GroupDao.insert()

loadGroupsFromCache()
├── Вызывается: LocalApi.getGroupsLocal()
└── Вызывает: GroupDao.getAll()

getGroupFromCache()
├── Вызывается: LocalApi methods by ID
└── Вызывает: GroupDao.getById()

deleteGroupFromCache()
├── Вызывается: LocalApi.deleteGroupLocal()
└── Вызывает: GroupDao.delete()

saveUsersToCache()
├── Вызывается: LocalApi.create/update user
└── Вызывает: UserDao.insert()

loadUsersFromCache()
├── Вызывается: LocalApi.getUsersLocal()
└── Вызывает: UserDao.getAll()

getUserFromCache()
├── Вызывается: LocalApi methods by ID
└── Вызывает: UserDao.getById()

deleteUserFromCache()
├── Вызывается: LocalApi.deleteUserLocal()
└── Вызывает: UserDao.delete()

## 4. Network Layer

### TaskSyncManager

syncTasksServer()
├── Вызывается: observeSyncRequests()
└── Вызывает:
    ├── sendQueueToServer()
    └── updateLocalCache()

syncGroupsAndUsersServer()
├── Вызывается: performFullSync()
└── Вызывает:
    ├── GroupsServerApi.getAll()
    ├── UsersServerApi.getUsers()
    └── OfflineRepository.saveGroups/UsersToCache()

performFullSync()
├── Вызывается: syncTasksServer()
└── Вызывает: syncGroupsAndUsersServer()

observeSyncRequests()
├── Вызывается: Application.createTaskSyncManager()
└── Вызывает: syncTasksServer() when requestSync == true

sendQueueToServer()
├── Вызывается: syncTasksServer()
└── Вызывает:
    ├── SyncService.syncQueue()
    └── validateTasksBeforeSync()

updateLocalCache()
├── Вызывается: syncTasksServer()
└── Вызывает:
    ├── OfflineRepository.saveTasksToCache()
    └── OfflineRepository.clearAllQueue()

validateTasksBeforeSync()
├── Вызывается: sendQueueToServer()
└── Вызывает: TaskValidationService.validateTaskBeforeSend()

### SyncService

syncQueue()
├── Вызывается: TaskSyncManager.sendQueueToServer()
└── Вызывает: ServerApi.syncQueueServer()

syncStateBeforeRecalculation()
├── Вызывается: LocalApi.updateRecurringTasksIfNeeded()
└── Вызывает: syncQueue()

syncCacheWithServer()
├── Вызывается: TaskSyncManager.performFullSync()
└── Вызывает: calculateTasksHash()

calculateTasksHash()
├── Вызывается: syncCacheWithServer()
└── Вызывает: нет

### ServerApi

getTasksServer()
├── Вызывается:
│   ├── TaskSyncManager.syncTasksServer()
│   └── SyncService.syncCacheWithServer()
└── Вызывает:
    ├── buildRequest()
    ├── executeRequest()
    └── parseResponse()

createTaskServer()
├── Вызывается: syncQueue processing
└── Вызывает:
    ├── buildRequest()
    ├── executeRequest()
    └── parseResponse()

updateTaskServer()
├── Вызывается: syncQueue processing
└── Вызывает:
    ├── buildRequest()
    ├── executeRequest()
    └── parseResponse()

completeTaskServer()
├── Вызывается: syncQueue processing
└── Вызывает:
    ├── buildRequest()
    ├── executeRequest()
    └── parseResponse()

uncompleteTaskServer()
├── Вызывается: syncQueue processing
└── Вызывает:
    ├── buildRequest()
    ├── executeRequest()
    └── parseResponse()

deleteTaskServer()
├── Вызывается: syncQueue processing
└── Вызывает:
    ├── buildRequest()
    └── executeRequest()

syncQueueServer()
├── Вызывается: SyncService.syncQueue()
└── Вызывает:
    ├── buildRequest()
    ├── executeRequest()
    └── parseResponse()

getGroupsServer()
├── Вызывается: TaskSyncManager.syncGroupsAndUsersServer()
└── Вызывает:
    ├── buildRequest()
    ├── executeRequest()
    └── parseResponse()

createGroupServer()
├── Вызывается: syncQueue processing
└── Вызывает:
    ├── buildRequest()
    ├── executeRequest()
    └── parseResponse()

updateGroupServer()
├── Вызывается: syncQueue processing
└── Вызывает:
    ├── buildRequest()
    ├── executeRequest()
    └── parseResponse()

deleteGroupServer()
├── Вызывается: syncQueue processing
└── Вызывает:
    ├── buildRequest()
    └── executeRequest()

getUsersServer()
├── Вызывается: TaskSyncManager.syncGroupsAndUsersServer()
└── Вызывает:
    ├── buildRequest()
    ├── executeRequest()
    └── parseResponse()

createUserServer()
├── Вызывается: syncQueue processing
└── Вызывает:
    ├── buildRequest()
    ├── executeRequest()
    └── parseResponse()

updateUserServer()
├── Вызывается: syncQueue processing
└── Вызывает:
    ├── buildRequest()
    ├── executeRequest()
    └── parseResponse()

deleteUserServer()
├── Вызывается: syncQueue processing
└── Вызывает:
    ├── buildRequest()
    └── executeRequest()

executeRequest()
├── Вызывается: all ServerApi methods
└── Вызывает: OkHttpClient.newCall().execute()

buildRequest()
├── Вызывается: executeRequest()
└── Вызывает: Request.Builder

parseResponse()
├── Вызывается: executeRequest()
└── Вызывает: JSON deserialization

## 5. Logging Layer

### BinaryLogger

logEvent()
├── Вызывается: All components when logging needed
└── Вызывает: createLogEntry()

logError()
├── Вызывается:
│   ├── TaskViewModel.handleError()
│   └── Various components on exceptions
└── Вызывает: createLogEntry()

logNetworkRequest()
├── Вызывается: ServerApi methods
└── Вызывает: createLogEntry()

logTaskOperation()
├── Вызывается: LocalApi CRUD methods
└── Вызывает: createLogEntry()

flushLogs()
├── Вызывается: Periodic/On demand
└── Вызывает: BinaryLogStorage.saveLogEntry()

getLogLevel()
├── Вызывается: When checking log level
└── Вызывает: нет

setLogLevel()
├── Вызывается: On settings change
└── Вызывает: нет

createLogEntry()
├── Вызывается: All log methods
└── Вызывает: BinaryLogStorage.saveLogEntry()

### BinaryLogStorage

saveLogEntry()
├── Вызывается: BinaryLogger.createLogEntry()
└── Вызывает: encodeEntry()

getPendingLogs()
├── Вызывается: ChunkSender.sendPendingChunks()
└── Вызывает: readFromFile()

clearSentLogs()
├── Вызывается: ChunkSender.handleSendResult()
└── Вызывает: File.delete()

cleanupOldLogs()
├── Вызывается: LogCleanupManager.cleanupOldLogs()
└── Вызывает: File.listFiles()

getLogFileSize()
├── Вызывается: On size check
└── Вызывает: File.length()

encodeEntry()
├── Вызывается: saveLogEntry()
└── Вызывает: writeToFile()

writeToFile()
├── Вызывается: encodeEntry()
└── Вызывает: FileOutputStream.write()

readFromFile()
├── Вызывается: getPendingLogs()
└── Вызывает: FileInputStream.read()

### ChunkSender

sendPendingChunks()
├── Вызывается: Periodic
└── Вызывает:
    ├── getPendingLogs()
    ├── createChunk()
    └── sendChunk()

sendChunk()
├── Вызывается: sendPendingChunks()
└── Вызывает: handleSendResult()

retryFailedChunks()
├── Вызывается: On retry needed
└── Вызывает: sendChunk()

createChunk()
├── Вызывается: sendPendingChunks()
└── Вызывает: compressChunk()

compressChunk()
├── Вызывается: createChunk()
└── Вызывает: GZIPOutputStream

handleSendResult()
├── Вызывается: sendChunk()
└── Вызывает: clearSentLogs()

### LogCleanupManager

cleanupOldLogs()
├── Вызывается: Periodic
└── Вызывает:
    ├── calculateCleanupThreshold()
    ├── shouldCleanup()
    └── clearSentLogs()

cleanupSentLogs()
├── Вызывается: cleanupOldLogs()
└── Вызывает: BinaryLogStorage.clearSentLogs()

schedulePeriodicCleanup()
├── Вызывается: On initialization
└── Вызывает: AlarmManager.setRepeating()

calculateCleanupThreshold()
├── Вызывается: cleanupOldLogs()
└── Вызывает: Calendar.getTimeInMillis()

shouldCleanup()
├── Вызывается: calculateCleanupThreshold()
└── Вызывает: нет

cancelScheduledCleanup()
├── Вызывается: On shutdown
└── Вызывает: AlarmManager.cancel()

---

*Формат: MethodName() с двумя списками - Вызывается и Вызывает*
*Создано: 2025-12-30 09:50 UTC*
*Обновлено колбеки: 2025-12-30 20:08 UTC*
*Исправлена инициализация Network Layer: 2025-12-30 20:33 UTC*
*Обновлено для новой навигационной архитектуры: 2026-01-04 21:59 UTC*