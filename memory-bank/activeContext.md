# Active Context

## Current Work
Завершена реализация запланированных изменений в Android-приложении. Все компоненты офлайн-синхронизации внедрены.

## Recent Changes
- Реорганизация документации проекта (docs/INDEX.md)
- Разработка backend на Python с FastAPI и SQLAlchemy
- Реализация веб-интерфейса с WebSocket поддержкой
- Настройка Android-проекта с Gradle и Room
- Реализация Room entities с хешами для задач, пользователей и групп
- Создание репозиториев для управления данными
- Внедрение EventProcessor и ConflictResolver для синхронизации
- Интеграция компонентов в SyncService

## Current Challenges
- Тестирование реализованной синхронизации
- Проверка миграции БД на существующих устройствах

## Active Files
- Android: app/src/main/java/... (структура Room entities, DAOs, repositories)
- Docs: CACHE_STRATEGY_UPDATE_V1.md, OFFLINE_FIRST_ARCHITECTURE.md

## Next Steps
- Создание новых Room Entity с полями hash
- Реализация HashCalculator
- Обновление репозиториев и базы данных
- Интеграция в SyncService

## Dependencies
- Текущая версия Android API: 21+
- Room: последняя стабильная версия
- Kotlin для Android разработки
- Backend API с поддержкой синхронизации