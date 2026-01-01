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

```kotlin
class Application : android.app.Application() {

    override fun onCreate() {
        // Инициализация DI, создание компонентов всех слоёв
        initializeDependencies()      // DI container
        createTaskViewModel()         // Business Logic + Data Layer
        createTaskSyncManager()       // Network Layer
        BinaryLogger.initialize()     // Logging Layer
    }

    private fun initializeDependencies()    // Настройка DI framework
    private fun createTaskViewModel()       // Создание TaskViewModel с зависимостями
    private fun createTaskSyncManager()     // Создание TaskSyncManager с зависимостями
}
```

**Зависимости**: Android Application class

---

## 1. UI Layer

### MainActivity.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/MainActivity.kt`
**Ответственность**: Главный экран приложения, управление жизненным циклом

```kotlin
class MainActivity : ComponentActivity() {

    // === Жизненный цикл ===
    override fun onCreate(savedInstanceState: Bundle?)  // Вызывает: setupComposeContent()
    override fun onResume()                             // Нет вызовов
    override fun onPause()                              // Нет вызовов
    override fun onDestroy()                            // Нет вызовов

    // === Навигация ===
    private fun navigateToTodayTab()                    // Вызывается: handleUiEvents при выборе вкладки
    private fun navigateToAllTasksTab()                 // Вызывается: handleUiEvents при выборе вкладки
    private fun navigateToSettingsTab()                 // Вызывается: handleUiEvents при выборе вкладки

    // === Настройка UI ===
    private fun setupComposeContent()                   // Вызывается: onCreate, получает TaskViewModel из DI

    // === UI состояние ===
    private fun observeTaskState(state: TaskScreenState)  // Вызывается: Compose collectAsState
    private fun handleUiEvents(event: UiEvent)            // Вызывается: UI компонентами
}
```

**Зависимости**:
- `TaskViewModel.state` - наблюдение за состоянием через StateFlow (получается из DI)
- Compose Navigation API для навигации

---

### TaskListScreen.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/ui/tasks/TaskListScreen.kt`
**Ответственность**: Экран списка задач

```kotlin
@Composable
fun TaskListScreen(
    state: TaskScreenState,
    onTaskClick: (Task) -> Unit,
    onTaskComplete: (Int) -> Unit,
    onTaskDelete: (Int) -> Unit,
    onCreateTask: () -> Unit
) {
    @Composable private fun TaskListContent()
        // Вызывается: TaskListScreen, вызывает TaskItem для каждого элемента списка
    @Composable private fun TodayTasksList()
        // Вызывается: TaskListScreen для вкладки "Сегодня"
    @Composable private fun AllTasksList()
        // Вызывается: TaskListScreen для вкладки "Все задачи"
    @Composable private fun LoadingIndicator()
        // Вызывается: TaskListScreen при состоянии загрузки
    @Composable private fun ErrorMessage()
        // Вызывается: TaskListScreen при ошибке
    @Composable private fun EmptyState()
        // Вызывается: TaskListScreen при пустом списке
}
```

**Зависимости**: Нет (параметры передаются извне)

---

### TaskItem.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/ui/tasks/TaskItem.kt`
**Ответственность**: Элемент списка задач

```kotlin
@Composable
fun TaskItem(
    task: Task,
    onComplete: (Int) -> Unit,
    onClick: (Task) -> Unit,
    onDelete: (Int) -> Unit
) {
    @Composable private fun TaskItemContent()
        // Вызывается: TaskItem
    @Composable private fun SwipeActions()
        // Вызывается: TaskItemContent
    @Composable private fun TaskContent()
        // Вызывается: TaskItemContent
    @Composable private fun TaskTitle()
        // Вызывается: TaskContent
    @Composable private fun TaskDescription()
        // Вызывается: TaskContent
    @Composable private fun TaskTime()
        // Вызывается: TaskContent
}
```

**Зависимости**: Нет

---

### TaskDialogs.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/ui/tasks/TaskDialogs.kt`
**Ответственность**: Диалоги для работы с задачами

```kotlin
@Composable
fun CreateTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (Task) -> Unit
) {
    @Composable private fun DialogContent()
    @Composable private fun TaskForm()
}

@Composable
fun EditTaskDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirm: (Task) -> Unit
) {
    @Composable private fun DialogContent()
    @Composable private fun TaskForm()
}

@Composable
fun DeleteTaskDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    @Composable private fun DialogContent()
    @Composable private fun ConfirmationMessage()
}
```

**Зависимости**: Нет

---

## 2. Business Logic Layer

### TaskViewModel.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/viewmodel/TaskViewModel.kt`
**Ответственность**: Управление состоянием UI и координация бизнес-логики

```kotlin
class TaskViewModel(application: Application) : AndroidViewModel(application) {

    // === Инициализация ===
    fun initialize(
        networkConfig: NetworkConfig,
        apiBaseUrl: String,
        selectedUser: SelectedUser?
    )  // Инициализирует ViewModel с сетевыми настройками и базовым URL API. Вызывает loadInitialData() для загрузки начальных данных





    // === Фильтрация ===
    fun getFilteredTasks(tasks: List<Task>, filter: TaskFilterType): List<Task>
        // Фильтрует список задач по заданному типу фильтра (TODAY, ALL, COMPLETED, PENDING)



    // === Внутренние методы ===
    private fun updateState(newState: TaskScreenState)
        // Обновляет внутреннее состояние ViewModel новым TaskScreenState, уведомляя UI о изменениях
    private fun handleError(error: Throwable)
        // Обрабатывает исключение: обновляет состояние с сообщением об ошибке и логирует через BinaryLogger
    private suspend fun loadInitialData()
        // Загружает начальные данные приложения: задачи из локального хранилища и вспомогательные данные (группы, пользователи)
}
```

**Зависимости**:
- `TaskFilter.filterTasks()`

---

### TaskFilter.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/utils/TaskFilter.kt`
**Ответственность**: Универсальная фильтрация задач по различным критериям

```kotlin
object TaskFilter {

    fun filterTasks(
        tasks: List<Task>,
        filter: TaskFilterType,
        selectedUser: SelectedUser?,
        dayStartHour: Int
    ): List<Task>
        // Фильтрует задачи по заданному типу фильтра: TODAY, ALL, COMPLETED и т.д.

    fun isTaskVisibleToday(task: Task, dayStartHour: Int): Boolean
        // Определяет, должна ли задача быть видна сегодня на основе времени напоминания

    fun getDayStartTime(dayStartHour: Int): LocalDateTime
        // Вычисляет время начала дня на основе настроенного часа

    private fun filterByUser(tasks: List<Task>, selectedUser: SelectedUser?): List<Task>
        // Фильтрует задачи по назначенным пользователям
    private fun filterByTime(tasks: List<Task>, dayStartHour: Int): List<Task>
        // Фильтрует задачи по времени видимости сегодня
    private fun filterByCompletion(tasks: List<Task>): List<Task>
        // Фильтрует задачи по статусу выполнения
}
```

**Зависимости**:
- `TaskDateCalculator.getDayStart()`

---

### TaskDateCalculator.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/utils/TaskDateCalculator.kt`
**Ответственность**: Вычисление дат для задач

```kotlin
object TaskDateCalculator {

    fun getDayStart(time: LocalDateTime, dayStartHour: Int): LocalDateTime
        // Вычисляет время начала дня для заданного времени и часа начала дня
    fun isNewDay(lastUpdateMillis: Long?, nowMillis: Long, lastDayStartHour: Int?, currentDayStartHour: Int): Boolean
        // Определяет, наступил ли новый день с момента последнего обновления
    fun calculateNextReminderTime(task: Task, nowMillis: Long, dayStartHour: Int): String
        // Вычисляет время следующего напоминания для повторяющейся задачи
    fun shouldRecalculateTask(task: Task, dayStartHour: Int): Boolean
        // Проверяет, нужно ли пересчитать время следующего выполнения задачи

    private fun getNextOccurrence(task: Task, currentTime: LocalDateTime): LocalDateTime
        // Вычисляет следующее время выполнения для повторяющейся задачи
    private fun adjustForDayStart(time: LocalDateTime, dayStartHour: Int): LocalDateTime
        // Корректирует время с учётом настроенного часа начала дня
}
```

**Зависимости**: Нет

---

### ReminderScheduler.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/ReminderScheduler.kt`
**Ответственность**: Планирование уведомлений

```kotlin
object ReminderScheduler {

    fun scheduleForTasks(tasks: List<Task>)
        // Планирует уведомления для списка задач
    fun scheduleForTaskIfUpcoming(task: Task)
        // Планирует уведомление для задачи, если оно скоро наступит
    fun cancelForTask(task: Task)
        // Отменяет запланированное уведомление для задачи
    fun cancelAll(tasks: List<Task>)
        // Отменяет все запланированные уведомления для списка задач
    fun updateNotificationSettings(settings: NotificationSettings)
        // Обновляет настройки уведомлений

    private fun createPendingIntent(taskId: Int): PendingIntent
        // Создаёт PendingIntent для уведомления задачи
    private fun calculateTriggerTime(task: Task): Long
        // Вычисляет время срабатывания уведомления для задачи
    private fun shouldScheduleNotification(task: Task): Boolean
        // Определяет, нужно ли планировать уведомление для задачи
}
```

**Зависимости**: Android AlarmManager API

---

## 3. Data Layer

### LocalApi.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/api/LocalApi.kt`
**Ответственность**: Работа с локальным хранилищем для UI и Business Logic

```kotlin
class LocalApi(
    private val offlineRepository: OfflineRepository,
    private val taskDateCalculator: TaskDateCalculator
) {

    // === Задачи ===
    suspend fun getTasksLocal(activeOnly: Boolean = true): List<Task>
        // Возвращает список задач из локального кэша, опционально только активные
    suspend fun createTaskLocal(task: Task, assignedUserIds: List<Int> = emptyList()): Task
        // Создает новую задачу в локальном хранилище и добавляет в очередь синхронизации
    suspend fun updateTaskLocal(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Task
        // Обновляет существующую задачу в локальном хранилище и добавляет в очередь синхронизации
    suspend fun completeTaskLocal(taskId: Int): Task
        // Отмечает задачу как выполненную в локальном хранилище и добавляет в очередь синхронизации
    suspend fun uncompleteTaskLocal(taskId: Int): Task
        // Снимает отметку выполнения с задачи в локальном хранилище и добавляет в очередь синхронизации
    suspend fun deleteTaskLocal(taskId: Int)
        // Удаляет задачу из локального хранилища и добавляет в очередь синхронизации

    // === Группы ===
    suspend fun getGroupsLocal(): List<Group>
        // Возвращает список групп из локального кэша
    suspend fun createGroupLocal(group: Group): Group
        // Создает новую группу в локальном хранилище и добавляет в очередь синхронизации
    suspend fun updateGroupLocal(groupId: Int, group: Group): Group
        // Обновляет существующую группу в локальном хранилище и добавляет в очередь синхронизации
    suspend fun deleteGroupLocal(groupId: Int)
        // Удаляет группу из локального хранилища и добавляет в очередь синхронизации

    // === Пользователи ===
    suspend fun getUsersLocal(): List<User>
        // Возвращает список пользователей из локального кэша
    suspend fun createUserLocal(user: User): User
        // Создает нового пользователя в локальном хранилище и добавляет в очередь синхронизации
    suspend fun updateUserLocal(userId: Int, user: User): User
        // Обновляет существующего пользователя в локальном хранилище и добавляет в очередь синхронизации
    suspend fun deleteUserLocal(userId: Int)
        // Удаляет пользователя из локального хранилища и добавляет в очередь синхронизации

    // === Внутренние методы ===
    private suspend fun updateRecurringTasksIfNeeded(dayStartHour: Int)
        // Проверяет и обновляет повторяющиеся задачи если наступил новый день
    private suspend fun addToSyncQueue(action: String, entityType: String, entityId: Int?, entity: Any?)
        // Добавляет операцию в очередь синхронизации для последующей отправки на сервер
}
```

**Зависимости**:
- `OfflineRepository.saveTasksToCache()` → `loadTasksFromCache()` → `getTaskFromCache()` → `deleteTaskFromCache()` → `addToSyncQueue()` → `requestSync = true`
- `TaskDateCalculator.shouldRecalculateTask()`

---

### GroupsLocalApi.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/api/GroupsLocalApi.kt`
**Ответственность**: Локальный API для групп

```kotlin
class GroupsLocalApi(private val offlineRepository: OfflineRepository) {

    suspend fun getGroupsLocal(): List<Group>
    suspend fun createGroupLocal(group: Group): Group
    suspend fun updateGroupLocal(groupId: Int, group: Group): Group
    suspend fun deleteGroupLocal(groupId: Int)

    private suspend fun addGroupToSyncQueue(action: String, groupId: Int?, group: Group?)
}
```

**Зависимости**:
- `OfflineRepository` (методы для групп)

---

### UsersLocalApi.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/api/UsersLocalApi.kt`
**Ответственность**: Локальный API для пользователей

```kotlin
class UsersLocalApi(private val offlineRepository: OfflineRepository) {

    suspend fun getUsersLocal(): List<User>
    suspend fun createUserLocal(user: User): User
    suspend fun updateUserLocal(userId: Int, user: User): User
    suspend fun deleteUserLocal(userId: Int)

    private suspend fun addUserToSyncQueue(action: String, userId: Int?, user: User?)
}
```

**Зависимости**:
- `OfflineRepository` (методы для пользователей)

---

### OfflineRepository.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/repository/OfflineRepository.kt`
**Ответственность**: Низкоуровневая работа с Room database

```kotlin
class OfflineRepository(
    private val taskCacheDao: TaskCacheDao,
    private val syncQueueDao: SyncQueueDao,
    private val metadataDao: MetadataDao
) {

    // === Задачи ===
    suspend fun saveTasksToCache(tasks: List<Task>): Result<Unit>
        // Сохраняет список задач в локальный кэш базы данных
    suspend fun loadTasksFromCache(): List<Task>
        // Загружает все задачи из локального кэша
    suspend fun getTaskFromCache(id: Int): Task?
        // Получает задачу по ID из кэша
    suspend fun deleteTaskFromCache(id: Int)
        // Удаляет задачу из кэша по ID

    // === Очередь синхронизации ===
    suspend fun addToSyncQueue(action: String, entityType: String, entityId: Int?, entity: Any?)
        // Добавляет операцию в очередь для синхронизации с сервером
    suspend fun getPendingQueueItems(): List<SyncQueueItem>
        // Получает список ожидающих операций синхронизации
    suspend fun clearAllQueue()
        // Очищает очередь синхронизации после успешной отправки

    // === Пересчет задач ===
    suspend fun updateRecurringTasksForNewDay(dayStartHour: Int): Boolean
        // Обновляет повторяющиеся задачи при переходе на новый день

    // === Синхронизация ===
    var requestSync: Boolean = false
        // Флаг, сигнализирующий Network Layer о необходимости синхронизации после изменений данных

    // === Группы ===
    suspend fun saveGroupsToCache(groups: List<Group>)
        // Сохраняет список групп в локальный кэш
    suspend fun loadGroupsFromCache(): List<Group>
        // Загружает все группы из кэша
    suspend fun getGroupFromCache(id: Int): Group?
        // Получает группу по ID из кэша
    suspend fun deleteGroupFromCache(id: Int)
        // Удаляет группу из кэша по ID

    // === Пользователи ===
    suspend fun saveUsersToCache(users: List<User>)
        // Сохраняет список пользователей в локальный кэш
    suspend fun loadUsersFromCache(): List<User>
        // Загружает всех пользователей из кэша
    suspend fun getUserFromCache(id: Int): User?
        // Получает пользователя по ID из кэша
    suspend fun deleteUserFromCache(id: Int)
        // Удаляет пользователя из кэша по ID
}
```

**Зависимости**:
- `TaskCacheDao` - операции с задачами
- `SyncQueueDao` - операции с очередью
- `MetadataDao` - метаданные

---

## 4. Network Layer

### TaskSyncManager.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/services/TaskSyncManager.kt`
**Ответственность**: Синхронизация задач между локальным кэшем и сервером

```kotlin
class TaskSyncManager(
    private val serverApi: ServerApi,
    private val offlineRepository: OfflineRepository,
    private val syncService: SyncService,
    private val taskValidationService: TaskValidationService
) {

    suspend fun syncTasksServer(): Result<Unit>
        // Синхронизирует очередь операций задач с сервером и обновляет локальный кэш
    suspend fun syncGroupsAndUsersServer(): Result<Unit>
        // Синхронизирует группы и пользователей с сервером и обновляет локальный кэш
    suspend fun performFullSync(): Result<Unit>
        // Выполняет полную синхронизацию: задачи, группы и пользователи

    fun observeSyncRequests()
        // Автоматически наблюдает за флагом requestSync и запускает синхронизацию при изменениях

    private suspend fun sendQueueToServer(): Result<List<Task>>
        // Отправляет накопленную очередь операций на сервер и получает обновлённые данные
    private suspend fun updateLocalCache(serverTasks: List<Task>): Result<Unit>
        // Обновляет локальный кэш актуальными данными с сервера
    private suspend fun validateTasksBeforeSync(tasks: List<Task>): Result<Unit>
        // Проверяет корректность данных задач перед отправкой на сервер
}
```

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

```kotlin
class SyncService(private val offlineRepository: OfflineRepository) {

    suspend fun sendOperations(queueItems: List<SyncQueueItem>): Result<Unit>
    suspend fun addOperationToQueue(operation: SyncOperation): Result<Unit>
    suspend fun getPendingOperations(): List<SyncQueueItem>
    suspend fun clearProcessedOperations(): Result<Unit>

    private fun retryWithBackoff(operation: suspend () -> Result<Unit>): Result<Unit>
    private fun shouldRetry(attempt: Int, error: Throwable): Boolean
    private fun calculateBackoffDelay(attempt: Int): Long
}
```

**Зависимости**:
- `OfflineRepository.addToSyncQueue()` → `getPendingQueueItems()` → `clearAllQueue()`

---

### ServerApi.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/api/ServerApi.kt`
**Ответственность**: HTTP запросы к REST API сервера

```kotlin
class ServerApi(
    private val httpClient: OkHttpClient,
    private val baseUrl: String
) {

    // === Задачи ===
    suspend fun getTasksServer(activeOnly: Boolean = true): Result<List<Task>>
        // Вызывает: buildRequest(), executeRequest(), parseResponse()
    suspend fun createTaskServer(task: Task, assignedUserIds: List<Int> = emptyList()): Result<Task>
        // Вызывает: buildRequest(), executeRequest(), parseResponse()
    suspend fun updateTaskServer(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Result<Task>
        // Вызывает: buildRequest(), executeRequest(), parseResponse()
    suspend fun completeTaskServer(taskId: Int): Result<Task>
        // Вызывает: buildRequest(), executeRequest(), parseResponse()
    suspend fun uncompleteTaskServer(taskId: Int): Result<Task>
        // Вызывает: buildRequest(), executeRequest(), parseResponse()
    suspend fun deleteTaskServer(taskId: Int): Result<Unit>
        // Вызывает: buildRequest(), executeRequest()
    suspend fun syncQueueServer(queueItems: List<SyncQueueItem>): Result<List<Task>>
        // Вызывает: buildRequest(), executeRequest(), parseResponse()

    // === Группы ===
    suspend fun getGroupsServer(): Result<List<Group>>
        // Вызывает: buildRequest(), executeRequest(), parseResponse()
    suspend fun createGroupServer(group: Group): Result<Group>
        // Вызывает: buildRequest(), executeRequest(), parseResponse()
    suspend fun updateGroupServer(groupId: Int, group: Group): Result<Group>
        // Вызывает: buildRequest(), executeRequest(), parseResponse()
    suspend fun deleteGroupServer(groupId: Int): Result<Unit>
        // Вызывает: buildRequest(), executeRequest()

    // === Пользователи ===
    suspend fun getUsersServer(): Result<List<User>>
        // Вызывает: buildRequest(), executeRequest(), parseResponse()
    suspend fun createUserServer(user: User): Result<User>
        // Вызывает: buildRequest(), executeRequest(), parseResponse()
    suspend fun updateUserServer(userId: Int, user: User): Result<User>
        // Вызывает: buildRequest(), executeRequest(), parseResponse()
    suspend fun deleteUserServer(userId: Int): Result<Unit>
        // Вызывает: buildRequest(), executeRequest()

    // === Внутренние методы ===
    private suspend fun executeRequest(request: Request): Result<Response>
        // OkHttpClient.newCall().execute()
    private fun buildRequest(endpoint: String, method: String, body: RequestBody?): Request
        // OkHttp Request.Builder
    private fun parseResponse<T>(response: Response, clazz: Class<T>): Result<T>
        // JSON parsing with kotlinx.serialization
}
```

**Зависимости**: OkHttpClient для HTTP запросов

---

### GroupsServerApi.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/api/GroupsServerApi.kt`
**Ответственность**: API для групп (сервер)

```kotlin
class GroupsServerApi(
    private val httpClient: OkHttpClient,
    private val baseUrl: String
) {

    suspend fun getGroupsServer(): Result<List<Group>>
    suspend fun createGroupServer(group: Group): Result<Group>
    suspend fun updateGroupServer(groupId: Int, group: Group): Result<Group>
    suspend fun deleteGroupServer(groupId: Int): Result<Unit>

    private suspend fun executeGroupRequest(request: Request): Result<Response>
}
```

**Зависимости**: OkHttpClient

---

### UsersServerApi.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/api/UsersServerApi.kt`
**Ответственность**: API для пользователей (сервер)

```kotlin
class UsersServerApi(
    private val httpClient: OkHttpClient,
    private val baseUrl: String
) {

    suspend fun getUsersServer(): Result<List<User>>
    suspend fun createUserServer(user: User): Result<User>
    suspend fun updateUserServer(userId: Int, user: User): Result<User>
    suspend fun deleteUserServer(userId: Int): Result<Unit>

    private suspend fun executeUserRequest(request: Request): Result<Response>
}
```

**Зависимости**: OkHttpClient

---

### TaskValidationService.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/services/TaskValidationService.kt`
**Ответственность**: Валидация данных задач перед отправкой

```kotlin
class TaskValidationService {

    fun validateTaskBeforeSend(task: Task): ValidationResult
    fun validateTaskFields(task: Task): List<ValidationError>
    fun validateBusinessRules(task: Task): List<ValidationError>

    data class ValidationResult(val isValid: Boolean, val errors: List<ValidationError>)
    data class ValidationError(val field: String, val message: String)

    private fun validateTitle(title: String): ValidationError?
    private fun validateDescription(description: String?): ValidationError?
    private fun validateReminderTime(time: String): ValidationError?
    private fun validateBusinessLogic(task: Task): List<ValidationError>
}
```

**Зависимости**: Нет

---

### ConnectionStatusManager.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/services/ConnectionStatusManager.kt`
**Ответственность**: Определение состояния сетевого соединения

```kotlin
class ConnectionStatusManager {

    fun getConnectionStatus(): ConnectionStatus
    fun updateConnectionStatusFromResponse(response: HttpResponse): ConnectionStatus
    fun updateConnectionStatusFromError(error: Throwable): ConnectionStatus
    fun isCriticalError(error: Throwable): Boolean
    fun shouldRetry(error: Throwable): Boolean

    private fun analyzeHttpResponse(response: HttpResponse): ConnectionStatus
    private fun analyzeNetworkError(error: Throwable): ConnectionStatus
    private fun updateLastSuccessfulRequest()

    enum class ConnectionStatus { UNKNOWN, ONLINE, DEGRADED, OFFLINE }
}
```

**Зависимости**: Нет (работает с HTTP ответами)

---

## 5. Logging Layer

### BinaryLogger.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/debug/BinaryLogger.kt`
**Ответственность**: Бинарное логирование

```kotlin
class BinaryLogger(private val binaryLogStorage: BinaryLogStorage) {

    fun logEvent(event: LogEvent)
    fun logError(error: Throwable, context: String?)
    fun logNetworkRequest(request: HttpRequest, response: HttpResponse?)
    fun logTaskOperation(operation: String, taskId: Int, userId: Int?)

    suspend fun flushLogs(): Result<Unit>
    fun getLogLevel(): LogLevel
    fun setLogLevel(level: LogLevel)

    private fun createLogEntry(event: LogEvent): BinaryLogEntry
    private fun shouldLog(level: LogLevel): Boolean
}
```

**Зависимости**:
- `BinaryLogStorage.saveLogEntry()`
- `BinaryLogStorage.flushLogs()`

---

### BinaryLogStorage.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/debug/BinaryLogStorage.kt`
**Ответственность**: Хранилище бинарных логов

```kotlin
class BinaryLogStorage(private val context: Context) {

    suspend fun saveLogEntry(entry: BinaryLogEntry): Result<Unit>
    suspend fun getPendingLogs(): List<BinaryLogEntry>
    suspend fun clearSentLogs(logIds: List<Long>): Result<Unit>
    suspend fun cleanupOldLogs(olderThan: Long): Result<Unit>
    suspend fun getLogFileSize(): Long

    private fun encodeEntry(entry: BinaryLogEntry): ByteArray
    private fun writeToFile(data: ByteArray, file: File): Result<Unit>
    private fun readFromFile(file: File): Result<List<BinaryLogEntry>>
}
```

**Зависимости**: Android Context (file system operations)

---

### ChunkSender.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/debug/ChunkSender.kt`
**Ответственность**: Отправка чанков логов на сервер

```kotlin
class ChunkSender(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
    private val binaryLogStorage: BinaryLogStorage
) {

    suspend fun sendPendingChunks(): Result<Unit>
    suspend fun sendChunk(chunk: BinaryLogChunk): Result<Unit>
    suspend fun retryFailedChunks(): Result<Unit>

    private suspend fun createChunk(logs: List<BinaryLogEntry>): BinaryLogChunk
    private suspend fun compressChunk(chunk: BinaryLogChunk): ByteArray
    private fun handleSendResult(result: Result<Unit>, chunk: BinaryLogChunk)
}
```

**Зависимости**:
- `BinaryLogStorage.getPendingLogs()`
- `BinaryLogStorage.clearSentLogs()`
- OkHttpClient

---

### LogCleanupManager.kt
**Расположение**: `android/app/src/main/java/com/homeplanner/debug/LogCleanupManager.kt`
**Ответственность**: Очистка старых логов

```kotlin
class LogCleanupManager(
    private val binaryLogStorage: BinaryLogStorage,
    private val context: Context
) {

    suspend fun cleanupOldLogs(): Result<Unit>
    suspend fun cleanupSentLogs(): Result<Unit>
    fun schedulePeriodicCleanup()

    private fun calculateCleanupThreshold(): Long
    private fun shouldCleanup(): Boolean
    private fun cancelScheduledCleanup()
}
```

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

**Последнее обновление**: 2025-12-30 20:51 UTC
**Создатель**: AI Assistant