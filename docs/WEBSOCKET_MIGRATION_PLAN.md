# План миграции WebSocket

## 1. Обзор
Миграция WebSocket направлена на интеграцию новых функций обмена в реальном времени, уточнение архитектуры и обеспечение соответствия требованиям к оффлайн‑первому подходу. План охватывает уже реализованные этапы и предоставляет ссылки на все необходимые документы.

### 1.1 WebSocket‑эндпоинт
- **URL**: `ws://<host>:<port>/api/v<version>/tasks/stream`
- **Пример**: `ws://localhost:8000/api/v0.2/tasks/stream`
- **Формат сообщений**: `task_update` с действиями `created`, `updated`, `deleted`, `completed`, `uncompleted`, `shown`
- **Переподключение**: экспоненциальный бэкофф с джиттером (2–10 с, макс. 60 с)
- **Разрешение конфликтов**: сервер — источник истины, клиент синхронизирует локальный кэш после успешной синхронизации

## 2. Реализованные этапы

| Этап | Описание | Ссылка на документ |
|------|----------|--------------------|
| **2.1** | Начальная настройка WebSocket‑эндпоинтов в FastAPI | [DOCUMENTATION_ANALYSIS_REPORT.md](docs/DOCUMENTATION_ANALYSIS_REPORT.md) |
| **2.2** | Интеграция с системой событий | [OFFLINE_CLIENT_DESIGN.md](docs/OFFLINE_CLIENT_DESIGN.md) |
| **2.3** | Обеспечение совместимости с Android‑приложением | [ANDROID_USER_GUIDE.md](docs/ANDROID_USER_GUIDE.md) |
| **2.4** | Настройка путей к WebSocket‑потокам через `settings.toml` | [PLAN_NETWORK_SETTINGS.md](docs/PLAN_NETWORK_SETTINGS.md) |

## 3. Необходимые документы для написания кода
- **API‑спецификации**: [API.md](docs/API.md) — описывает доступные эндпоинты.  
- **Согласованность интерфейсов**: [INTERFACES_CONSISTENCY_REPORT.md](docs/INTERFACES_CONSISTENCY_REPORT.md) — гарантирует единый стиль.  
- **Архитектурные принципы**: [OFFLINE_FIRST_ARCHITECTURE.md](docs/OFFLINE_FIRST_ARCHITECTURE.md) — определяет принципы оффлайн‑первого подхода.  
- **Тестовые сценарии**: [TESTING_CONVENTIONS.md](docs/TESTING_CONVENTIONS.md) — стандарты тестирования WebSocket‑функций.

## 4. Структура кода
- **backend/routers/realtime.py** — роутер для WebSocket‑соединений.  
- **backend/models/event.py**, **backend/models/group.py** — модели событий и групп.  
- **backend/services/event_service.py**, **backend/services/group_service.py** — сервисы обработки.  
- **backend/schemas/** — схемы Pydantic для валидации сообщений.

## 5. План миграции
1. **Аудит текущих WebSocket‑маршрутов** – сравнить с требованиями из [API.md](docs/API.md). ✅
2. **Обновление схем данных** – добавить новые поля, описанные в [INTERFACES_CONSISTENCY_REPORT.md](docs/INTERFACES_CONSISTENCY_REPORT.md). ✅  
   - Уточнить статус `TasksApiOffline` как устаревшего, удалить его  
   - Разделить обязанности между `LocalApi` (локальное хранилище) и `SyncService` (синхронизация)  
   - Добавить описание всех методов `OfflineRepository` (включая метаданные)  
   - Синхронизировать потоки данных в документации под целевую архитектуру с `LocalApi` и `SyncService`  
   - Перенести WebSocket из UI в слой синхронизации (см. [WEBSOCKET_REFACTORING_PLAN.md](docs/WEBSOCKET_REFACTORING_PLAN.md))
3. **Реализация бизнес‑логики в сервисах** – использовать [event_service.py](backend/services/event_service.py) и [group_service.py](backend/services/group_service.py).  
4. **Создание тестов** – покрыть новые сценарии согласно [TESTING_CONVENTIONS.md](docs/TESTING_CONVENTIONS.md). ✅  
   - Добавить тесты для WebSocket‑соединений и переподключения  
   - Добавить тесты для обработки сообщений `task_update` (легкие и полные операции)  
   - Добавить тесты для разрешения конфликтов (сервер — источник истины)  
   - Добавить тесты для offline‑first поведения (локальный кэш, синхронизация)  
   - Добавить smoke‑тесты для WebSocket‑потока в Android и веб‑клиенте
5. **Документация** – добавить описания к новым эндпоинтам и процессам в соответствующие markdown‑файлы.  
6. **Порядок работы** – основные этапы правки кода, что именно и как нужно править на каждом этапе и как контролировать процесс

### Этап 1: Проверка существующих компонентов
**Что делать:**
- Проверить, что `LocalApi` уже реализован в `android/app/src/main/java/com/homeplanner/api/LocalApi.kt`
- Убедиться, что `TasksApiOffline` отсутствует в коде (уже удалён)
- Зафиксировать текущее состояние WebSocket в `MainActivity.kt`

**Как контролировать:**
- Убедиться, что `LocalApi` используется в `MainActivity.kt`
- Подтвердить отсутствие `TasksApiOffline` в коде

### Этап 2: Создание WebSocketService
**Что делать:**
- Создать файл `android/app/src/main/java/com/homeplanner/sync/WebSocketService.kt`
- Реализовать методы: `start`, `stop`, `isConnected`, `connectWebSocket`, `handleMessage`
- Обрабатывать события `task_update` и `hash_check`
- Обновлять локальный кэш через `OfflineRepository`
- Вызывать `ReminderScheduler` после обновлений

**Как контролировать:**
- Проверить, что WebSocket подключается и переподключается при разрыве
- Убедиться, что события обрабатываются и сохраняются в кэш
- Проверить, что `ReminderScheduler` вызывается после обновлений

### Этап 3: Интеграция WebSocketService в MainActivity
**Что делать:**
- Создать экземпляр `WebSocketService` в `MainActivity`
- Управлять жизненным циклом через `LaunchedEffect`
- Удалить старую реализацию WebSocket из `MainActivity`

**Как контролировать:**
- Проверить, что WebSocket работает и обновляет кэш
- Убедиться, что старая реализация полностью удалена

### Этап 6: Тестирование
**Что делать:**
- Добавить тесты для WebSocket‑соединений и переподключения
- Добавить тесты для обработки сообщений `task_update`
- Добавить тесты для разрешения конфликтов
- Добавить тесты для offline‑first поведения
- Добавить smoke‑тесты для WebSocket‑потока

**Как контролировать:**
- Запустить все тесты, убедиться, что они проходят
- Проверить, что покрытие тестами соответствует требованиям

### Этап 7: Документация
**Что делать:**
- Обновить `ANDROID_ARCHITECTURE.md` — убрать предупреждения о текущей реализации
- Добавить описание `LocalApi` и `WebSocketService` в документацию
- Обновить диаграммы архитектуры

**Как контролировать:**
- Проверить, что документация актуальна и соответствует коду
- Убедиться, что все изменения отражены в документации

## 6. Ссылки на связанные документы
- [DOCUMENTATION_CONSISTENCY_REPORT.md](docs/DOCUMENTATION_CONSISTENCY_REPORT.md)  
- [OFFLINE_CLIENT_DESIGN.md](docs/OFFLINE_CLIENT_DESIGN.md)  
- [OFFLINE_REQUIREMENTS.md](docs/OFFLINE_REQUIREMENTS.md)  
- [PLAN_OFFLINE_MODE.md](docs/PLAN_OFFLINE_MODE.md)  
- [PLAN_OFFLINE_MODE_IMPROVEMENTS.md](docs/PLAN_OFFLINE_MODE_IMPROVEMENTS.md)  
- [WEBSOCKET_REFACTORING_PLAN.md](docs/WEBSOCKET_REFACTORING_PLAN.md)  

## 7. Риски и меры по их снижению
- **Риск потери событий при переходе** — тестирование всех сценариев переключения между вкладками и состояниями приложения
- **Риск утечки памяти** — использование правильного CoroutineScope, закрытие WebSocket при остановке
- **Риск UI не обновляется автоматически** — использование Flow для наблюдения за кэшем, проверка реакции Compose
- **Риск конфликтов при одновременном обновлении** — сервер — источник истины, все обновления через OfflineRepository

## 8. Расписание и ответственные
- **Этап 1 (Аудит маршрутов)**: 1 день, ответственный — Backend-разработчик
- **Этап 2 (Синхронизация схем)**: 2 дня, ответственный — Backend-разработчик
- **Этап 3 (Реализация бизнес‑логики)**: 3 дня, ответственный — Backend-разработчик
- **Этап 4 (Тестирование)**: 2 дня, ответственный — QA-инженер
- **Этап 5 (Документация)**: 1 день, ответственный — Технический писатель

---
*Документ подготовлен 28.12.2025, версия 1.0.*
