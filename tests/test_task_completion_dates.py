"""Тесты поведения reminder_time и флагов при выполнении задач."""

from __future__ import annotations

from collections.abc import Generator
from datetime import datetime, timedelta

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from backend.database import Base, get_db
from backend.main import app
from backend.models.task import Task
from tests.utils import (
    api_path,
    create_sqlite_engine,
    isoformat,
    session_scope,
    test_client_with_session,
)

engine, SessionLocal = create_sqlite_engine("test_completion_dates.db")


@pytest.fixture(scope="function")
def db_session() -> Generator[Session, None, None]:
    """Создать тестовую БД и вернуть сессию."""

    with session_scope(Base, engine, SessionLocal) as session:
        yield session


@pytest.fixture(scope="function")
def client(db_session: Session) -> Generator[TestClient, None, None]:
    """Подменить зависимость get_db для FastAPI-клиента."""

    with test_client_with_session(app, get_db, db_session) as test_client:
        yield test_client


class TestTaskCompletionDates:
    """Проверка изменения reminder_time при выполнении задач."""

    def test_recurring_due_today_keeps_reminder(self, client: TestClient) -> None:
        """Ежедневная задача с reminder_time сегодня не смещается при выполнении."""

        reminder_time = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        payload = {
            "title": "Daily task",
            "task_type": "recurring",
            "recurrence_type": "daily",
            "recurrence_interval": 1,
            "reminder_time": isoformat(reminder_time),
        }

        created = client.post(api_path("/tasks/"), json=payload).json()
        completed = client.post(api_path(f"/tasks/{created['id']}/complete")).json()

        assert completed["completed"] is True
        assert completed["reminder_time"] == created["reminder_time"]

    def test_recurring_overdue_keeps_reminder(self, client: TestClient) -> None:
        """Просроченная задача не смещает reminder_time при выполнении."""

        reminder_time = (datetime.now() - timedelta(days=1)).replace(hour=10, minute=0, second=0, microsecond=0)
        payload = {
            "title": "Overdue task",
            "task_type": "recurring",
            "recurrence_type": "daily",
            "recurrence_interval": 1,
            "reminder_time": isoformat(reminder_time),
        }

        created = client.post(api_path("/tasks/"), json=payload).json()
        completed = client.post(api_path(f"/tasks/{created['id']}/complete")).json()

        assert completed["reminder_time"] == created["reminder_time"]

    def test_one_time_completion_marks_inactive(self, client: TestClient) -> None:
        """Разовая задача после выполнения становится неактивной и не смещает reminder_time."""

        reminder_time = datetime.now().replace(hour=7, minute=45, second=0, microsecond=0)
        payload = {
            "title": "One-time task",
            "task_type": "one_time",
            "reminder_time": isoformat(reminder_time),
        }

        created = client.post(api_path("/tasks/"), json=payload).json()
        completed = client.post(api_path(f"/tasks/{created['id']}/complete")).json()

        assert completed["reminder_time"] == created["reminder_time"]
        assert completed["completed"] is True
        assert completed["active"] is False

    def test_uncomplete_restores_flags(self, client: TestClient, db_session: Session) -> None:
        """Отмена выполнения возвращает флаги и reminder_time."""

        reminder_time = datetime.now().replace(hour=15, minute=0, second=0, microsecond=0)
        payload = {
            "title": "Undo recurring",
            "task_type": "recurring",
            "recurrence_type": "daily",
            "recurrence_interval": 1,
            "reminder_time": isoformat(reminder_time),
        }

        created = client.post(api_path("/tasks/"), json=payload).json()
        client.post(api_path(f"/tasks/{created['id']}/complete"))

        response = client.post(api_path(f"/tasks/{created['id']}/uncomplete"))
        data = response.json()

        assert data["completed"] is False
        assert data["reminder_time"] == created["reminder_time"]

        # Проверяем в БД, что состояние согласовано
        task = db_session.query(Task).filter(Task.id == created["id"]).first()
        assert task is not None
        assert task.completed is False
        assert task.reminder_time == reminder_time
