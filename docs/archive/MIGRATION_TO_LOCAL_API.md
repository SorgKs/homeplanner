# План миграции с TasksApiOffline на LocalApi

> **Статус:** ✅ **Завершена**  
> **Приоритет:** Высокий  
> **Связанные документы:** [OFFLINE_FIRST_ARCHITECTURE.md](OFFLINE_FIRST_ARCHITECTURE.md)

## Обзор

Данный документ описывает план миграции с текущей реализации `TasksApiOffline` на целевую архитектуру с разделением на `LocalApi`, `TasksApi` и `SyncService`.

## Текущее состояние

### Используемые компоненты

1. **TasksApi** ✅
   - Прямые HTTP-запросы к серверу
   - Расположение: `android/app/src/main/java/com/homeplanner/api/TasksApi.kt`
   - Статус: Реализован и работает корректно

2. **TasksApiOffline** ✅ (удалён)
   - Обёртка над `TasksApi` с логикой кэширования и синхронизации
   - Расположение: ~~`android/app/src/main/java/com/homeplanner/api/TasksApiOffline.kt`~~ (удалён)
   - Статус: Успешно мигрирован на `LocalApi`, файл удалён

3. **SyncService** ✅
   - Синхронизация очереди операций с сервером
   - Расположение: `android/app/src/main/java/com/homeplanner/sync/SyncService.kt`
   - Статус: Реализован, но используется только частично

4. **OfflineRepository** ✅
   - Низкоуровневая работа с Room database и очередью
   - Расположение: `android/app/src/main/java/com/homeplanner/repository/OfflineRepository.kt`
   - Статус: Реализован и работает корректно

5. **LocalApi** ✅
   - Работа с локальным хранилищем для UI
   - Расположение: `android/app/src/main/java/com/homeplanner/api/LocalApi.kt`
   - Статус: Реализован и используется в `MainActivity`

### ~~Где используется TasksApiOffline~~ (устарело)

- ~~`MainActivity.kt`~~ — ✅ Мигрировано на `LocalApi`
- ~~`TasksApiOfflineTest.kt`~~ — ✅ Тесты переписаны/удалены

### Проблемы текущего подхода

1. **Смешение ответственности:**
   - `TasksApiOffline` одновременно работает с кэшем и синхронизирует с сервером
   - Логика синхронизации разбросана между `TasksApiOffline` и `SyncService`
   - Сложно понять, где происходит работа с кэшем, а где с сервером

2. **Сложность тестирования:**
   - Невозможно протестировать работу с кэшем изолированно от синхронизации
   - Требуется мокирование множества зависимостей

3. **Дублирование логики:**
   - Синхронизация происходит и в `TasksApiOffline`, и в `SyncService`
   - Нет единой точки управления синхронизацией

4. **Сложность поддержки:**
   - Изменения в логике синхронизации требуют правок в нескольких местах
   - Неочевидно, где искать логику работы с кэшем

## Целевое состояние

### Архитектура компонентов

```
┌─────────────┐
│   MainActivity (UI) │
└──────┬──────┘
       │
       ├──► LocalApi (работа с кэшем для UI)
       │         │
       │         └──► OfflineRepository (Room DB)
       │
       └──► SyncService (синхронизация)
                 │
                 ├──► TasksApi (HTTP-запросы)
                 │
                 └──► OfflineRepository (очередь)
```

### Разделение ответственности

1. **LocalApi:**
   - Только работа с локальным хранилищем
   - Добавление операций в очередь синхронизации
   - Оптимистичные обновления
   - Не выполняет запросы к серверу

2. **TasksApi:**
   - Только HTTP-запросы к серверу
   - Парсинг JSON-ответов
   - Обработка HTTP-ошибок
   - Не содержит логики кэширования

3. **SyncService:**
   - Только синхронизация очереди с сервером
   - Загрузка данных с сервера и сохранение в кэш
   - Обработка конфликтов
   - Использует `TasksApi` для запросов

## План миграции

### Этап 1: Создание LocalApi

**Цель:** Создать новый класс `LocalApi` с логикой работы с кэшем из `TasksApiOffline`.

**Шаги:**

1. Создать файл `android/app/src/main/java/com/homeplanner/api/LocalApi.kt`

2. Реализовать методы на основе логики из `TasksApiOffline`:
   ```kotlin
   class LocalApi(
       private val offlineRepository: OfflineRepository
   ) {
       suspend fun getTasks(activeOnly: Boolean = true): List<Task>
       suspend fun createTask(task: Task, assignedUserIds: List<Int> = emptyList()): Task
       suspend fun updateTask(taskId: Int, task: Task, assignedUserIds: List<Int> = emptyList()): Task
       suspend fun completeTask(taskId: Int): Task
       suspend fun uncompleteTask(taskId: Int): Task
       suspend fun deleteTask(taskId: Int)
   }
   ```

3. **Важно:** Убрать всю логику синхронизации:
   - Не вызывать `syncService.syncQueue()` внутри методов
   - Не запускать фоновые корутины для синхронизации
   - Только работа с кэшем и добавление в очередь

4. Написать unit-тесты для `LocalApi`

**Критерии готовности:**
- ✅ Все методы реализованы
- ✅ Логика синхронизации удалена
- ✅ Тесты написаны и проходят
- ✅ Документация (KDoc) добавлена

---

### Этап 2: Обновление SyncService

**Цель:** Убедиться, что `SyncService` полностью покрывает всю логику синхронизации.

**Шаги:**

1. Проверить, что `SyncService.syncQueue()` обрабатывает все типы операций:
   - create
   - update
   - complete
   - uncomplete
   - delete

2. Добавить метод для синхронизации кэша с сервером в `SyncService`:
   ```kotlin
   suspend fun syncCacheWithServer(
       groupsApi: GroupsApi? = null,
       usersApi: UsersApi? = null
   ): Result<SyncCacheResult>
   
   data class SyncCacheResult(
       val cacheUpdated: Boolean,
       val users: List<UserSummary>? = null,
       val groups: Map<Int, String>? = null
   )
   ```
   
   **Алгоритм:**
   1. Проверка наличия интернета
   2. Запрос данных с сервера через `TasksApi.getTasks()`
      - Если запрос не удался → возврат ошибки (не тратим ресурсы на вычисление хеша кэша)
   3. Загрузка данных из кэша для сравнения
   4. Вычисление хеша кэшированных данных
   5. Вычисление хеша данных с сервера
   6. Сравнение хешей и обновление кэша при различиях
   7. Синхронизация очереди операций
   8. Опционально: загрузка групп и пользователей (если переданы API)
   9. Возврат результата
   
   **Оптимизация:** Сначала запрашиваем данные с сервера, и только если запрос успешен, вычисляем хеш кэша. Это позволяет избежать лишних вычислений при ошибках сети.

3. Убедиться, что обработка ошибок и конфликтов реализована

4. Написать/обновить тесты для `SyncService`

**Критерии готовности:**
- ✅ Все операции синхронизации обрабатываются
- ✅ Обработка ошибок реализована
- ✅ Тесты написаны и проходят

---

### Этап 3: Обновление MainActivity

**Цель:** Заменить использование `TasksApiOffline` на `LocalApi` + явные вызовы `SyncService`.

**Шаги:**

1. Создать экземпляр `LocalApi` в `MainActivity`:
   ```kotlin
   val localApi = remember(offlineRepository) {
       LocalApi(offlineRepository)
   }
   ```

2. Заменить все вызовы `tasksApiOffline.*` на `localApi.*`

3. Добавить явные вызовы `syncService.syncQueue()` после операций записи:
   ```kotlin
   // Пример для createTask
   scope.launch(Dispatchers.IO) {
       val newTask = localApi.createTask(task, assignedUserIds)
       // Явная синхронизация в фоне
       if (syncService.isOnline()) {
           syncService.syncQueue()
       }
   }
   ```

4. Обновить логику загрузки задач:
   ```kotlin
   suspend fun loadTasks() {
       // 1. Загружаем из кэша (мгновенно)
       val cachedTasks = localApi.getTasks(activeOnly)
       updateUIFromCache()
       
       // 2. Синхронизируем в фоне
       if (syncService.isOnline() && apiBaseUrl != null) {
           scope.launch(Dispatchers.IO) {
               val groupsApi = GroupsApi(baseUrl = apiBaseUrl)
               val usersApi = UsersApi(baseUrl = apiBaseUrl)
               val syncResult = syncService.syncCacheWithServer(groupsApi, usersApi)
               
               if (syncResult.isSuccess) {
                   val result = syncResult.getOrNull()
                   withContext(Dispatchers.Main) {
                       if (result?.users != null) {
                           users = result.users
                       }
                       if (result?.groups != null) {
                           saveGroupsToCache(context, result.groups)
                       }
                   }
                   if (result?.cacheUpdated == true) {
                       updateUIFromCache()
                   }
               }
           }
       }
   }
   ```

5. Обновить периодическую синхронизацию (если нужно)

**Критерии готовности:**
- ✅ Все вызовы `tasksApiOffline` заменены на `localApi`
- ✅ Явные вызовы синхронизации добавлены
- ✅ Приложение работает корректно
- ✅ Тесты проходят

---

### Этап 4: Удаление TasksApiOffline

**Цель:** Удалить устаревший класс `TasksApiOffline`.

**Шаги:**

1. Убедиться, что `TasksApiOffline` больше нигде не используется:
   ```bash
   grep -r "TasksApiOffline" android/
   ```

2. Удалить файл `android/app/src/main/java/com/homeplanner/api/TasksApiOffline.kt`

3. Удалить тесты `TasksApiOfflineTest.kt` (или переписать их для `LocalApi`)

4. Обновить импорты в других файлах (если есть)

**Критерии готовности:**
- ✅ `TasksApiOffline` удалён
- ✅ Нет ссылок на удалённый класс
- ✅ Все тесты проходят

---

### Этап 5: Обновление документации

**Цель:** Обновить документацию архитектуры.

**Шаги:**

1. Обновить `OFFLINE_FIRST_ARCHITECTURE.md`:
   - Изменить статус `LocalApi` с "планируется" на "реализован"
   - Удалить раздел "Миграция с TasksApiOffline на LocalApi"
   - Обновить примеры использования

2. Обновить README (если есть упоминания)

3. Обновить комментарии в коде

**Критерии готовности:**
- ✅ Документация обновлена
- ✅ Примеры актуальны
- ✅ Комментарии в коде обновлены

## Чеклист миграции

- [x] **Этап 1:** Создание LocalApi
  - [x] Файл создан
  - [x] Методы реализованы
  - [x] Логика синхронизации удалена
  - [x] Тесты написаны
  - [x] Документация добавлена

- [x] **Этап 2:** Обновление SyncService
  - [x] Все операции обрабатываются
  - [x] Обработка ошибок реализована
  - [x] Тесты написаны

- [x] **Этап 3:** Обновление MainActivity
  - [x] LocalApi создан
  - [x] Все вызовы заменены
  - [x] Явная синхронизация добавлена
  - [x] Приложение работает

- [x] **Этап 4:** Удаление TasksApiOffline
  - [x] Проверка использования
  - [x] Файл удалён
  - [x] Тесты удалены/переписаны

- [x] **Этап 5:** Обновление документации
  - [x] OFFLINE_FIRST_ARCHITECTURE.md обновлён
  - [x] Примеры обновлены
  - [x] Комментарии обновлены

## Риски и митигация

### Риск 1: Потеря функциональности при миграции

**Митигация:**
- Тщательное тестирование на каждом этапе
- Сохранение всех тестов `TasksApiOffline` до завершения миграции
- Постепенная миграция (по одному методу)

### Риск 2: Проблемы с синхронизацией

**Митигация:**
- Убедиться, что `SyncService` полностью покрывает логику синхронизации
- Тестирование оффлайн-сценариев
- Мониторинг очереди синхронизации

### Риск 3: Регрессии в UI

**Митигация:**
- UI-тесты перед миграцией
- Ручное тестирование всех сценариев
- Откат изменений при необходимости

## Оценка времени

- **Этап 1:** 4-6 часов
- **Этап 2:** 2-3 часа
- **Этап 3:** 4-6 часов
- **Этап 4:** 1 час
- **Этап 5:** 1-2 часа

**Итого:** 12-18 часов

## Следующие шаги

1. Начать с Этапа 1 (создание LocalApi)
2. После завершения каждого этапа — обновить чеклист
3. После завершения всех этапов — обновить статус документа

---

**Последнее обновление:** 2025-01-27  
**Дата завершения:** 2025-01-27  
**Статус:** ✅ Миграция полностью завершена, документ перемещён в архив
