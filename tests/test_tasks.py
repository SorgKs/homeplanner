"""Тесты REST API задач."""

from __future__ import annotations

from collections.abc import Generator
from datetime import datetime, timedelta

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from backend.database import Base, get_db
from backend.main import app
from backend.models.task import RecurrenceType
from tests.utils import (
    api_path,
    create_sqlite_engine,
    isoformat,
    session_scope,
    test_client_with_session,
)


engine, SessionLocal = create_sqlite_engine("test_tasks.db")


@pytest.fixture(scope="module")
def db_setup() -> Generator[None, None, None]:
    """Create test database schema once per module."""
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


@pytest.fixture(scope="function")
def db_session(db_setup: None) -> Generator[Session, None, None]:
    """Создать сессию тестовой базы данных SQLite и очистить данные между тестами."""
    from sqlalchemy import text

    db = SessionLocal()
    try:
        # Clean all tables before each test
        # Disable foreign key checks for faster deletion, then re-enable
        with db.begin():
            db.execute(text("PRAGMA foreign_keys = OFF"))
            # Delete all data - order doesn't matter with FK checks disabled
            db.execute(text("DELETE FROM task_users"))
            db.execute(text("DELETE FROM task_history"))
            db.execute(text("DELETE FROM tasks"))
            db.execute(text("DELETE FROM events"))
            db.execute(text("DELETE FROM groups"))
            db.execute(text("DELETE FROM users"))
            db.execute(text("DELETE FROM app_metadata"))
            db.execute(text("PRAGMA foreign_keys = ON"))
        db.commit()
        yield db
    finally:
        db.rollback()
        db.close()


@pytest.fixture(scope="function")
def client(db_session: Session) -> Generator[TestClient, None, None]:
    """Создать тестовый клиент FastAPI с изолированной БД."""

    with test_client_with_session(app, get_db, db_session) as test_client:
        yield test_client


def _create_task(client: TestClient, payload: dict) -> dict:
    """Создать задачу через REST и вернуть ответ."""

    response = client.post(api_path("/tasks/"), json=payload)
    assert response.status_code == 201
    return response.json()


def test_create_one_time_task_defaults(client: TestClient) -> None:
    """Создание разовой задачи возвращает корректные флаги."""

    reminder_time = datetime.now() + timedelta(hours=2)
    payload = {
        "title": "Одноразовая задача",
        "task_type": "one_time",
        "reminder_time": isoformat(reminder_time),
    }
    data = _create_task(client, payload)

    assert data["title"] == payload["title"]
    assert data["reminder_time"] == payload["reminder_time"]
    assert data["completed"] is False
    assert data["active"] is True


def test_update_task_sets_active_flag(client: TestClient) -> None:
    """Обновление задачи позволяет менять флаг активности."""

    reminder_time = datetime.now() + timedelta(days=1)
    payload = {
        "title": "Ежедневная задача",
        "task_type": "recurring",
        "recurrence_type": RecurrenceType.DAILY.value,
        "recurrence_interval": 1,
        "reminder_time": isoformat(reminder_time),
    }
    created = _create_task(client, payload)

    update_body = {
        "active": False,
    }
    response = client.put(api_path(f"/tasks/{created['id']}"), json=update_body)
    assert response.status_code == 200
    updated = response.json()
    assert updated["active"] is False


def test_complete_task_marks_completed(client: TestClient) -> None:
    """Подтверждение выполнения помечает задачу completed."""

    reminder_time = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
    payload = {
        "title": "Расписание на сегодня",
        "task_type": "recurring",
        "recurrence_type": RecurrenceType.DAILY.value,
        "recurrence_interval": 1,
        "reminder_time": isoformat(reminder_time),
    }
    created = _create_task(client, payload)

    response = client.post(api_path(f"/tasks/{created['id']}/complete"))
    assert response.status_code == 200
    completed = response.json()
    assert completed["completed"] is True
    # Напоминание не меняется, если задача была на сегодня
    assert completed["reminder_time"] == created["reminder_time"]


def test_uncomplete_task_restores_state(client: TestClient) -> None:
    """Отмена выполнения возвращает флаги по умолчанию."""

    reminder_time = datetime.now().replace(hour=10, minute=0, second=0, microsecond=0)
    payload = {
        "title": "Разовая задача",
        "task_type": "one_time",
        "reminder_time": isoformat(reminder_time),
    }
    created = _create_task(client, payload)

    complete_response = client.post(api_path(f"/tasks/{created['id']}/complete"))
    assert complete_response.status_code == 200
    assert complete_response.json()["completed"] is True
    assert complete_response.json()["active"] is False

    uncomplete_response = client.post(api_path(f"/tasks/{created['id']}/uncomplete"))
    assert uncomplete_response.status_code == 200
    data = uncomplete_response.json()
    assert data["completed"] is False
    assert data["active"] is True
    assert data["reminder_time"] == created["reminder_time"]

