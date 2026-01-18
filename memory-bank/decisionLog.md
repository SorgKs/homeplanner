# Decision Log

## Major Architectural Decisions

### 1. Offline-First Architecture (2023-12-XX)
**Decision:** Реализовать offline-first подход для Android-приложения
**Rationale:** Обеспечить работу приложения без интернета, что критично для мобильных пользователей
**Alternatives Considered:** Online-only, periodic sync
**Impact:** Требует локальной БД и сложной логики синхронизации

### 2. Room for Local Storage (2024-01-XX)
**Decision:** Использовать Room вместо SharedPreferences для хранения данных
**Rationale:** Более надежное и структурированное хранение, поддержка сложных запросов
**Alternatives Considered:** SQLite напрямую, SharedPreferences
**Impact:** Необходимость в entities, DAOs и migrations

### 3. Hash-Based Change Detection (2024-XX-XX)
**Decision:** Добавить поля hash в entities для обнаружения изменений
**Rationale:** Эффективное обнаружение конфликтов при синхронизации
**Alternatives Considered:** Timestamps, version fields
**Impact:** Требует HashCalculator и обновления всех entities

### 4. Kotlin for Android Development (2023-XX-XX)
**Decision:** Использовать Kotlin вместо Java
**Rationale:** Современный язык с null-safety и корутинами
**Alternatives Considered:** Java
**Impact:** Переход на Kotlin для всех новых компонентов

### 5. Repository Pattern Implementation (2024-XX-XX)
**Decision:** Внедрить репозитории для абстракции доступа к данным
**Rationale:** Разделение ответственности, тестируемость
**Alternatives Considered:** Direct DAO usage
**Impact:** Создание UserRepository, GroupRepository, обновление существующих

### 6. Hash-Based Conflict Resolution (2024-XX-XX)
**Decision:** Реализовать алгоритмы разрешения конфликтов на основе хешей и типов изменений
**Rationale:** Обеспечить консистентность данных при оффлайн-синхронизации
**Alternatives Considered:** Always server wins, always client wins
**Impact:** Обновление ConflictResolver с поэлементным анализом конфликтов

### 7. Room Index Optimization (2024-XX-XX)
**Decision:** Добавить индекс по полю hash в TaskEntity для оптимизации запросов синхронизации
**Rationale:** Улучшить производительность поиска и сравнения хешей при синхронизации
**Alternatives Considered:** Без индекса, индекс по другим полям
**Impact:** Добавление Index(value = ["hash"]) в TaskCache entity

### 8. Hash Calculator Consistency Fix (2026-01-17)
**Decision:** Исправить несоответствия в HashCalculator между Android и backend, привести к единой формуле
**Rationale:** Обеспечить консистентность хешей для правильной синхронизации
**Alternatives Considered:** Оставить как есть, исправить только в одном месте
**Impact:** Изменение calculate_combined_hash в backend для сортировки по ID, удаление лишних полей из Group hash в Android

### 9. TaskEntity Renaming and Data Migration (2026-01-18)
**Decision:** Переименовать TaskCache в TaskEntity, TaskCacheRepository в TaskRepository, и реализовать миграцию данных из SharedPreferences в Room
**Rationale:** Улучшить naming consistency и выполнить полную миграцию хранения данных согласно стратегии обновления
**Alternatives Considered:** Оставить старые имена, миграция только через синхронизацию
**Impact:** Обновление всех ссылок на классы, миграция Room schema, автоматическая миграция данных пользователей из SharedPreferences