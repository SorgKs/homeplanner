# Decision Log

## Architectural Decisions

### Technology Stack Selection
- **Decision**: Use Python FastAPI for backend API server
- **Rationale**: Modern, async-capable framework with automatic OpenAPI documentation
- **Date**: Initial project setup

### Database Choice
- **Decision**: SQLite for development, PostgreSQL for production
- **Rationale**: SQLite for simplicity in development, PostgreSQL for production scalability
- **Date**: Initial setup

### Mobile Framework
- **Decision**: Kotlin with Jetpack Compose for Android app
- **Rationale**: Modern Android development with declarative UI
- **Date**: Android component addition

### Real-time Communication
- **Decision**: WebSocket for real-time updates between clients
- **Rationale**: Enables live synchronization without polling
- **Date**: WebSocket implementation

### Offline-First Architecture
- **Decision**: Implement offline-first design for Android app
- **Rationale**: Better user experience in poor network conditions
- **Date**: Android offline features

## Database Schema Decisions

### Groups Feature (029e6193a551)
- **Decision**: Add groups table for task organization
- **Rationale**: Allow categorization of tasks beyond just users
- **Impact**: Tasks can now belong to groups

### Recurrence Types (59eedfe7933f, 65b1a7f858d4, 91afd8b8effa)
- **Decision**: Support multiple recurrence patterns (interval, weekdays/weekends)
- **Rationale**: Flexible scheduling for different types of recurring tasks
- **Impact**: Tasks can recur daily, weekly, on specific days

### User Management (b4c5d6e7f8a9, c7d8e9f0ab12)
- **Decision**: Add users table with roles and status
- **Rationale**: Multi-user support for shared task management
- **Impact**: Tasks can be assigned to specific users

### Task History (a1b2c3d4e5f6, 8836f263f0a0)
- **Decision**: Track all task state changes in history table
- **Rationale**: Audit trail and debugging capability
- **Impact**: Complete log of task modifications

### Notifications System (cf2a3d7f4b6e)
- **Decision**: Add notifications and revision fields
- **Rationale**: Track notification delivery and data versioning
- **Impact**: Better notification management

### Field Renaming (20260105_rename_active_to_enabled)
- **Decision**: Rename 'active' field to 'enabled' for clarity
- **Rationale**: 'Enabled' better reflects the boolean state meaning
- **Impact**: API and code consistency

### App Metadata (20251110_add_app_metadata_table)
- **Decision**: Add table for application metadata storage
- **Rationale**: Centralized app configuration and versioning
- **Impact**: Better app state management

### Task Field Migration (20251109_task_field_migration)
- **Decision**: Migrate task fields for better structure
- **Rationale**: Improve data model consistency
- **Impact**: Updated task schema

### Revision Removal (9215f41a8633)
- **Decision**: Remove revision field from tasks table
- **Rationale**: Revision tracking moved to separate mechanism
- **Impact**: Simplified task schema

### Cascade Behavior (fa0aaa1f7284)
- **Decision**: Change task history foreign key to SET NULL on delete
- **Rationale**: Preserve history even when tasks are deleted
- **Impact**: Better data integrity

## Configuration Decisions

### Centralized Configuration
- **Decision**: Use single settings.toml file for all configuration
- **Rationale**: Single source of truth, easier management
- **Date**: Configuration system implementation

### API Versioning
- **Decision**: Prefix all API endpoints with version (/api/v0.2)
- **Rationale**: Backward compatibility and controlled upgrades
- **Date**: API design

## Development Process Decisions

### Versioning Strategy
- **Decision**: Semantic versioning with separate version files
- **Rationale**: Clear version management across components
- **Date**: Versioning system implementation

### Build Scripts
- **Decision**: Automated build scripts for Android (android/build.sh)
- **Rationale**: Consistent and repeatable builds
- **Date**: Build system setup

### WebSocket Synchronization Fix
- **Decision**: Include full task payload in WebSocket messages for "completed" and "uncompleted" actions
- **Rationale**: Android app expects full task data for status updates, previously only task_id was sent causing synchronization failures
- **Impact**: Ensures real-time sync between web and Android clients for task completions
- **Date**: 2026-01-09

### Offline Task Recalculation Implementation
- **Decision**: Implement offline task recalculation in Android app following backend principles
- **Rationale**: Tasks should be recalculated locally when offline, using the same logic as backend for consistency
- **Impact**: Android app now handles task recalculation independently, ensuring proper task updates even without server connection
- **Components**: OfflineRepository methods check for new day on every access, DayChangeScheduler uses AlarmManager for daily recalculation at day start +1 minute
- **Date**: 2026-01-10

### Task Recalculation Filter Update
- **Decision**: Change recalculate_tasks to process ALL completed tasks regardless of reminder_time, and use task's reminder_time as base for next calculation when it's in the future
- **Rationale**: New filtering rules require all completed tasks to be recalculated on new day, not just those with past reminder_time. For tasks completed before their due date, next reminder_time should be calculated from original reminder_time
- **Impact**: Ensures consistent task recalculation behavior, completed future tasks are properly rescheduled
- **Components**: Modified recalculate_tasks logic in TaskService, added test for future reminder_time recalculation
- **Date**: 2026-01-12

### Project Structure Reorganization
- **Decision**: Move files from project root to appropriate subdirectories (alembic/ to backend/alembic/, scripts to scripts/, documentation files to docs/, etc.) and update configurations accordingly
- **Rationale**: Clean up project root directory by organizing files into logical locations, improving maintainability and reducing clutter
- **Impact**: Better project organization, updated paths in alembic.ini for database and script locations
- **Components**: Moved alembic migrations, scripts, documentation, and updated backend/alembic.ini paths
- **Date**: 2026-01-12

### Alarm Fields Addition to Tasks
- **Decision**: Add 'alarm' (Boolean, default=False) and 'alarm_time' (DateTime, nullable=True) fields to the tasks table using Alembic autogenenerate migration
- **Rationale**: Enable alarm functionality for tasks, allowing users to set reminders with specific alarm times
- **Impact**: Tasks can now have alarms set, with validation ensuring alarm_time is provided when alarm is True
- **Components**: Updated Task model with alarm fields and validation, generated migration d94c8af5aff3 including full initial schema
- **Date**: 2026-01-13

### Alarm Management Utility Class
- **Decision**: Create AlarmManagerUtil Kotlin class in Android app to provide centralized methods for scheduling and canceling alarms using AlarmManager
- **Rationale**: Encapsulate alarm management logic in a reusable utility class, ensuring consistent PendingIntent handling and proper AlarmManager usage
- **Impact**: Android app can now schedule and cancel task alarms reliably, integrating with existing ReminderReceiver for alarm notifications
- **Components**: Added AlarmManagerUtil.kt in utils/ package with scheduleAlarm and cancelAlarm methods
- **Date**: 2026-01-13

### Alarm Integration in OfflineRepository
- **Decision**: Integrate alarm scheduling logic into OfflineRepository.saveTasksToCache() method to automatically manage alarms when tasks are loaded or updated
- **Rationale**: Centralize alarm management at the repository level to ensure alarms are consistently scheduled/cancelled whenever task data changes, whether from server sync or local operations
- **Impact**: Tasks with alarm=True and future alarm_time will have alarms scheduled automatically; alarms are cancelled for disabled alarms or past times
- **Components**: Updated Task and TaskCache models with alarm fields, added manageAlarmsForTasks() function in OfflineRepository with ISO datetime parsing and AlarmManagerUtil integration
- **Date**: 2026-01-13

### Dependency Management with uv
- **Decision**: Use uv package manager for Python dependency installation and virtual environment management
- **Rationale**: uv is faster and more reliable than pip for dependency resolution and installation, especially for projects with complex dependency trees
- **Impact**: Backend can now start properly with all required dependencies (SQLAlchemy, FastAPI, etc.) installed in isolated virtual environment
- **Components**: Created .venv virtual environment, installed 54 packages including all project dependencies from pyproject.toml
- **Date**: 2026-01-13

### WebSocket Endpoint Path Fix
- **Decision**: Change WebSocket path construction in Android WebSocketService from hardcoded "/api/v0.2/tasks/stream" to relative "/tasks/stream" to work with dynamic API versioning
- **Rationale**: Android app was constructing incorrect WebSocket URLs by appending hardcoded v0.2 path to base URL that already contained API version, resulting in malformed URLs like "/api/v0.3/api/v0.2/tasks/stream"
- **Impact**: Android app can now properly connect to WebSocket endpoint using correct API version from configuration (currently v0.3)
- **Components**: Modified WEBSOCKET_PATH constant in WebSocketService.kt, updated NetworkConfig.kt comments to reflect v0.3 API version
- **Date**: 2026-01-13

### Task Completion UI Bug Fixes
- **Decision**: Fix incorrect onCheckedChange handlers in TaskItemToday.kt and TaskItemAll.kt to pass the new checked state instead of old task.completed, and correct the logic in MainActivity.kt to properly handle complete/uncomplete based on new state
- **Rationale**: The UI was passing incorrect values to the completion handlers, and the logic was inverted, preventing proper task state updates and synchronization
- **Impact**: Task completion/uncompletion now correctly updates local state and triggers server synchronization
- **Components**: Updated onCheckedChange in TaskItemToday.kt and TaskItemAll.kt to pass 'checked' parameter, fixed if-else logic in MainActivity.kt onTaskComplete
- **Date**: 2026-01-14