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