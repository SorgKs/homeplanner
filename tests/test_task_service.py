"""Юнит-тесты для TaskService с учётом новой модели задач."""

from __future__ import annotations

from collections.abc import Generator
from datetime import datetime, timedelta
from typing import TYPE_CHECKING

import pytest
from sqlalchemy.orm import Session

from backend.database import Base
from backend.models.task import RecurrenceType, TaskType
from backend.schemas.task import TaskCreate, TaskUpdate
from backend.services.task_service import TaskService
from tests.utils import (
    create_sqlite_engine,
    normalize_datetime,
    session_scope,
)

if TYPE_CHECKING:
    from _pytest.fixtures import FixtureRequest


engine, SessionLocal = create_sqlite_engine("test_task_service.db")


@pytest.fixture(scope="function")
def db_session() -> Generator[Session, None, None]:
    """Создать in-memory БД для тестов."""

    with session_scope(Base, engine, SessionLocal) as session:
        yield session


class TestTaskService:
    """Проверка CRUD и бизнес-логики сервиса задач."""

    def test_create_task_defaults(self, db_session: Session) -> None:
        """Создание разовой задачи заполняет дефолтные флаги."""

        reminder_time = normalize_datetime(datetime.now().replace(hour=9, minute=0, second=0))
        payload = TaskCreate(
            title="Test Task",
            description="Test Description",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
        )
        
        task = TaskService.create_task(db_session, payload)
        
        assert task.id is not None
        assert task.title == payload.title
        assert task.reminder_time == reminder_time
        assert task.completed is False
        assert task.active is True

    def test_get_all_tasks_active_filter(self, db_session: Session) -> None:
        """active_only=True возвращает только активные задачи."""

        reminder_time = normalize_datetime(datetime.now().replace(hour=9, minute=0, second=0))
        first = TaskService.create_task(
            db_session,
            TaskCreate(
                title="Active",
            task_type=TaskType.ONE_TIME,
                reminder_time=reminder_time,
            ),
        )
        second = TaskService.create_task(
            db_session,
            TaskCreate(
                title="Inactive",
            task_type=TaskType.ONE_TIME,
                reminder_time=reminder_time,
            ),
        )
        
        TaskService.update_task(
            db_session,
            second.id,
            TaskUpdate(revision=second.revision, active=False),
        )

        active = TaskService.get_all_tasks(db_session, active_only=True)
        assert [task.title for task in active] == [first.title]

    def test_complete_one_time_task(self, db_session: Session) -> None:
        """complete_task делает разовую задачу выполненной и неактивной."""

        reminder_time = normalize_datetime(datetime.now().replace(hour=8, minute=0, second=0))
        created = TaskService.create_task(
            db_session,
            TaskCreate(
                title="One time",
            task_type=TaskType.ONE_TIME,
                reminder_time=reminder_time,
            ),
        )

        completed = TaskService.complete_task(db_session, created.id)
        
        assert completed is not None
        assert completed.completed is True
        assert completed.active is False
        assert completed.reminder_time == reminder_time

    def test_complete_recurring_future(self, db_session: Session) -> None:
        """Для будущей повторяющейся задачи reminder_time сдвигается вперёд."""

        reminder_time = normalize_datetime((datetime.now() + timedelta(days=1)).replace(hour=9))
        created = TaskService.create_task(
            db_session,
            TaskCreate(
                title="Recurring",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
                reminder_time=reminder_time,
            ),
        )

        completed = TaskService.complete_task(db_session, created.id)
        
        assert completed is not None
        assert completed.completed is True
        assert completed.reminder_time > reminder_time

    def test_uncomplete_restores_flags(self, db_session: Session) -> None:
        """uncomplete_task сбрасывает completed и активирует задачу."""

        reminder_time = normalize_datetime(datetime.now().replace(hour=11, minute=30, second=0))
        created = TaskService.create_task(
            db_session,
            TaskCreate(
                title="Undo task",
                task_type=TaskType.ONE_TIME,
                reminder_time=reminder_time,
            ),
        )
        TaskService.complete_task(db_session, created.id)

        reverted = TaskService.uncomplete_task(db_session, created.id)
        
        assert reverted is not None
        assert reverted.completed is False
        assert reverted.active is True
        assert reverted.reminder_time == reminder_time

    def test_get_upcoming_tasks_filters_by_window(self, db_session: Session) -> None:
        """get_upcoming_tasks возвращает задачи в окне."""

        now = normalize_datetime(datetime.now().replace(hour=7, minute=0, second=0))
        TaskService.create_task(
            db_session,
            TaskCreate(
                title="Today",
                task_type=TaskType.ONE_TIME,
                reminder_time=now,
            ),
        )
        TaskService.create_task(
            db_session,
            TaskCreate(
                title="Tomorrow",
                task_type=TaskType.ONE_TIME,
                reminder_time=now + timedelta(days=1),
            ),
        )
        TaskService.create_task(
            db_session,
            TaskCreate(
                title="Next Week",
                task_type=TaskType.ONE_TIME,
                reminder_time=now + timedelta(days=7),
            ),
        )

        upcoming = TaskService.get_upcoming_tasks(db_session, days_ahead=2)
        titles = {task.title for task in upcoming}

        assert titles == {"Today", "Tomorrow"}

    def test_calculate_next_reminder_time_daily(self) -> None:
        """Проверка расчёта ежедневного повторения."""

        base = datetime(2025, 1, 1, 9, 0, 0)
        result = TaskService._calculate_next_reminder_time(base, RecurrenceType.DAILY, 1, base)
        assert result == datetime(2025, 1, 2, 9, 0, 0)

    def test_calculate_next_reminder_time_weekly(self) -> None:
        """Проверка расчёта еженедельного повторения."""

        base = datetime(2025, 1, 1, 9, 0, 0)  # Среда
        reminder = datetime(2025, 1, 5, 9, 0, 0)  # Воскресенье
        result = TaskService._calculate_next_reminder_time(base, RecurrenceType.WEEKLY, 1, reminder)
        assert result.weekday() == reminder.weekday()
        assert result > base

    def test_calculate_next_reminder_time_monthly(self) -> None:
        """Проверка расчёта ежемесячного повторения."""

        base = datetime(2025, 1, 15, 12, 0, 0)
        reminder = datetime(2025, 1, 15, 12, 0, 0)
        result = TaskService._calculate_next_reminder_time(base, RecurrenceType.MONTHLY, 1, reminder)
        assert result.month in {1, 2}
        assert result.day == reminder.day or result.day >= 28

