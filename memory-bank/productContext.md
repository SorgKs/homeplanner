# Product Context: HomePlanner

## Overview
HomePlanner is a task planning and reminder application designed for home use. It helps users manage recurring tasks and one-time events with notifications, providing both a web interface and an Android mobile app.

## Key Features
- Recurring task reminders
- One-time event storage and notifications
- Simple Android application for creating events and reminders
- Web interface for task management
- Complete history of all task actions

## Architecture
- **Backend**: Python-based API server using FastAPI framework
- **Database**: SQLite by default, PostgreSQL support for production
- **Android App**: Kotlin with Jetpack Compose, offline-first design
- **Web Frontend**: Static web interface served via HTTP server
- **Real-time Sync**: WebSocket connections for live updates
- **Configuration**: Centralized via `common/config/settings.toml`

## Deployment
- Backend runs on port 8000 (configurable)
- Web frontend on port 8080
- Supports network deployment for mobile access
- Production-ready with systemd services and reverse proxies

## Target Users
Home users who need to manage household tasks, chores, and reminders across multiple devices.

## Key Components
- Task management with completion tracking
- Group-based task organization
- User assignments for tasks
- Task history logging
- Real-time notifications via WebSocket
- Offline synchronization for mobile app

## Technology Stack
- Backend: Python 3.11+, FastAPI, SQLAlchemy, Alembic
- Database: SQLite/PostgreSQL
- Android: Kotlin, Jetpack Compose, Gradle
- Frontend: HTML/CSS/JavaScript
- Networking: WebSocket, HTTP API
- Versioning: Semantic versioning with bump scripts