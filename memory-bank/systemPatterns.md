# System Patterns

## Architectural Patterns
- **Offline-First Architecture:** Все операции сначала выполняются локально, потом синхронизируются
- **Repository Pattern:** Абстракция доступа к данным через репозитории
- **MVVM (Model-View-ViewModel):** Для Android UI
- **Clean Architecture:** Разделение на слои (data, domain, presentation)

## Code Organization
- **Backend:** Python с FastAPI, SQLAlchemy для ORM, Alembic для миграций
- **Frontend:** JavaScript с модульной структурой
- **Android:** Kotlin с Room для локальной БД
- **Common:** Общие утилиты на Python

## Naming Conventions
- **Python:** snake_case для функций/переменных, PascalCase для классов
- **JavaScript:** camelCase для всего
- **Kotlin:** camelCase для функций/переменных, PascalCase для классов
- **SQL:** snake_case для таблиц/колонок

## Data Patterns
- **Entity Pattern:** Room entities для локального хранения
- **DTO Pattern:** Data Transfer Objects для API коммуникации
- **Hash-based Change Detection:** Использование хешей для обнаружения изменений

## Synchronization Patterns
- **Event-Driven Sync:** Обработка событий изменения данных
- **Conflict Resolution:** Стратегии разрешения конфликтов (last-write-wins, manual merge)
- **Queue-based Operations:** Очередь операций для офлайн-синхронизации

## Error Handling
- **Try-Catch with Logging:** Логирование ошибок с контекстом
- **Graceful Degradation:** Продолжение работы при ошибках синхронизации