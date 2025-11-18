"""Unit tests for TaskService."""

from collections.abc import Generator
from datetime import datetime, timedelta
from typing import TYPE_CHECKING

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from backend.database import Base
from backend.models.task import RecurrenceType, TaskType
from backend.schemas.task import TaskCreate, TaskUpdate
from backend.services.task_service import TaskService

if TYPE_CHECKING:
    from _pytest.capture import CaptureFixture
    from _pytest.fixtures import FixtureRequest
    from _pytest.logging import LogCaptureFixture
    from _pytest.monkeypatch import MonkeyPatch
    from pytest_mock.plugin import MockerFixture
    from sqlalchemy.orm import Session


# Test database
SQLALCHEMY_DATABASE_URL = "sqlite:///./test_task_service.db"

engine = create_engine(
    SQLALCHEMY_DATABASE_URL, connect_args={"check_same_thread": False}
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


@pytest.fixture(scope="module")
def db_setup() -> Generator[None, None, None]:
    """Create test database schema once per module."""
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


@pytest.fixture(scope="function")
def db_session(db_setup: None) -> Generator["Session", None, None]:
    """Create test database session and clean data between tests using optimized DELETE."""
    from sqlalchemy import text

    db = TestingSessionLocal()
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


class TestTaskService:
    """Unit tests for TaskService."""

    def test_create_task(self, db_session: "Session") -> None:
        """Test creating a task."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="Test Task",
            description="Test Description",
            task_type=TaskType.ONE_TIME,
            reminder_time=today,
        )
        
        task = TaskService.create_task(db_session, task_data)
        
        assert task.id is not None
        assert task.title == "Test Task"
        assert task.description == "Test Description"
        assert task.task_type == TaskType.ONE_TIME
        assert task.reminder_time == today
        assert task.active is True

    def test_get_task(self, db_session: "Session") -> None:
        """Test getting a task by ID."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="Test Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today,
        )
        
        created_task = TaskService.create_task(db_session, task_data)
        task = TaskService.get_task(db_session, created_task.id)
        
        assert task is not None
        assert task.id == created_task.id
        assert task.title == "Test Task"

    def test_get_task_not_found(self, db_session: "Session") -> None:
        """Test getting a non-existent task."""
        task = TaskService.get_task(db_session, 999)
        assert task is None

    def test_get_all_tasks(self, db_session: "Session") -> None:
        """Test getting all tasks."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        
        task1_data = TaskCreate(
            title="Task 1",
            task_type=TaskType.ONE_TIME,
            reminder_time=today,
        )
        task2_data = TaskCreate(
            title="Task 2",
            task_type=TaskType.ONE_TIME,
            reminder_time=today + timedelta(days=1),
        )
        
        TaskService.create_task(db_session, task1_data)
        TaskService.create_task(db_session, task2_data)
        
        tasks = TaskService.get_all_tasks(db_session)
        
        assert len(tasks) == 2
        assert tasks[0].title == "Task 1"
        assert tasks[1].title == "Task 2"

    def test_get_all_tasks_active_only(self, db_session: "Session") -> None:
        """Test getting only active tasks."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        
        task1_data = TaskCreate(
            title="Active Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today,
        )
        task2_data = TaskCreate(
            title="Inactive Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today,
        )
        
        task1 = TaskService.create_task(db_session, task1_data)
        task2 = TaskService.create_task(db_session, task2_data)
        
        # Update task2 to be inactive
        update_data = TaskUpdate(active=False)
        TaskService.update_task(db_session, task2.id, update_data)
        
        tasks = TaskService.get_all_tasks(db_session, active_only=True)
        
        assert len(tasks) == 1
        assert tasks[0].title == "Active Task"

    def test_update_task(self, db_session: "Session") -> None:
        """Test updating a task."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="Original Title",
            task_type=TaskType.ONE_TIME,
            reminder_time=today,
        )
        
        created_task = TaskService.create_task(db_session, task_data)
        
        update_data = TaskUpdate(title="Updated Title")
        updated_task = TaskService.update_task(db_session, created_task.id, update_data)
        
        assert updated_task is not None
        assert updated_task.title == "Updated Title"
        assert updated_task.id == created_task.id

    def test_update_task_not_found(self, db_session: "Session") -> None:
        """Test updating a non-existent task."""
        update_data = TaskUpdate(title="Updated Title")
        updated_task = TaskService.update_task(db_session, 999, update_data)
        assert updated_task is None

    def test_delete_task(self, db_session: "Session") -> None:
        """Test deleting a task."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="Test Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today,
        )
        
        created_task = TaskService.create_task(db_session, task_data)
        success = TaskService.delete_task(db_session, created_task.id)
        
        assert success is True
        
        task = TaskService.get_task(db_session, created_task.id)
        assert task is None

    def test_delete_task_not_found(self, db_session: "Session") -> None:
        """Test deleting a non-existent task."""
        success = TaskService.delete_task(db_session, 999)
        assert success is False

    def test_complete_task_one_time(self, db_session: "Session") -> None:
        """Test completing a one-time task."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="One-time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today,
        )
        
        created_task = TaskService.create_task(db_session, task_data)
        completed_task = TaskService.complete_task(db_session, created_task.id)
        
        assert completed_task is not None
        assert completed_task.active is False
        assert completed_task.completed is True
        # Date should not change for one-time tasks
        assert completed_task.reminder_time.date() == today.date()

    def test_complete_task_recurring(self, db_session: "Session") -> None:
        """Test completing a recurring task due today."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="Daily Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=today,
        )
        
        created_task = TaskService.create_task(db_session, task_data)
        completed_task = TaskService.complete_task(db_session, created_task.id)
        
        assert completed_task is not None
        assert completed_task.completed is True
        # Date should not change immediately if due today
        assert completed_task.reminder_time.date() == today.date()

    def test_complete_task_recurring_future(self, db_session: "Session") -> None:
        """Test completing a recurring task due in the future."""
        tomorrow = datetime.now() + timedelta(days=1)
        tomorrow = tomorrow.replace(hour=9, minute=0, second=0, microsecond=0)
        
        task_data = TaskCreate(
            title="Daily Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=tomorrow,
        )
        
        created_task = TaskService.create_task(db_session, task_data)
        completed_task = TaskService.complete_task(db_session, created_task.id)
        
        assert completed_task is not None
        assert completed_task.completed is True
        # Пересчёта быть не должно сразу — он произойдёт централизованно в новый день
        assert completed_task.reminder_time == tomorrow

    def test_complete_task_interval(self, db_session: "Session") -> None:
        """Test completing an interval task due today."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=today,
        )
        
        created_task = TaskService.create_task(db_session, task_data)
        completed_task = TaskService.complete_task(db_session, created_task.id)
        
        assert completed_task is not None
        assert completed_task.completed is True
        # Немедленного сдвига не происходит — сдвиг будет в новый день
        assert completed_task.reminder_time == today

    def test_recalc_happens_on_new_day_centrally(self, db_session: "Session", monkeypatch: "MonkeyPatch") -> None:
        """Пересчёт дат происходит централизованно в новый день одним местом."""
        # Задача recurring: сегодня в 09:00, подтверждаем сегодня
        now = datetime.now().replace(second=0, microsecond=0)
        today_9 = now.replace(hour=9, minute=0)
        task_data = TaskCreate(
            title="Daily Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=today_9,
        )
        created_task = TaskService.create_task(db_session, task_data)
        # Подтверждаем — дата не должна меняться сразу
        completed_task = TaskService.complete_task(db_session, created_task.id)
        assert completed_task is not None
        assert completed_task.completed is True
        assert completed_task.reminder_time == today_9

        # Эмулируем наступление нового дня: заставляем сервис считать, что день сменился
        monkeypatch.setattr(TaskService, "_is_new_day", staticmethod(lambda _db: True))
        # Вызов централизованного обновления
        updated = TaskService.get_all_tasks(db_session)
        # Находим нашу задачу
        updated_task = next(t for t in updated if t.id == created_task.id)
        # Дата должна быть пересчитана на следующий цикл, а completed сброшен
        assert updated_task.reminder_time > today_9
        assert updated_task.completed is False

    def test_complete_task_not_found(self, db_session: "Session") -> None:
        """Test completing a non-existent task."""
        completed_task = TaskService.complete_task(db_session, 999)
        assert completed_task is None

    def test_calculate_next_due_date_daily(self) -> None:
        """Test calculating next due date for daily recurrence."""
        today = datetime(2025, 1, 1, 9, 0, 0)
        next_date = TaskService._calculate_next_due_date(
            today, RecurrenceType.DAILY, 1
        )
        
        assert next_date == today + timedelta(days=1)

    def test_calculate_next_due_date_weekly(self) -> None:
        """Test calculating next due date for weekly recurrence."""
        today = datetime(2025, 1, 1, 9, 0, 0)
        next_date = TaskService._calculate_next_due_date(
            today,
            RecurrenceType.WEEKLY,
            1,
            reminder_time=today,
        )
        
        assert next_date == today + timedelta(weeks=1)

    def test_calculate_next_due_date_monthly(self) -> None:
        """Test calculating next due date for monthly recurrence."""
        today = datetime(2025, 1, 1, 9, 0, 0)
        next_date = TaskService._calculate_next_due_date(
            today, RecurrenceType.MONTHLY, 1
        )
        
        # Monthly uses 30 days approximation
        assert next_date == today + timedelta(days=30)

    def test_calculate_next_due_date_yearly(self) -> None:
        """Test calculating next due date for yearly recurrence."""
        today = datetime(2025, 1, 1, 9, 0, 0)
        next_date = TaskService._calculate_next_due_date(
            today, RecurrenceType.YEARLY, 1
        )
        
        assert next_date == today + timedelta(days=365)

    def test_calculate_next_due_date_default(self) -> None:
        """Test calculating next due date with default (daily)."""
        today = datetime(2025, 1, 1, 9, 0, 0)
        next_date = TaskService._calculate_next_due_date(
            today, None, 1
        )
        
        assert next_date == today + timedelta(days=1)

    def test_get_upcoming_tasks(self, db_session: "Session") -> None:
        """Test getting upcoming tasks."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        
        task1_data = TaskCreate(
            title="Task Today",
            task_type=TaskType.ONE_TIME,
            reminder_time=today,
            # active defaults to True
        )
        task2_data = TaskCreate(
            title="Task Tomorrow",
            task_type=TaskType.ONE_TIME,
            reminder_time=today + timedelta(days=1),
            # active defaults to True
        )
        task3_data = TaskCreate(
            title="Task Next Week",
            task_type=TaskType.ONE_TIME,
            reminder_time=today + timedelta(days=7),
            # active defaults to True
        )
        
        TaskService.create_task(db_session, task1_data)
        TaskService.create_task(db_session, task2_data)
        TaskService.create_task(db_session, task3_data)
        
        upcoming_tasks = TaskService.get_upcoming_tasks(db_session, days_ahead=3)
        
        # Should return tasks due in next 3 days
        assert len(upcoming_tasks) == 2
        assert any(task.title == "Task Today" for task in upcoming_tasks)
        assert any(task.title == "Task Tomorrow" for task in upcoming_tasks)

    def test_get_today_task_ids_skips_future_recurring(self, db_session: "Session") -> None:
        """Ensure recurring tasks scheduled in the future are not returned in 'today' view."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        future_due = today + timedelta(days=1)

        future_recurring = TaskCreate(
            title="Future recurring task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=future_due,
            # active defaults to True
        )

        TaskService.create_task(db_session, future_recurring)

        today_task_ids = TaskService.get_today_task_ids(db_session)

        assert today_task_ids == []

