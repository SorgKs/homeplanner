## ЛОГИКА СИНХРОНИЗАЦИИ ОЧЕРЕДИ (`SyncService.syncStateBeforeRecalculation`)

### 1. Формат ответа сервера для `syncQueue`

- Клиентский метод `TasksApi.syncQueue(items: List<SyncQueueItem>): List<Task>` ожидает, что сервер вернёт **JSON‑массив задач**:
  - пример корректного тела ответа:
    - `[{ "id": 1, "title": "...", ... }]`
  - внутри реализовано:
    - `val array = JSONArray(respBody)` — парсится именно массив, а не одиночный объект.
- Во всех тестах `syncQueue_*` формат мока соответствует этому ожиданию (возвращается `[...]`).

### 2. Проблема в тесте `syncStateBeforeRecalculation_sendsPendingQueue_andReloadsTasks`

- В этом тесте первый `MockResponse`, имитирующий ответ сервера на `syncQueue`, был задан как **одиночный JSON‑объект**:
  - `{ "id": 1, "title": "Тестовая задача", ... }`
  - это приводит к выбросу исключения в `JSONArray(respBody)` (ожидается массив).
- В результате:
  - `TasksApi.syncQueue` падает при парсинге;
  - `SyncService.syncQueue` возвращает `Result.failure(e)` **без вызова `repository.clearAllQueue()`**;
  - `SyncService.syncStateBeforeRecalculation` логирует warning и продолжает, вызывая `getTasks`, но pending‑элемент в очереди остаётся;
  - финальная проверка `getPendingQueueItems()` в тесте показывает `size == 1` вместо ожидаемого `0`.

### 3. Решение

- Исправление состоит в том, чтобы вернуть **массив задач** в первом `MockResponse` теста:
  - обернуть объект в квадратные скобки, как в остальных тестах `syncQueue_*`.
- После этого:
  - `syncQueue()` успешно отрабатывает;
  - `OfflineRepository.clearAllQueue()` очищает таблицу `sync_queue`;
  - `syncStateBeforeRecalculation()` возвращает `true`, и очередь действительно становится пустой.


