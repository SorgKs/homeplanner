# System Patterns

## Architectural Patterns

### 1. Modular Architecture
- **Backend**: Organized into routers (API endpoints), services (business logic), models (database entities), utils (helpers)
- **Android**: Component-based with offline-first design
- **Frontend**: Module-based JavaScript organization
- **Common**: Shared utilities and configuration

### 2. API Versioning
- All API endpoints prefixed with `/api/v{version}` (currently v0.2)
- Version configurable in settings.toml
- Backward compatibility maintained through versioning

### 3. Centralized Configuration
- Single source of truth: `common/config/settings.toml`
- Environment-specific overrides possible
- Configuration read at build time for Android, runtime for backend/frontend

### 4. Database Layer
- SQLAlchemy ORM for database operations
- Alembic for schema migrations
- Support for SQLite (development) and PostgreSQL (production)
- Models include relationships and constraints

### 5. Real-time Synchronization
- WebSocket connections for live updates
- Event-driven notifications for task changes
- Automatic UI updates without full page refreshes

### 6. Offline-First Mobile Design
- Android app maintains local data store
- Sync service handles background synchronization
- Network operations cached and retried

## Behavioral Patterns

### 7. Event-Driven Updates
- Task changes trigger WebSocket broadcasts
- Multiple clients receive updates simultaneously
- Reduces need for polling and manual refreshes

### 8. Task Lifecycle Management
- Tasks have states: enabled, completed, recurring
- Completion/uncompletion tracked in history
- Notifications generated based on task state changes

### 9. User and Group Organization
- Tasks assigned to users
- Groups provide categorization
- Permissions and access control

### 10. Error Handling and Logging
- Backend errors logged with timestamps
- API responses include error details
- Frontend handles network errors gracefully

### 11. Version Management
- Semantic versioning across components
- Automated version bumping scripts
- Version displayed in UI and API

### 12. Security Patterns
- JWT-based authentication
- CORS configuration for cross-origin requests
- Input validation on API endpoints

### 13. Build and Deployment
- Gradle for Android APK builds
- Python packaging with uv/pip
- Scripts for automated builds and deployments

## Data Patterns

### 14. Task History Tracking
- All task state changes logged
- Audit trail for debugging and compliance
- Historical data for analytics

### 15. Recurrence Patterns
- Flexible task scheduling (daily, weekly, custom)
- Date utilities for recurrence calculations
- Timezone-aware operations

### 16. Configuration Inheritance
- Default settings with overrides
- Environment-specific configurations
- Runtime vs build-time resolution