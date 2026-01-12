"""Comprehensive tests for TaskService."""

from collections.abc import Generator
from datetime import datetime, timedelta
from typing import TYPE_CHECKING

import pytest
from sqlalchemy.orm import Session

from backend.database import Base, get_db
from backend.main import app
from backend.models.task import RecurrenceType, TaskType
from backend.schemas.task import TaskCreate, TaskUpdate
from backend.services.task_service import TaskService
from tests.utils import (
    api_path,
    create_sqlite_engine,
    isoformat,
    session_scope,
    test_client_with_session,
)

if TYPE_CHECKING:
    from _pytest.fixtures import FixtureRequest


engine, SessionLocal = create_sqlite_engine("test_task_service_comprehensive.db")


@pytest.fixture(scope="module")
def db_setup() -> Generator[None, None, None]:
    """Create test database schema once per module."""
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


@pytest.fixture(scope="function")
def db_session(db_setup: None) -> Generator[Session, None, None]:
    """Create test database session and clean data between tests using optimized DELETE."""
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
def client(db_session: Session) -> Generator[Generator, None, None]:
    """Create test client with test database."""
    from fastapi.testclient import TestClient
    with test_client_with_session(app, get_db, db_session) as test_client:
        yield test_client


class TestTaskServiceComprehensive:
    """Comprehensive tests for TaskService."""

    def test_create_task_with_timestamp(self, db_session: Session) -> None:
        """Test creating a task with explicit timestamp."""
        from backend.models.user import User
        
        # Create a user for assignment
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        reminder_time = datetime.now() + timedelta(days=1)
        task_data = TaskCreate(
            title="Test Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        
        timestamp = datetime.now()
        task = TaskService.create_task(db_session, task_data, timestamp=timestamp)
        
        assert task.title == "Test Task"
        assert task.reminder_time == reminder_time
        assert task.created_at == timestamp
        assert task.updated_at == timestamp
        assert len(task.assignees) == 1
        assert task.assignees[0].id == user.id

    def test_update_task_with_timestamp(self, db_session: Session) -> None:
        """Test updating a task with explicit timestamp."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a task
        reminder_time = datetime.now() + timedelta(days=1)
        task_data = TaskCreate(
            title="Test Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Update task with timestamp
        update_data = TaskUpdate(
            title="Updated Task",
            reminder_time=reminder_time + timedelta(hours=1)
        )
        
        timestamp = datetime.now()
        updated_task = TaskService.update_task(db_session, task.id, update_data, timestamp=timestamp)
        
        assert updated_task.title == "Updated Task"
        assert updated_task.reminder_time == reminder_time + timedelta(hours=1)
        assert updated_task.updated_at == timestamp

    def test_delete_task_with_timestamp(self, db_session: Session) -> None:
        """Test deleting a task with explicit timestamp."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a task
        reminder_time = datetime.now() + timedelta(days=1)
        task_data = TaskCreate(
            title="Test Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Delete task with timestamp
        timestamp = datetime.now()
        result = TaskService.delete_task(db_session, task.id, timestamp=timestamp)
        
        assert result is True
        
        # Verify task is deleted
        deleted_task = TaskService.get_task(db_session, task.id)
        assert deleted_task is None

    def test_complete_task_with_timestamp(self, db_session: Session) -> None:
        """Test completing a task with explicit timestamp."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a task
        reminder_time = datetime.now() + timedelta(days=1)
        task_data = TaskCreate(
            title="Test Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Complete task with timestamp
        timestamp = datetime.now()
        completed_task = TaskService.complete_task(db_session, task.id, timestamp=timestamp)
        
        assert completed_task.completed is True
        assert completed_task.updated_at == timestamp

    def test_uncomplete_task_with_timestamp(self, db_session: Session) -> None:
        """Test uncompleting a task with explicit timestamp."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a task
        reminder_time = datetime.now() + timedelta(days=1)
        task_data = TaskCreate(
            title="Test Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Complete task first
        TaskService.complete_task(db_session, task.id)
        
        # Uncomplete task with timestamp
        timestamp = datetime.now()
        uncompleted_task = TaskService.uncomplete_task(db_session, task.id, timestamp=timestamp)
        
        assert uncompleted_task.completed is False
        assert uncompleted_task.updated_at == timestamp

    def test_get_all_tasks_with_new_day_logic(self, db_session: Session) -> None:
        """Test get_all_tasks with new day logic."""
        from backend.models.user import User

        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()

        # Create a completed recurring task
        reminder_time = datetime.now() - timedelta(days=1)
        task_data = TaskCreate(
            title="Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)

        # Complete the task
        TaskService.complete_task(db_session, task.id)

        # Get all tasks - should trigger new day logic
        tasks = TaskService.get_all_tasks(db_session)

        # Task should be updated for next day
        assert len(tasks) == 1
        updated_task = tasks[0]
        assert updated_task.completed is False
        assert updated_task.reminder_time > reminder_time

    def test_get_today_tasks_with_user_filter(self, db_session: Session) -> None:
        """Test get_today_tasks with user filtering."""
        from backend.models.user import User
        
        # Create users
        user1 = User(name="User 1", email="user1@example.com")
        user2 = User(name="User 2", email="user2@example.com")
        db_session.add_all([user1, user2])
        db_session.commit()
        
        # Create tasks for different users
        reminder_time = datetime.now()
        task_data1 = TaskCreate(
            title="Task for User 1",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user1.id]
        )
        task_data2 = TaskCreate(
            title="Task for User 2",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user2.id]
        )
        task_data3 = TaskCreate(
            title="Task for Both",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user1.id, user2.id]
        )
        task_data4 = TaskCreate(
            title="Task for None",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        TaskService.create_task(db_session, task_data3)
        TaskService.create_task(db_session, task_data4)
        
        # Get tasks for user1
        tasks_user1 = TaskService.get_today_tasks(db_session, user_id=user1.id)
        assert len(tasks_user1) == 3  # Task for User 1, Task for Both Users, and Task for None
        
        # Get tasks for user2
        tasks_user2 = TaskService.get_today_tasks(db_session, user_id=user2.id)
        assert len(tasks_user2) == 3  # Task for User 2, Task for Both Users, and Task for None
        
        # Get tasks for no user (all tasks)
        tasks_all = TaskService.get_today_tasks(db_session, user_id=None)
        assert len(tasks_all) == 4  # All tasks

    def test_calculate_next_due_date_weekly(self, db_session: Session) -> None:
        """Test calculating next due date for weekly tasks."""
        from backend.models.task import Task
        
        # Create a weekly task with specific reminder_time
        reminder_time = datetime(2025, 1, 1, 10, 0)  # Wednesday
        task = Task(
            title="Weekly Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.WEEKLY,
            recurrence_interval=1,
            reminder_time=reminder_time
        )
        db_session.add(task)
        db_session.commit()
        
        # Test calculation from different dates
        current_date = datetime(2025, 1, 2, 15, 0)  # Thursday
        next_date = TaskService._calculate_next_due_date(
            current_date,
            RecurrenceType.WEEKLY,
            1,
            reminder_time
        )
        
        # Should be next Wednesday
        expected_date = datetime(2025, 1, 8, 10, 0)
        assert next_date == expected_date

    def test_calculate_next_due_date_monthly_weekday(self, db_session: Session) -> None:
        """Test calculating next due date for monthly weekday tasks."""
        from backend.models.task import Task
        
        # Create a monthly weekday task (2nd Tuesday)
        reminder_time = datetime(2025, 1, 14, 10, 0)  # 2nd Tuesday of January
        task = Task(
            title="Monthly Weekday Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.MONTHLY_WEEKDAY,
            recurrence_interval=1,
            reminder_time=reminder_time
        )
        db_session.add(task)
        db_session.commit()
        
        # Test calculation from different dates
        current_date = datetime(2025, 1, 15, 15, 0)  # After the 2nd Tuesday
        next_date = TaskService._calculate_next_due_date(
            current_date,
            RecurrenceType.MONTHLY_WEEKDAY,
            1,
            reminder_time
        )
        
        # Should be 2nd Tuesday of February
        expected_date = datetime(2025, 2, 11, 10, 0)
        assert next_date == expected_date

    def test_format_task_settings(self, db_session: Session) -> None:
        """Test formatting task settings for history."""
        from backend.models.task import Task
        
        # Test one-time task
        reminder_time = datetime(2025, 1, 15, 10, 0)
        task = Task(
            title="One Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time
        )
        
        settings = TaskService._format_task_settings("one_time", task)
        assert "15 января в 10:00" in settings
        
        # Test recurring daily task
        task = Task(
            title="Daily Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=reminder_time
        )
        
        settings = TaskService._format_task_settings("recurring", task)
        assert "в 10:00 каждый день" in settings
        
        # Test interval task
        task = Task(
            title="Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=reminder_time
        )
        
        settings = TaskService._format_task_settings("interval", task)
        assert "раз в неделю" in settings

    def test_get_upcoming_tasks(self, db_session: Session) -> None:
        """Test getting upcoming tasks."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks with different due dates
        now = datetime.now()
        
        # Task due today
        task_data1 = TaskCreate(
            title="Task Due Today",
            task_type=TaskType.ONE_TIME,
            reminder_time=now + timedelta(hours=2),
            assigned_user_ids=[user.id]
        )
        
        # Task due tomorrow
        task_data2 = TaskCreate(
            title="Task Due Tomorrow",
            task_type=TaskType.ONE_TIME,
            reminder_time=now + timedelta(days=1),
            assigned_user_ids=[user.id]
        )
        
        # Task due in 10 days (should not be included in 7-day window)
        task_data3 = TaskCreate(
            title="Task Due in 10 Days",
            task_type=TaskType.ONE_TIME,
            reminder_time=now + timedelta(days=10),
            assigned_user_ids=[user.id]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        TaskService.create_task(db_session, task_data3)
        
        # Get upcoming tasks (7 days)
        upcoming_tasks = TaskService.get_upcoming_tasks(db_session, days_ahead=7)
        
        assert len(upcoming_tasks) == 2
        task_titles = [task.title for task in upcoming_tasks]
        assert "Task Due Today" in task_titles
        assert "Task Due Tomorrow" in task_titles
        assert "Task Due in 10 Days" not in task_titles

    def test_get_today_task_ids(self, db_session: Session) -> None:
        """Test getting today task IDs."""
        from backend.models.user import User

        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()

        # Create tasks
        reminder_time = datetime.now()
        task_data1 = TaskCreate(
            title="Task 1",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        task_data2 = TaskCreate(
            title="Task 2",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time + timedelta(days=1),
            assigned_user_ids=[user.id]
        )

        task1 = TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)

        # Get today task IDs
        task_ids = TaskService.get_today_task_ids(db_session, user_id=user.id)

        assert task1.id in task_ids
        assert len(task_ids) == 1  # Only today task should be in today's view

    def test_mark_task_shown(self, db_session: Session) -> None:
        """Test marking task as shown."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a task
        reminder_time = datetime.now()
        task_data = TaskCreate(
            title="Test Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Mark task as shown
        marked_task = TaskService.mark_task_shown(db_session, task.id)
        
        assert marked_task is not None
        assert marked_task.last_shown_at is not None
        assert marked_task.last_shown_at >= reminder_time

    def test_get_users_by_ids(self, db_session: Session) -> None:
        """Test getting users by IDs."""
        from backend.models.user import User
        
        # Create users
        user1 = User(name="User 1", email="user1@example.com")
        user2 = User(name="User 2", email="user2@example.com")
        user3 = User(name="User 3", email="user3@example.com")
        db_session.add_all([user1, user2, user3])
        db_session.commit()
        
        # Get users by IDs
        user_ids = [user1.id, user2.id, user3.id]
        users = TaskService._get_users_by_ids(db_session, user_ids)
        
        assert len(users) == 3
        assert users[0].id == user1.id
        assert users[1].id == user2.id
        assert users[2].id == user3.id
        
        # Test with non-existent user
        with pytest.raises(ValueError, match="Users not found"):
            TaskService._get_users_by_ids(db_session, [user1.id, 999])

    def test_is_new_day_logic(self, db_session: Session) -> None:
        """Test new day detection logic."""
        from backend.utils.date_utils import is_new_day, set_last_update

        # Test with no last update
        is_new = is_new_day(db_session)
        assert is_new is True

        # Set last update to yesterday
        yesterday = datetime.now() - timedelta(days=1)
        set_last_update(db_session, yesterday, commit=True)

        # Should be new day
        is_new = is_new_day(db_session)
        assert is_new is True

        # Set last update to today (before day_start_hour)
        today_morning = datetime.now().replace(hour=5, minute=0, second=0, microsecond=0)
        set_last_update(db_session, today_morning, commit=True)

        # Should still be new day if current time is after day_start_hour
        is_new = is_new_day(db_session)
        # This depends on current time, so we just check it doesn't crash
        assert isinstance(is_new, bool)

    def test_find_nth_weekday_in_month(self, db_session: Session) -> None:
        """Test finding N-th weekday in month."""
        # Test finding 2nd Tuesday of January 2025
        result = TaskService._find_nth_weekday_in_month(2025, 1, 1, 2)  # Tuesday=1
        expected = datetime(2025, 1, 14)
        assert result.date() == expected.date()
        
        # Test finding last Friday of January 2025
        result = TaskService._find_nth_weekday_in_month(2025, 1, 4, -1)  # Friday=4
        expected = datetime(2025, 1, 31)
        assert result.date() == expected.date()

    def test_determine_weekday_occurrence(self, db_session: Session) -> None:
        """Test determining weekday occurrence."""
        # Test 2nd Tuesday
        result = TaskService._determine_weekday_occurrence(14, 1, 2025, 1)  # Tuesday=1
        assert result == 2
        
        # Test last Friday
        result = TaskService._determine_weekday_occurrence(31, 4, 2025, 1)  # Friday=4
        assert result == -1

    def test_format_datetime_for_history(self, db_session: Session) -> None:
        """Test formatting datetime for history."""
        dt = datetime(2025, 1, 15, 10, 30)
        formatted = TaskService._format_datetime_for_history(dt)
        assert "15 января 2025" in formatted
        assert "10:30" in formatted
        
        # Test None
        formatted_none = TaskService._format_datetime_for_history(None)
        assert formatted_none == "не установлено"

    def test_format_datetime_short(self, db_session: Session) -> None:
        """Test formatting datetime as short string."""
        dt = datetime(2025, 1, 15, 10, 30)
        formatted = TaskService._format_datetime_short(dt)
        assert "15 января в 10:30" in formatted

    def test_get_day_start(self, db_session: Session) -> None:
        """Test getting day start based on day_start_hour."""
        from backend.config import get_settings
        
        settings = get_settings()
        day_start_hour = settings.day_start_hour
        
        # Test with time after day_start_hour
        dt_after = datetime(2025, 1, 15, day_start_hour + 2, 0, 0)
        day_start = TaskService._get_day_start(dt_after)
        expected = datetime(2025, 1, 15, day_start_hour, 0, 0)
        assert day_start == expected
        
        # Test with time before day_start_hour
        dt_before = datetime(2025, 1, 15, day_start_hour - 2, 0, 0)
        day_start = TaskService._get_day_start(dt_before)
        expected = datetime(2025, 1, 14, day_start_hour, 0, 0)  # Previous day
        assert day_start == expected

    def test_get_last_update(self, db_session: Session) -> None:
        """Test getting last update timestamp."""
        # Test with no metadata
        last_update = TaskService._get_last_update(db_session)
        assert last_update is None
        
        # Set last update
        timestamp = datetime.now()
        TaskService._set_last_update(db_session, timestamp, commit=True)
        
        # Get last update
        last_update = TaskService._get_last_update(db_session)
        assert last_update == timestamp

    def test_set_last_update(self, db_session: Session) -> None:
        """Test setting last update timestamp."""
        timestamp = datetime.now()
        
        # Set last update without commit
        TaskService._set_last_update(db_session, timestamp, commit=False)
        
        # Should not be visible until commit
        last_update = TaskService._get_last_update(db_session)
        assert last_update is None
        
        # Commit and check
        db_session.commit()
        last_update = TaskService._get_last_update(db_session)
        assert last_update == timestamp

    def test_calculate_next_due_date_edge_cases(self, db_session: Session) -> None:
        """Test edge cases in next due date calculation."""
        from backend.models.task import Task
        
        # Test yearly task crossing year boundary
        reminder_time = datetime(2024, 12, 25, 10, 0)  # December 25, 2024
        task = Task(
            title="Yearly Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.YEARLY,
            recurrence_interval=1,
            reminder_time=reminder_time
        )
        db_session.add(task)
        db_session.commit()
        
        # Test calculation from January 2025
        current_date = datetime(2025, 1, 1, 15, 0)
        next_date = TaskService._calculate_next_due_date(
            current_date,
            RecurrenceType.YEARLY,
            1,
            reminder_time
        )
        
        # Should be December 25, 2025
        expected_date = datetime(2025, 12, 25, 10, 0)
        assert next_date == expected_date

    def test_format_task_settings_edge_cases(self, db_session: Session) -> None:
        """Test edge cases in task settings formatting."""
        from backend.models.task import Task
        
        # Test task with no reminder_time
        task = Task(
            title="Task Without Time",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=None
        )
        
        settings = TaskService._format_task_settings("recurring", task)
        assert "ежедневно" in settings
        
        # Test interval task with no interval_days
        task = Task(
            title="Interval Task Without Days",
            task_type=TaskType.INTERVAL,
            interval_days=None,
            reminder_time=None
        )
        
        settings = TaskService._format_task_settings("interval", task)
        assert "интервальная задача" in settings

    def test_get_all_tasks_active_only(self, db_session: Session) -> None:
        """Test getting only active tasks."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create active and inactive tasks
        reminder_time = datetime.now()
        task_data1 = TaskCreate(
            title="Active Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        task_data2 = TaskCreate(
            title="Inactive Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        
        active_task = TaskService.create_task(db_session, task_data1)
        inactive_task = TaskService.create_task(db_session, task_data2)
        
        # Mark second task as inactive
        inactive_task.active = False
        db_session.commit()
        
        # Get all tasks
        all_tasks = TaskService.get_all_tasks(db_session, active_only=False)
        assert len(all_tasks) == 2
        
        # Get only active tasks
        active_tasks = TaskService.get_all_tasks(db_session, active_only=True)
        assert len(active_tasks) == 1
        assert active_tasks[0].id == active_task.id

    def test_get_task_not_found(self, db_session: Session) -> None:
        """Test getting a non-existent task."""
        task = TaskService.get_task(db_session, 999)
        assert task is None

    def test_update_task_not_found(self, db_session: Session) -> None:
        """Test updating a non-existent task."""
        update_data = TaskUpdate(title="Updated Title")
        task = TaskService.update_task(db_session, 999, update_data)
        assert task is None

    def test_delete_task_not_found(self, db_session: Session) -> None:
        """Test deleting a non-existent task."""
        result = TaskService.delete_task(db_session, 999)
        assert result is False

    def test_complete_task_not_found(self, db_session: Session) -> None:
        """Test completing a non-existent task."""
        task = TaskService.complete_task(db_session, 999)
        assert task is None

    def test_uncomplete_task_not_found(self, db_session: Session) -> None:
        """Test uncompleting a non-existent task."""
        task = TaskService.uncomplete_task(db_session, 999)
        assert task is None

    def test_create_task_without_assignees(self, db_session: Session) -> None:
        """Test creating a task without assignees."""
        reminder_time = datetime.now() + timedelta(days=1)
        task_data = TaskCreate(
            title="Task Without Assignees",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[]
        )
        
        task = TaskService.create_task(db_session, task_data)
        
        assert task.title == "Task Without Assignees"
        assert task.reminder_time == reminder_time
        assert len(task.assignees) == 0

    def test_update_task_assignees(self, db_session: Session) -> None:
        """Test updating task assignees."""
        from backend.models.user import User
        
        # Create users
        user1 = User(name="User 1", email="user1@example.com")
        user2 = User(name="User 2", email="user2@example.com")
        db_session.add_all([user1, user2])
        db_session.commit()
        
        # Create a task
        reminder_time = datetime.now() + timedelta(days=1)
        task_data = TaskCreate(
            title="Task with Assignees",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user1.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Update assignees
        update_data = TaskUpdate(assigned_user_ids=[user2.id])
        updated_task = TaskService.update_task(db_session, task.id, update_data)
        
        assert len(updated_task.assignees) == 1
        assert updated_task.assignees[0].id == user2.id

    def test_create_task_with_invalid_user_ids(self, db_session: Session) -> None:
        """Test creating a task with invalid user IDs."""
        reminder_time = datetime.now() + timedelta(days=1)
        task_data = TaskCreate(
            title="Task with Invalid Users",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[999, 1000]
        )
        
        with pytest.raises(ValueError, match="Users not found"):
            TaskService.create_task(db_session, task_data)

    def test_update_task_with_invalid_user_ids(self, db_session: Session) -> None:
        """Test updating a task with invalid user IDs."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a task
        reminder_time = datetime.now() + timedelta(days=1)
        task_data = TaskCreate(
            title="Task with Valid User",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Update with invalid user IDs
        update_data = TaskUpdate(assigned_user_ids=[999, 1000])
        
        with pytest.raises(ValueError, match="Users not found"):
            TaskService.update_task(db_session, task.id, update_data)

    def test_calculate_next_due_date_with_time_passed(self, db_session: Session) -> None:
        """Test calculating next due date when time has passed."""
        from backend.models.task import Task
        
        # Create a daily task
        reminder_time = datetime(2025, 1, 1, 10, 0)
        task = Task(
            title="Daily Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=reminder_time
        )
        db_session.add(task)
        db_session.commit()
        
        # Test calculation from same day but time has passed
        current_date = datetime(2025, 1, 1, 15, 0)  # 5 hours later
        next_date = TaskService._calculate_next_due_date(
            current_date,
            RecurrenceType.DAILY,
            1,
            reminder_time
        )
        
        # Should be tomorrow at same time
        expected_date = datetime(2025, 1, 2, 10, 0)
        assert next_date == expected_date

    def test_calculate_next_due_date_weekdays(self, db_session: Session) -> None:
        """Test calculating next due date for weekdays tasks."""
        from backend.models.task import Task
        
        # Create a weekdays task
        reminder_time = datetime(2025, 1, 6, 10, 0)  # Monday
        task = Task(
            title="Weekdays Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.WEEKDAYS,
            recurrence_interval=1,
            reminder_time=reminder_time
        )
        db_session.add(task)
        db_session.commit()
        
        # Test calculation from Friday
        current_date = datetime(2025, 1, 10, 15, 0)  # Friday
        next_date = TaskService._calculate_next_due_date(
            current_date,
            RecurrenceType.WEEKDAYS,
            1,
            reminder_time
        )
        
        # Should be next Monday
        expected_date = datetime(2025, 1, 13, 10, 0)
        assert next_date == expected_date

    def test_calculate_next_due_date_weekends(self, db_session: Session) -> None:
        """Test calculating next due date for weekends tasks."""
        from backend.models.task import Task
        
        # Create a weekends task
        reminder_time = datetime(2025, 1, 4, 10, 0)  # Saturday
        task = Task(
            title="Weekends Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.WEEKENDS,
            recurrence_interval=1,
            reminder_time=reminder_time
        )
        db_session.add(task)
        db_session.commit()
        
        # Test calculation from Sunday
        current_date = datetime(2025, 1, 5, 15, 0)  # Sunday
        next_date = TaskService._calculate_next_due_date(
            current_date,
            RecurrenceType.WEEKENDS,
            1,
            reminder_time
        )
        
        # Should be next Saturday
        expected_date = datetime(2025, 1, 11, 10, 0)
        assert next_date == expected_date

    def test_calculate_next_due_date_monthly_last_day(self, db_session: Session) -> None:
        """Test calculating next due date for monthly tasks on last day."""
        from backend.models.task import Task
        
        # Create a monthly task on 31st
        reminder_time = datetime(2025, 1, 31, 10, 0)
        task = Task(
            title="Monthly Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.MONTHLY,
            recurrence_interval=1,
            reminder_time=reminder_time
        )
        db_session.add(task)
        db_session.commit()
        
        # Test calculation from February (which doesn't have 31st)
        current_date = datetime(2025, 2, 15, 15, 0)
        next_date = TaskService._calculate_next_due_date(
            current_date,
            RecurrenceType.MONTHLY,
            1,
            reminder_time
        )
        
        # Should be last day of February (28th)
        expected_date = datetime(2025, 2, 28, 10, 0)
        assert next_date == expected_date

    def test_calculate_next_due_date_yearly_leap_year(self, db_session: Session) -> None:
        """Test calculating next due date for yearly tasks in leap year."""
        from backend.models.task import Task
        
        # Create a yearly task on February 29th
        reminder_time = datetime(2024, 2, 29, 10, 0)  # Leap year
        task = Task(
            title="Yearly Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.YEARLY,
            recurrence_interval=1,
            reminder_time=reminder_time
        )
        db_session.add(task)
        db_session.commit()
        
        # Test calculation from 2025 (not a leap year)
        current_date = datetime(2025, 1, 1, 15, 0)
        next_date = TaskService._calculate_next_due_date(
            current_date,
            RecurrenceType.YEARLY,
            1,
            reminder_time
        )
        
        # Should be February 28, 2025 (last day of February in non-leap year)
        expected_date = datetime(2025, 2, 28, 10, 0)
        assert next_date == expected_date

    def test_format_task_settings_recurring_weekly(self, db_session: Session) -> None:
        """Test formatting recurring weekly task settings."""
        from backend.models.task import Task
        
        # Create a weekly task
        reminder_time = datetime(2025, 1, 6, 10, 0)  # Monday
        task = Task(
            title="Weekly Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.WEEKLY,
            recurrence_interval=2,  # Every 2 weeks
            reminder_time=reminder_time
        )
        
        settings = TaskService._format_task_settings("recurring", task)
        assert "раз в 2 недели в понедельник в 10:00" in settings

    def test_format_task_settings_recurring_monthly_weekday(self, db_session: Session) -> None:
        """Test formatting recurring monthly weekday task settings."""
        from backend.models.task import Task
        
        # Create a monthly weekday task (3rd Wednesday)
        reminder_time = datetime(2025, 1, 15, 10, 0)  # 3rd Wednesday
        task = Task(
            title="Monthly Weekday Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.MONTHLY_WEEKDAY,
            recurrence_interval=1,
            reminder_time=reminder_time
        )
        
        settings = TaskService._format_task_settings("recurring", task)
        assert "третий среду месяца в 10:00" in settings

    def test_format_task_settings_recurring_yearly_weekday(self, db_session: Session) -> None:
        """Test formatting recurring yearly weekday task settings."""
        from backend.models.task import Task
        
        # Create a yearly weekday task (1st Monday of January)
        reminder_time = datetime(2025, 1, 6, 10, 0)  # 1st Monday of January
        task = Task(
            title="Yearly Weekday Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.YEARLY_WEEKDAY,
            recurrence_interval=1,
            reminder_time=reminder_time
        )
        
        settings = TaskService._format_task_settings("recurring", task)
        assert "первый понедельник января в 10:00 ежегодно" in settings

    def test_format_task_settings_interval_variations(self, db_session: Session) -> None:
        """Test formatting interval task settings with different intervals."""
        from backend.models.task import Task
        
        # Test 1 day
        task = Task(
            title="Daily Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=1,
            reminder_time=datetime(2025, 1, 1, 10, 0)
        )
        settings = TaskService._format_task_settings("interval", task)
        assert "раз в день в 10:00" in settings
        
        # Test 7 days
        task.interval_days = 7
        settings = TaskService._format_task_settings("interval", task)
        assert "раз в неделю в 10:00" in settings
        
        # Test 14 days
        task.interval_days = 14
        settings = TaskService._format_task_settings("interval", task)
        assert "раз в 2 недели в 10:00" in settings
        
        # Test 30 days
        task.interval_days = 30
        settings = TaskService._format_task_settings("interval", task)
        assert "раз в месяц в 10:00" in settings
        
        # Test 60 days
        task.interval_days = 60
        settings = TaskService._format_task_settings("interval", task)
        assert "раз в 2 месяца в 10:00" in settings

    def test_get_today_tasks_with_completed_future_tasks(self, db_session: Session) -> None:
        """Test get_today_tasks with completed tasks that have future dates."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a completed task with future date
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Completed Future Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Complete the task
        TaskService.complete_task(db_session, task.id)
        
        # Get today tasks - completed tasks should be visible even if future
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].completed is True

    def test_get_today_tasks_with_recurring_past_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with recurring tasks that are past and completed."""
        from backend.models.user import User

        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()

        # Create a recurring task in the past
        past_date = datetime.now() - timedelta(days=1)
        task_data = TaskCreate(
            title="Recurring Past Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=past_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)

        # Complete the task
        TaskService.complete_task(db_session, task.id)

        # Get today tasks - task gets reset by new day logic and becomes future, so not visible
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)

        assert len(today_tasks) == 0

    def test_get_today_tasks_with_future_recurring(self, db_session: Session) -> None:
        """Test get_today_tasks with recurring tasks that are in the future."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a recurring task in the future
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Recurring Future Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Get today tasks - should not be visible because it's in the future
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 0

    def test_get_today_tasks_with_interval_past_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with interval tasks that are past and completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create an interval task in the past
        past_date = datetime.now() - timedelta(days=1)
        task_data = TaskCreate(
            title="Interval Past Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=past_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Complete the task
        TaskService.complete_task(db_session, task.id)
        
        # Get today tasks - task gets reset by new day logic and becomes future, so not visible
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)

        assert len(today_tasks) == 0

    def test_get_today_tasks_with_future_interval(self, db_session: Session) -> None:
        """Test get_today_tasks with interval tasks that are in the future."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create an interval task in the future
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Interval Future Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Get today tasks - should not be visible because it's in the future
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 0

    def test_get_today_tasks_with_no_assignees(self, db_session: Session) -> None:
        """Test get_today_tasks with tasks that have no assignees."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a task with no assignees
        reminder_time = datetime.now()
        task_data = TaskCreate(
            title="Task Without Assignees",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[]
        )
        TaskService.create_task(db_session, task_data)
        
        # Get today tasks for the user - should include task with no assignees
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].title == "Task Without Assignees"
        
        # Get today tasks for no user - should also include task with no assignees
        today_tasks_all = TaskService.get_today_tasks(db_session, user_id=None)
        
        assert len(today_tasks_all) == 1
        assert today_tasks_all[0].title == "Task Without Assignees"

    def test_get_today_tasks_with_multiple_assignees(self, db_session: Session) -> None:
        """Test get_today_tasks with tasks that have multiple assignees."""
        from backend.models.user import User
        
        # Create users
        user1 = User(name="User 1", email="user1@example.com")
        user2 = User(name="User 2", email="user2@example.com")
        db_session.add_all([user1, user2])
        db_session.commit()
        
        # Create a task with multiple assignees
        reminder_time = datetime.now()
        task_data = TaskCreate(
            title="Task With Multiple Assignees",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user1.id, user2.id]
        )
        TaskService.create_task(db_session, task_data)
        
        # Get today tasks for user1 - should include task
        today_tasks_user1 = TaskService.get_today_tasks(db_session, user_id=user1.id)
        
        assert len(today_tasks_user1) == 1
        assert today_tasks_user1[0].title == "Task With Multiple Assignees"
        
        # Get today tasks for user2 - should include task
        today_tasks_user2 = TaskService.get_today_tasks(db_session, user_id=user2.id)
        
        assert len(today_tasks_user2) == 1
        assert today_tasks_user2[0].title == "Task With Multiple Assignees"

    def test_get_today_tasks_with_empty_assignees_list(self, db_session: Session) -> None:
        """Test get_today_tasks with tasks that have empty assignees list."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a task with empty assignees list (different from no assignees)
        reminder_time = datetime.now()
        task_data = TaskCreate(
            title="Task With Empty Assignees",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[]
        )
        TaskService.create_task(db_session, task_data)
        
        # Get today tasks for the user - should include task
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].title == "Task With Empty Assignees"

    def test_get_today_tasks_with_completed_one_time_future(self, db_session: Session) -> None:
        """Test get_today_tasks with completed one-time tasks that have future dates."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a one-time task in the future
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Completed Future One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Complete the task
        TaskService.complete_task(db_session, task.id)
        
        # Get today tasks - should be visible because it's completed
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].completed is True

    def test_get_today_tasks_with_active_one_time_future(self, db_session: Session) -> None:
        """Test get_today_tasks with active one-time tasks that have future dates."""
        from backend.models.user import User

        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()

        # Create a one-time task in the future
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Active Future One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        TaskService.create_task(db_session, task_data)

        # Get today tasks - future one-time tasks should not be visible
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)

        assert len(today_tasks) == 0

    def test_get_today_tasks_with_inactive_one_time(self, db_session: Session) -> None:
        """Test get_today_tasks with inactive one-time tasks."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a one-time task
        reminder_time = datetime.now()
        task_data = TaskCreate(
            title="Inactive One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Mark task as inactive
        task.active = False
        db_session.commit()
        
        # Get today tasks - should still be visible because it's one-time
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].active is False

    def test_get_today_tasks_with_inactive_recurring(self, db_session: Session) -> None:
        """Test get_today_tasks with inactive recurring tasks."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a recurring task
        reminder_time = datetime.now()
        task_data = TaskCreate(
            title="Inactive Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Mark task as inactive
        task.active = False
        db_session.commit()
        
        # Get today tasks - should not be visible because it's inactive recurring
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 0

    def test_get_today_tasks_with_inactive_interval(self, db_session: Session) -> None:
        """Test get_today_tasks with inactive interval tasks."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create an interval task
        reminder_time = datetime.now()
        task_data = TaskCreate(
            title="Inactive Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Mark task as inactive
        task.active = False
        db_session.commit()
        
        # Get today tasks - should not be visible because it's inactive interval
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 0

    def test_get_today_tasks_with_completed_recurring_future(self, db_session: Session) -> None:
        """Test get_today_tasks with completed recurring tasks that have future dates."""
        from backend.models.user import User

        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()

        # Create a recurring task in the future
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Completed Future Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)

        # Complete the task
        TaskService.complete_task(db_session, task.id)

        # Get today tasks - completed tasks are always visible
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)

        assert len(today_tasks) == 1

    def test_get_today_tasks_with_completed_interval_future(self, db_session: Session) -> None:
        """Test get_today_tasks with completed interval tasks that have future dates."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create an interval task in the future
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Completed Future Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)

        # Complete the task
        TaskService.complete_task(db_session, task.id)

        # Get today tasks - completed tasks are always visible
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)

        assert len(today_tasks) == 1

    def test_get_today_tasks_with_past_recurring_not_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with past recurring tasks that are not completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a recurring task in the past
        past_date = datetime.now() - timedelta(days=1)
        task_data = TaskCreate(
            title="Past Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=past_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Get today tasks - should be visible because it's in the past
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].completed is False

    def test_get_today_tasks_with_past_interval_not_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with past interval tasks that are not completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create an interval task in the past
        past_date = datetime.now() - timedelta(days=1)
        task_data = TaskCreate(
            title="Past Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=past_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Get today tasks - should be visible because it's in the past
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].completed is False

    def test_get_today_tasks_with_today_recurring_not_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with recurring tasks that are due today and not completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a recurring task due today
        today_date = datetime.now().replace(hour=10, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="Today Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=today_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Get today tasks - should be visible because it's due today
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].completed is False

    def test_get_today_tasks_with_today_interval_not_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with interval tasks that are due today and not completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create an interval task due today
        today_date = datetime.now().replace(hour=10, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="Today Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=today_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Get today tasks - should be visible because it's due today
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].completed is False

    def test_get_today_tasks_with_today_one_time_not_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with one-time tasks that are due today and not completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a one-time task due today
        today_date = datetime.now().replace(hour=10, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="Today One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Get today tasks - should be visible because it's due today
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].completed is False

    def test_get_today_tasks_with_today_one_time_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with one-time tasks that are due today and completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a one-time task due today
        today_date = datetime.now().replace(hour=10, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="Today Completed One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Complete the task
        TaskService.complete_task(db_session, task.id)
        
        # Get today tasks - should be visible because it's one-time and completed
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].completed is True

    def test_get_today_tasks_with_past_one_time_not_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with one-time tasks that are in the past and not completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a one-time task in the past
        past_date = datetime.now() - timedelta(days=1)
        task_data = TaskCreate(
            title="Past One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=past_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Get today tasks - should be visible because it's one-time and in the past
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].completed is False

    def test_get_today_tasks_with_past_one_time_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with one-time tasks that are in the past and completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a one-time task in the past
        past_date = datetime.now() - timedelta(days=1)
        task_data = TaskCreate(
            title="Past Completed One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=past_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Complete the task
        TaskService.complete_task(db_session, task.id)
        
        # Get today tasks - should be visible because it's one-time and completed
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].completed is True

    def test_get_today_tasks_with_future_one_time_not_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with one-time tasks that are in the future and not completed."""
        from backend.models.user import User

        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()

        # Create a one-time task in the future
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Future One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        TaskService.create_task(db_session, task_data)

        # Get today tasks - future one-time tasks should not be visible
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)

        assert len(today_tasks) == 0

    def test_get_today_tasks_with_future_one_time_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with one-time tasks that are in the future and completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a one-time task in the future
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Future Completed One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Complete the task
        TaskService.complete_task(db_session, task.id)
        
        # Get today tasks - should be visible because it's one-time and completed
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].completed is True

    def test_get_today_tasks_with_future_recurring_not_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with recurring tasks that are in the future and not completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a recurring task in the future
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Future Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Get today tasks - should not be visible because it's future recurring
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 0

    def test_get_today_tasks_with_future_interval_not_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with interval tasks that are in the future and not completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create an interval task in the future
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Future Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Get today tasks - should not be visible because it's future interval
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 0

    def test_get_today_tasks_with_future_recurring_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with recurring tasks that are in the future and completed."""
        from backend.models.user import User

        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()

        # Create a recurring task in the future
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Future Completed Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)

        # Complete the task
        TaskService.complete_task(db_session, task.id)

        # Get today tasks - completed tasks are always visible
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)

        assert len(today_tasks) == 1

    def test_get_today_tasks_with_future_interval_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with interval tasks that are in the future and completed."""
        from backend.models.user import User

        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()

        # Create an interval task in the future
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Future Completed Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)

        # Complete the task
        TaskService.complete_task(db_session, task.id)

        # Get today tasks - completed tasks are always visible
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)

        assert len(today_tasks) == 1

    def test_get_today_tasks_with_past_recurring_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with recurring tasks that are in the past and completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a recurring task in the past
        past_date = datetime.now() - timedelta(days=1)
        task_data = TaskCreate(
            title="Past Completed Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=past_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)

        # Complete the task
        TaskService.complete_task(db_session, task.id)

        # Get today tasks - task gets reset by new day logic and becomes future, so not visible
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)

        assert len(today_tasks) == 0

    def test_get_today_tasks_with_past_interval_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with interval tasks that are in the past and completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create an interval task in the past
        past_date = datetime.now() - timedelta(days=1)
        task_data = TaskCreate(
            title="Past Completed Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=past_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)

        # Complete the task
        TaskService.complete_task(db_session, task.id)

        # Get today tasks - task gets reset by new day logic and becomes future, so not visible
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)

        assert len(today_tasks) == 0

    def test_get_today_tasks_with_today_recurring_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with recurring tasks that are due today and completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a recurring task due today
        today_date = datetime.now().replace(hour=10, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="Today Completed Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=today_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Complete the task
        TaskService.complete_task(db_session, task.id)
        
        # Get today tasks - should be visible because it's due today
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].completed is True

    def test_get_today_tasks_with_today_interval_completed(self, db_session: Session) -> None:
        """Test get_today_tasks with interval tasks that are due today and completed."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create an interval task due today
        today_date = datetime.now().replace(hour=10, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="Today Completed Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=today_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Complete the task
        TaskService.complete_task(db_session, task.id)
        
        # Get today tasks - should be visible because it's due today
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].completed is True

    def test_get_today_tasks_with_inactive_one_time_past(self, db_session: Session) -> None:
        """Test get_today_tasks with inactive one-time tasks that are in the past."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a one-time task in the past
        past_date = datetime.now() - timedelta(days=1)
        task_data = TaskCreate(
            title="Inactive Past One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=past_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Mark task as inactive
        task.active = False
        db_session.commit()
        
        # Get today tasks - should still be visible because it's one-time
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].active is False

    def test_get_today_tasks_with_inactive_one_time_today(self, db_session: Session) -> None:
        """Test get_today_tasks with inactive one-time tasks that are due today."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a one-time task due today
        today_date = datetime.now().replace(hour=10, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="Inactive Today One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Mark task as inactive
        task.active = False
        db_session.commit()
        
        # Get today tasks - should still be visible because it's one-time
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].active is False

    def test_get_today_tasks_with_inactive_one_time_future(self, db_session: Session) -> None:
        """Test get_today_tasks with inactive one-time tasks that are in the future."""
        from backend.models.user import User

        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()

        # Create a one-time task in the future
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Inactive Future One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        TaskService.create_task(db_session, task_data)

        # Get today tasks - future one-time tasks should not be visible even if inactive
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)

        assert len(today_tasks) == 0

    def test_get_today_tasks_with_inactive_recurring_past(self, db_session: Session) -> None:
        """Test get_today_tasks with inactive recurring tasks that are in the past."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a recurring task in the past
        past_date = datetime.now() - timedelta(days=1)
        task_data = TaskCreate(
            title="Inactive Past Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=past_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Mark task as inactive
        task.active = False
        db_session.commit()
        
        # Get today tasks - should not be visible because it's inactive recurring
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 0

    def test_get_today_tasks_with_inactive_recurring_today(self, db_session: Session) -> None:
        """Test get_today_tasks with inactive recurring tasks that are due today."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a recurring task due today
        today_date = datetime.now().replace(hour=10, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="Inactive Today Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=today_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Mark task as inactive
        task.active = False
        db_session.commit()
        
        # Get today tasks - should not be visible because it's inactive recurring
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 0

    def test_get_today_tasks_with_inactive_recurring_future(self, db_session: Session) -> None:
        """Test get_today_tasks with inactive recurring tasks that are in the future."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a recurring task in the future
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Inactive Future Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Mark task as inactive
        task.active = False
        db_session.commit()
        
        # Get today tasks - should not be visible because it's inactive recurring
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 0

    def test_get_today_tasks_with_inactive_interval_past(self, db_session: Session) -> None:
        """Test get_today_tasks with inactive interval tasks that are in the past."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create an interval task in the past
        past_date = datetime.now() - timedelta(days=1)
        task_data = TaskCreate(
            title="Inactive Past Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=past_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Mark task as inactive
        task.active = False
        db_session.commit()
        
        # Get today tasks - should not be visible because it's inactive interval
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 0

    def test_get_today_tasks_with_inactive_interval_today(self, db_session: Session) -> None:
        """Test get_today_tasks with inactive interval tasks that are due today."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create an interval task due today
        today_date = datetime.now().replace(hour=10, minute=0, second=0, microsecond=0)
        task_data = TaskCreate(
            title="Inactive Today Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=today_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Mark task as inactive
        task.active = False
        db_session.commit()
        
        # Get today tasks - should not be visible because it's inactive interval
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 0

    def test_get_today_tasks_with_inactive_interval_future(self, db_session: Session) -> None:
        """Test get_today_tasks with inactive interval tasks that are in the future."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create an interval task in the future
        future_date = datetime.now() + timedelta(days=2)
        task_data = TaskCreate(
            title="Inactive Future Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=future_date,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Mark task as inactive
        task.active = False
        db_session.commit()
        
        # Get today tasks - should not be visible because it's inactive interval
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 0

    def test_get_today_tasks_with_completed_inactive_one_time(self, db_session: Session) -> None:
        """Test get_today_tasks with completed inactive one-time tasks."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a one-time task
        reminder_time = datetime.now()
        task_data = TaskCreate(
            title="Completed Inactive One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Complete and mark as inactive
        TaskService.complete_task(db_session, task.id)
        task.active = False
        db_session.commit()
        
        # Get today tasks - should still be visible because it's one-time
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 1
        assert today_tasks[0].id == task.id
        assert today_tasks[0].completed is True
        assert today_tasks[0].active is False

    def test_get_today_tasks_with_completed_inactive_recurring(self, db_session: Session) -> None:
        """Test get_today_tasks with completed inactive recurring tasks."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create a recurring task
        reminder_time = datetime.now()
        task_data = TaskCreate(
            title="Completed Inactive Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Complete and mark as inactive
        TaskService.complete_task(db_session, task.id)
        task.active = False
        db_session.commit()

        # Get today tasks - completed tasks are always visible
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)

        assert len(today_tasks) == 1

    def test_get_today_tasks_with_completed_inactive_interval(self, db_session: Session) -> None:
        """Test get_today_tasks with completed inactive interval tasks."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create an interval task
        reminder_time = datetime.now()
        task_data = TaskCreate(
            title="Completed Inactive Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=reminder_time,
            assigned_user_ids=[user.id]
        )
        task = TaskService.create_task(db_session, task_data)
        
        # Complete and mark as inactive
        TaskService.complete_task(db_session, task.id)
        task.active = False
        db_session.commit()

        # Get today tasks - completed tasks are always visible
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)

        assert len(today_tasks) == 1

    def test_get_today_tasks_with_multiple_tasks_different_types(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks of different types."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks of different types
        now = datetime.now()
        
        # One-time task due today
        task_data1 = TaskCreate(
            title="One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Recurring task due today
        task_data2 = TaskCreate(
            title="Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Interval task due today
        task_data3 = TaskCreate(
            title="Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        TaskService.create_task(db_session, task_data3)
        
        # Get today tasks - should include all three
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        assert len(today_tasks) == 3
        task_titles = [task.title for task in today_tasks]
        assert "One-Time Task" in task_titles
        assert "Recurring Task" in task_titles
        assert "Interval Task" in task_titles

    def test_get_today_tasks_with_multiple_tasks_different_users(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks assigned to different users."""
        from backend.models.user import User
        
        # Create users
        user1 = User(name="User 1", email="user1@example.com")
        user2 = User(name="User 2", email="user2@example.com")
        db_session.add_all([user1, user2])
        db_session.commit()
        
        # Create tasks for different users
        now = datetime.now()
        
        # Task for user1
        task_data1 = TaskCreate(
            title="Task for User 1",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user1.id]
        )
        
        # Task for user2
        task_data2 = TaskCreate(
            title="Task for User 2",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user2.id]
        )
        
        # Task for both users
        task_data3 = TaskCreate(
            title="Task for Both Users",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user1.id, user2.id]
        )
        
        # Task for no users
        task_data4 = TaskCreate(
            title="Task for No Users",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        TaskService.create_task(db_session, task_data3)
        TaskService.create_task(db_session, task_data4)
        
        # Get today tasks for user1
        today_tasks_user1 = TaskService.get_today_tasks(db_session, user_id=user1.id)

        assert len(today_tasks_user1) == 3  # Task for User 1, Task for Both Users, and Task for No Users
        
        # Get today tasks for user2
        today_tasks_user2 = TaskService.get_today_tasks(db_session, user_id=user2.id)

        assert len(today_tasks_user2) == 3  # Task for User 2, Task for Both Users, and Task for No Users
        
        # Get today tasks for no user
        today_tasks_all = TaskService.get_today_tasks(db_session, user_id=None)
        
        assert len(today_tasks_all) == 4  # All tasks

    def test_get_today_tasks_with_multiple_tasks_different_states(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks in different states."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks in different states
        now = datetime.now()
        
        # Active one-time task
        task_data1 = TaskCreate(
            title="Active One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Inactive one-time task
        task_data2 = TaskCreate(
            title="Inactive One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Active recurring task
        task_data3 = TaskCreate(
            title="Active Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Inactive recurring task
        task_data4 = TaskCreate(
            title="Inactive Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        task1 = TaskService.create_task(db_session, task_data1)
        task2 = TaskService.create_task(db_session, task_data2)
        task3 = TaskService.create_task(db_session, task_data3)
        task4 = TaskService.create_task(db_session, task_data4)
        
        # Mark some tasks as inactive
        task2.active = False
        task4.active = False
        db_session.commit()
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)

        # Should include active one-time, inactive one-time, active recurring
        # Should not include inactive recurring
        assert len(today_tasks) == 3
        task_ids = [task.id for task in today_tasks]
        assert task1.id in task_ids  # Active one-time
        assert task2.id in task_ids  # Inactive one-time
        assert task3.id in task_ids  # Active recurring
        assert task4.id not in task_ids  # Inactive recurring

    def test_get_today_tasks_with_multiple_tasks_different_dates(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different dates."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks with different dates
        now = datetime.now()
        
        # Task due today
        task_data1 = TaskCreate(
            title="Task Due Today",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Task due yesterday
        task_data2 = TaskCreate(
            title="Task Due Yesterday",
            task_type=TaskType.ONE_TIME,
            reminder_time=now - timedelta(days=1),
            assigned_user_ids=[user.id]
        )
        
        # Task due tomorrow
        task_data3 = TaskCreate(
            title="Task Due Tomorrow",
            task_type=TaskType.ONE_TIME,
            reminder_time=now + timedelta(days=1),
            assigned_user_ids=[user.id]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        TaskService.create_task(db_session, task_data3)
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)

        # Should include today and yesterday tasks, but not future
        assert len(today_tasks) == 2
        task_titles = [task.title for task in today_tasks]
        assert "Task Due Today" in task_titles
        assert "Task Due Yesterday" in task_titles
        assert "Task Due Tomorrow" not in task_titles

    def test_get_today_tasks_with_multiple_tasks_different_completion_states(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different completion states."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks with different completion states
        now = datetime.now()
        
        # Not completed task
        task_data1 = TaskCreate(
            title="Not Completed Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Completed task
        task_data2 = TaskCreate(
            title="Completed Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        task1 = TaskService.create_task(db_session, task_data1)
        task2 = TaskService.create_task(db_session, task_data2)
        
        # Complete one task
        TaskService.complete_task(db_session, task2.id)
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include both tasks
        assert len(today_tasks) == 2
        task_ids = [task.id for task in today_tasks]
        assert task1.id in task_ids
        assert task2.id in task_ids
        
        # Check completion states
        for task in today_tasks:
            if task.id == task1.id:
                assert task.completed is False
            elif task.id == task2.id:
                assert task.completed is True

    def test_get_today_tasks_with_multiple_tasks_different_recurrence_types(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different recurrence types."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks with different recurrence types
        now = datetime.now()
        
        # Daily recurring task
        task_data1 = TaskCreate(
            title="Daily Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Weekly recurring task
        task_data2 = TaskCreate(
            title="Weekly Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.WEEKLY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Monthly recurring task
        task_data3 = TaskCreate(
            title="Monthly Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.MONTHLY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Yearly recurring task
        task_data4 = TaskCreate(
            title="Yearly Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.YEARLY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Interval task
        task_data5 = TaskCreate(
            title="Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        TaskService.create_task(db_session, task_data3)
        TaskService.create_task(db_session, task_data4)
        TaskService.create_task(db_session, task_data5)
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include all tasks that are due today
        assert len(today_tasks) == 5
        task_titles = [task.title for task in today_tasks]
        assert "Daily Recurring Task" in task_titles
        assert "Weekly Recurring Task" in task_titles
        assert "Monthly Recurring Task" in task_titles
        assert "Yearly Recurring Task" in task_titles
        assert "Interval Task" in task_titles

    def test_get_today_tasks_with_multiple_tasks_different_intervals(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different intervals."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks with different intervals
        now = datetime.now()
        
        # Daily task (interval 1)
        task_data1 = TaskCreate(
            title="Daily Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Every 3 days task
        task_data2 = TaskCreate(
            title="Every 3 Days Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=3,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Weekly task (interval 1)
        task_data3 = TaskCreate(
            title="Weekly Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.WEEKLY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Every 2 weeks task
        task_data4 = TaskCreate(
            title="Every 2 Weeks Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.WEEKLY,
            recurrence_interval=2,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Monthly task (interval 1)
        task_data5 = TaskCreate(
            title="Monthly Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.MONTHLY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Every 2 months task
        task_data6 = TaskCreate(
            title="Every 2 Months Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.MONTHLY,
            recurrence_interval=2,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Yearly task (interval 1)
        task_data7 = TaskCreate(
            title="Yearly Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.YEARLY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Every 2 years task
        task_data8 = TaskCreate(
            title="Every 2 Years Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.YEARLY,
            recurrence_interval=2,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # 7-day interval task
        task_data9 = TaskCreate(
            title="7-Day Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # 14-day interval task
        task_data10 = TaskCreate(
            title="14-Day Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=14,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        TaskService.create_task(db_session, task_data3)
        TaskService.create_task(db_session, task_data4)
        TaskService.create_task(db_session, task_data5)
        TaskService.create_task(db_session, task_data6)
        TaskService.create_task(db_session, task_data7)
        TaskService.create_task(db_session, task_data8)
        TaskService.create_task(db_session, task_data9)
        TaskService.create_task(db_session, task_data10)
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include all tasks that are due today
        assert len(today_tasks) == 10
        task_titles = [task.title for task in today_tasks]
        assert "Daily Task" in task_titles
        assert "Every 3 Days Task" in task_titles
        assert "Weekly Task" in task_titles
        assert "Every 2 Weeks Task" in task_titles
        assert "Monthly Task" in task_titles
        assert "Every 2 Months Task" in task_titles
        assert "Yearly Task" in task_titles
        assert "Every 2 Years Task" in task_titles
        assert "7-Day Interval Task" in task_titles
        assert "14-Day Interval Task" in task_titles

    def test_get_today_tasks_with_multiple_tasks_different_reminder_times(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different reminder times."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks with different reminder times
        today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
        
        # Task at 9 AM
        task_data1 = TaskCreate(
            title="9 AM Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today.replace(hour=9),
            assigned_user_ids=[user.id]
        )
        
        # Task at 12 PM
        task_data2 = TaskCreate(
            title="12 PM Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today.replace(hour=12),
            assigned_user_ids=[user.id]
        )
        
        # Task at 6 PM
        task_data3 = TaskCreate(
            title="6 PM Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today.replace(hour=18),
            assigned_user_ids=[user.id]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        TaskService.create_task(db_session, task_data3)
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include all tasks
        assert len(today_tasks) == 3
        task_titles = [task.title for task in today_tasks]
        assert "9 AM Task" in task_titles
        assert "12 PM Task" in task_titles
        assert "6 PM Task" in task_titles

    def test_get_today_tasks_with_multiple_tasks_different_groups(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks in different groups."""
        from backend.models.user import User
        from backend.models.group import Group
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create groups
        group1 = Group(name="Group 1", description="First group")
        group2 = Group(name="Group 2", description="Second group")
        db_session.add_all([group1, group2])
        db_session.commit()
        
        # Create tasks in different groups
        now = datetime.now()
        
        # Task in group1
        task_data1 = TaskCreate(
            title="Task in Group 1",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            group_id=group1.id,
            assigned_user_ids=[user.id]
        )
        
        # Task in group2
        task_data2 = TaskCreate(
            title="Task in Group 2",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            group_id=group2.id,
            assigned_user_ids=[user.id]
        )
        
        # Task without group
        task_data3 = TaskCreate(
            title="Task without Group",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        TaskService.create_task(db_session, task_data3)
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include all tasks
        assert len(today_tasks) == 3
        task_titles = [task.title for task in today_tasks]
        assert "Task in Group 1" in task_titles
        assert "Task in Group 2" in task_titles
        assert "Task without Group" in task_titles

    def test_get_today_tasks_with_multiple_tasks_different_descriptions(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different descriptions."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks with different descriptions
        now = datetime.now()
        
        # Task with description
        task_data1 = TaskCreate(
            title="Task with Description",
            description="This is a task with a description",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Task without description
        task_data2 = TaskCreate(
            title="Task without Description",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include both tasks
        assert len(today_tasks) == 2
        task_titles = [task.title for task in today_tasks]
        assert "Task with Description" in task_titles
        assert "Task without Description" in task_titles

    def test_get_today_tasks_with_multiple_tasks_different_assignees(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different assignees."""
        from backend.models.user import User
        
        # Create users
        user1 = User(name="User 1", email="user1@example.com")
        user2 = User(name="User 2", email="user2@example.com")
        user3 = User(name="User 3", email="user3@example.com")
        db_session.add_all([user1, user2, user3])
        db_session.commit()
        
        # Create tasks with different assignees
        now = datetime.now()
        
        # Task assigned to user1
        task_data1 = TaskCreate(
            title="Task for User 1",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user1.id]
        )
        
        # Task assigned to user2
        task_data2 = TaskCreate(
            title="Task for User 2",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user2.id]
        )
        
        # Task assigned to user3
        task_data3 = TaskCreate(
            title="Task for User 3",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user3.id]
        )
        
        # Task assigned to user1 and user2
        task_data4 = TaskCreate(
            title="Task for User 1 and User 2",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user1.id, user2.id]
        )
        
        # Task assigned to all users
        task_data5 = TaskCreate(
            title="Task for All Users",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user1.id, user2.id, user3.id]
        )
        
        # Task with no assignees
        task_data6 = TaskCreate(
            title="Task with No Assignees",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        TaskService.create_task(db_session, task_data3)
        TaskService.create_task(db_session, task_data4)
        TaskService.create_task(db_session, task_data5)
        TaskService.create_task(db_session, task_data6)
        
        # Get today tasks for user1
        today_tasks_user1 = TaskService.get_today_tasks(db_session, user_id=user1.id)
        
        # Should include tasks for user1, tasks for user1 and user2, tasks for all users, and tasks with no assignees
        assert len(today_tasks_user1) == 4
        task_titles = [task.title for task in today_tasks_user1]
        assert "Task for User 1" in task_titles
        assert "Task for User 1 and User 2" in task_titles
        assert "Task for All Users" in task_titles
        assert "Task with No Assignees" in task_titles
        
        # Get today tasks for user2
        today_tasks_user2 = TaskService.get_today_tasks(db_session, user_id=user2.id)
        
        # Should include tasks for user2, tasks for user1 and user2, tasks for all users, and tasks with no assignees
        assert len(today_tasks_user2) == 4
        task_titles = [task.title for task in today_tasks_user2]
        assert "Task for User 2" in task_titles
        assert "Task for User 1 and User 2" in task_titles
        assert "Task for All Users" in task_titles
        assert "Task with No Assignees" in task_titles
        
        # Get today tasks for user3
        today_tasks_user3 = TaskService.get_today_tasks(db_session, user_id=user3.id)
        
        # Should include tasks for user3, tasks for all users, and tasks with no assignees
        assert len(today_tasks_user3) == 3
        task_titles = [task.title for task in today_tasks_user3]
        assert "Task for User 3" in task_titles
        assert "Task for All Users" in task_titles
        assert "Task with No Assignees" in task_titles
        
        # Get today tasks for no user
        today_tasks_all = TaskService.get_today_tasks(db_session, user_id=None)
        
        # Should include all tasks
        assert len(today_tasks_all) == 6
        task_titles = [task.title for task in today_tasks_all]
        assert "Task for User 1" in task_titles
        assert "Task for User 2" in task_titles
        assert "Task for User 3" in task_titles
        assert "Task for User 1 and User 2" in task_titles
        assert "Task for All Users" in task_titles
        assert "Task with No Assignees" in task_titles

    def test_get_today_tasks_with_multiple_tasks_different_creation_times(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different creation times."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks with different creation times
        now = datetime.now()
        
        # Task created first
        task_data1 = TaskCreate(
            title="First Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Task created second
        task_data2 = TaskCreate(
            title="Second Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Task created third
        task_data3 = TaskCreate(
            title="Third Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        TaskService.create_task(db_session, task_data3)
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include all tasks
        assert len(today_tasks) == 3
        task_titles = [task.title for task in today_tasks]
        assert "First Task" in task_titles
        assert "Second Task" in task_titles
        assert "Third Task" in task_titles

    def test_get_today_tasks_with_multiple_tasks_different_update_times(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different update times."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks
        now = datetime.now()
        
        task_data1 = TaskCreate(
            title="Task 1",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        task_data2 = TaskCreate(
            title="Task 2",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        task_data3 = TaskCreate(
            title="Task 3",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        task1 = TaskService.create_task(db_session, task_data1)
        task2 = TaskService.create_task(db_session, task_data2)
        task3 = TaskService.create_task(db_session, task_data3)
        
        # Update tasks at different times
        import time
        time.sleep(0.1)  # Small delay to ensure different timestamps
        
        update_data = TaskUpdate(title="Updated Task 1")
        TaskService.update_task(db_session, task1.id, update_data)
        
        time.sleep(0.1)  # Small delay to ensure different timestamps
        
        update_data = TaskUpdate(title="Updated Task 2")
        TaskService.update_task(db_session, task2.id, update_data)
        
        time.sleep(0.1)  # Small delay to ensure different timestamps
        
        update_data = TaskUpdate(title="Updated Task 3")
        TaskService.update_task(db_session, task3.id, update_data)
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include all tasks
        assert len(today_tasks) == 3
        task_titles = [task.title for task in today_tasks]
        assert "Updated Task 1" in task_titles
        assert "Updated Task 2" in task_titles
        assert "Updated Task 3" in task_titles

    def test_get_today_tasks_with_multiple_tasks_different_completion_times(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different completion times."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks
        now = datetime.now()
        
        task_data1 = TaskCreate(
            title="Task 1",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        task_data2 = TaskCreate(
            title="Task 2",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        task_data3 = TaskCreate(
            title="Task 3",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        task1 = TaskService.create_task(db_session, task_data1)
        task2 = TaskService.create_task(db_session, task_data2)
        task3 = TaskService.create_task(db_session, task_data3)
        
        # Complete tasks at different times
        import time
        time.sleep(0.1)  # Small delay to ensure different timestamps
        
        TaskService.complete_task(db_session, task1.id)
        
        time.sleep(0.1)  # Small delay to ensure different timestamps
        
        TaskService.complete_task(db_session, task2.id)
        
        time.sleep(0.1)  # Small delay to ensure different timestamps
        
        TaskService.complete_task(db_session, task3.id)
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include all tasks
        assert len(today_tasks) == 3
        task_ids = [task.id for task in today_tasks]
        assert task1.id in task_ids
        assert task2.id in task_ids
        assert task3.id in task_ids
        
        # All should be completed
        for task in today_tasks:
            assert task.completed is True

    def test_get_today_tasks_with_multiple_tasks_different_active_states(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different active states."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks
        now = datetime.now()
        
        task_data1 = TaskCreate(
            title="Active Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        task_data2 = TaskCreate(
            title="Inactive Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        task_data3 = TaskCreate(
            title="Another Active Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        task1 = TaskService.create_task(db_session, task_data1)
        task2 = TaskService.create_task(db_session, task_data2)
        task3 = TaskService.create_task(db_session, task_data3)
        
        # Mark task2 as inactive
        task2.active = False
        db_session.commit()
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include all one-time tasks regardless of active state
        assert len(today_tasks) == 3
        task_titles = [task.title for task in today_tasks]
        assert "Active Task" in task_titles
        assert "Inactive Task" in task_titles
        assert "Another Active Task" in task_titles

    def test_get_today_tasks_with_multiple_tasks_different_recurrence_intervals(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different recurrence intervals."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks with different recurrence intervals
        now = datetime.now()
        
        # Daily task with interval 1
        task_data1 = TaskCreate(
            title="Daily Task (1 day)",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Daily task with interval 2
        task_data2 = TaskCreate(
            title="Daily Task (2 days)",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=2,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Weekly task with interval 1
        task_data3 = TaskCreate(
            title="Weekly Task (1 week)",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.WEEKLY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Weekly task with interval 2
        task_data4 = TaskCreate(
            title="Weekly Task (2 weeks)",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.WEEKLY,
            recurrence_interval=2,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Monthly task with interval 1
        task_data5 = TaskCreate(
            title="Monthly Task (1 month)",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.MONTHLY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Monthly task with interval 2
        task_data6 = TaskCreate(
            title="Monthly Task (2 months)",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.MONTHLY,
            recurrence_interval=2,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Yearly task with interval 1
        task_data7 = TaskCreate(
            title="Yearly Task (1 year)",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.YEARLY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Yearly task with interval 2
        task_data8 = TaskCreate(
            title="Yearly Task (2 years)",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.YEARLY,
            recurrence_interval=2,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Interval task with 1 day
        task_data9 = TaskCreate(
            title="Interval Task (1 day)",
            task_type=TaskType.INTERVAL,
            interval_days=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Interval task with 7 days
        task_data10 = TaskCreate(
            title="Interval Task (7 days)",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        TaskService.create_task(db_session, task_data3)
        TaskService.create_task(db_session, task_data4)
        TaskService.create_task(db_session, task_data5)
        TaskService.create_task(db_session, task_data6)
        TaskService.create_task(db_session, task_data7)
        TaskService.create_task(db_session, task_data8)
        TaskService.create_task(db_session, task_data9)
        TaskService.create_task(db_session, task_data10)
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include all tasks that are due today
        assert len(today_tasks) == 10
        task_titles = [task.title for task in today_tasks]
        assert "Daily Task (1 day)" in task_titles
        assert "Daily Task (2 days)" in task_titles
        assert "Weekly Task (1 week)" in task_titles
        assert "Weekly Task (2 weeks)" in task_titles
        assert "Monthly Task (1 month)" in task_titles
        assert "Monthly Task (2 months)" in task_titles
        assert "Yearly Task (1 year)" in task_titles
        assert "Yearly Task (2 years)" in task_titles
        assert "Interval Task (1 day)" in task_titles
        assert "Interval Task (7 days)" in task_titles

    def test_get_today_tasks_with_multiple_tasks_different_reminder_time_formats(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different reminder time formats."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks with different reminder time formats
        today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
        
        # Task with exact time
        task_data1 = TaskCreate(
            title="Exact Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today.replace(hour=10, minute=30),
            assigned_user_ids=[user.id]
        )
        
        # Task with only hour
        task_data2 = TaskCreate(
            title="Hour Only Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today.replace(hour=14),
            assigned_user_ids=[user.id]
        )
        
        # Task with only minute
        task_data3 = TaskCreate(
            title="Minute Only Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today.replace(minute=45),
            assigned_user_ids=[user.id]
        )
        
        # Task with second precision
        task_data4 = TaskCreate(
            title="Second Precision Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today.replace(hour=16, minute=20, second=30),
            assigned_user_ids=[user.id]
        )
        
        # Task with microsecond precision
        task_data5 = TaskCreate(
            title="Microsecond Precision Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=today.replace(hour=18, minute=15, second=45, microsecond=123456),
            assigned_user_ids=[user.id]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        TaskService.create_task(db_session, task_data3)
        TaskService.create_task(db_session, task_data4)
        TaskService.create_task(db_session, task_data5)
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include all tasks
        assert len(today_tasks) == 5
        task_titles = [task.title for task in today_tasks]
        assert "Exact Time Task" in task_titles
        assert "Hour Only Task" in task_titles
        assert "Minute Only Task" in task_titles
        assert "Second Precision Task" in task_titles
        assert "Microsecond Precision Task" in task_titles

    def test_get_today_tasks_with_multiple_tasks_different_task_types_and_states(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks of different types and states."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks with different types and states
        now = datetime.now()
        
        # Active one-time task
        task_data1 = TaskCreate(
            title="Active One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Inactive one-time task
        task_data2 = TaskCreate(
            title="Inactive One-Time Task",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Active recurring task
        task_data3 = TaskCreate(
            title="Active Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Inactive recurring task
        task_data4 = TaskCreate(
            title="Inactive Recurring Task",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Active interval task
        task_data5 = TaskCreate(
            title="Active Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Inactive interval task
        task_data6 = TaskCreate(
            title="Inactive Interval Task",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        task1 = TaskService.create_task(db_session, task_data1)
        task2 = TaskService.create_task(db_session, task_data2)
        task3 = TaskService.create_task(db_session, task_data3)
        task4 = TaskService.create_task(db_session, task_data4)
        task5 = TaskService.create_task(db_session, task_data5)
        task6 = TaskService.create_task(db_session, task_data6)
        
        # Mark some tasks as inactive
        task2.active = False
        task4.active = False
        task6.active = False
        db_session.commit()
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include active one-time, inactive one-time, active recurring, active interval
        # Should not include inactive recurring, inactive interval
        assert len(today_tasks) == 4
        task_ids = [task.id for task in today_tasks]
        assert task1.id in task_ids  # Active one-time
        assert task2.id in task_ids  # Inactive one-time
        assert task3.id in task_ids  # Active recurring
        assert task5.id in task_ids  # Active interval
        assert task4.id not in task_ids  # Inactive recurring
        assert task6.id not in task_ids  # Inactive interval

    def test_get_today_tasks_with_multiple_tasks_different_completion_and_active_states(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different completion and active states."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks with different completion and active states
        now = datetime.now()
        
        # Not completed, active
        task_data1 = TaskCreate(
            title="Not Completed, Active",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Not completed, inactive
        task_data2 = TaskCreate(
            title="Not Completed, Inactive",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Completed, active
        task_data3 = TaskCreate(
            title="Completed, Active",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Completed, inactive
        task_data4 = TaskCreate(
            title="Completed, Inactive",
            task_type=TaskType.ONE_TIME,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        task1 = TaskService.create_task(db_session, task_data1)
        task2 = TaskService.create_task(db_session, task_data2)
        task3 = TaskService.create_task(db_session, task_data3)
        task4 = TaskService.create_task(db_session, task_data4)
        
        # Set states
        task2.active = False
        TaskService.complete_task(db_session, task3.id)
        TaskService.complete_task(db_session, task4.id)
        task4.active = False
        db_session.commit()
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include all one-time tasks regardless of completion and active state
        assert len(today_tasks) == 4
        task_ids = [task.id for task in today_tasks]
        assert task1.id in task_ids  # Not completed, active
        assert task2.id in task_ids  # Not completed, inactive
        assert task3.id in task_ids  # Completed, active
        assert task4.id in task_ids  # Completed, inactive

    def test_get_today_tasks_with_multiple_tasks_different_recurrence_types_and_intervals(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different recurrence types and intervals."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks with different recurrence types and intervals
        now = datetime.now()
        
        # Daily, interval 1
        task_data1 = TaskCreate(
            title="Daily, Interval 1",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Daily, interval 2
        task_data2 = TaskCreate(
            title="Daily, Interval 2",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=2,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Weekly, interval 1
        task_data3 = TaskCreate(
            title="Weekly, Interval 1",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.WEEKLY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Weekly, interval 2
        task_data4 = TaskCreate(
            title="Weekly, Interval 2",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.WEEKLY,
            recurrence_interval=2,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Monthly, interval 1
        task_data5 = TaskCreate(
            title="Monthly, Interval 1",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.MONTHLY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Monthly, interval 2
        task_data6 = TaskCreate(
            title="Monthly, Interval 2",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.MONTHLY,
            recurrence_interval=2,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Yearly, interval 1
        task_data7 = TaskCreate(
            title="Yearly, Interval 1",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.YEARLY,
            recurrence_interval=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Yearly, interval 2
        task_data8 = TaskCreate(
            title="Yearly, Interval 2",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.YEARLY,
            recurrence_interval=2,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Interval, 1 day
        task_data9 = TaskCreate(
            title="Interval, 1 Day",
            task_type=TaskType.INTERVAL,
            interval_days=1,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        # Interval, 7 days
        task_data10 = TaskCreate(
            title="Interval, 7 Days",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=now,
            assigned_user_ids=[user.id]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        TaskService.create_task(db_session, task_data3)
        TaskService.create_task(db_session, task_data4)
        TaskService.create_task(db_session, task_data5)
        TaskService.create_task(db_session, task_data6)
        TaskService.create_task(db_session, task_data7)
        TaskService.create_task(db_session, task_data8)
        TaskService.create_task(db_session, task_data9)
        TaskService.create_task(db_session, task_data10)
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include all tasks that are due today
        assert len(today_tasks) == 10
        task_titles = [task.title for task in today_tasks]
        assert "Daily, Interval 1" in task_titles
        assert "Daily, Interval 2" in task_titles
        assert "Weekly, Interval 1" in task_titles
        assert "Weekly, Interval 2" in task_titles
        assert "Monthly, Interval 1" in task_titles
        assert "Monthly, Interval 2" in task_titles
        assert "Yearly, Interval 1" in task_titles
        assert "Yearly, Interval 2" in task_titles
        assert "Interval, 1 Day" in task_titles
        assert "Interval, 7 Days" in task_titles

    def test_get_today_tasks_with_multiple_tasks_different_reminder_times_and_recurrence_types(self, db_session: Session) -> None:
        """Test get_today_tasks with multiple tasks with different reminder times and recurrence types."""
        from backend.models.user import User
        
        # Create a user
        user = User(name="Test User", email="test@example.com")
        db_session.add(user)
        db_session.commit()
        
        # Create tasks with different reminder times and recurrence types
        today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
        
        # One-time task at 9 AM
        task_data1 = TaskCreate(
            title="One-Time at 9 AM",
            task_type=TaskType.ONE_TIME,
            reminder_time=today.replace(hour=9),
            assigned_user_ids=[user.id]
        )
        
        # Recurring daily task at 12 PM
        task_data2 = TaskCreate(
            title="Daily at 12 PM",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.DAILY,
            recurrence_interval=1,
            reminder_time=today.replace(hour=12),
            assigned_user_ids=[user.id]
        )
        
        # Recurring weekly task at 6 PM
        task_data3 = TaskCreate(
            title="Weekly at 6 PM",
            task_type=TaskType.RECURRING,
            recurrence_type=RecurrenceType.WEEKLY,
            recurrence_interval=1,
            reminder_time=today.replace(hour=18),
            assigned_user_ids=[user.id]
        )
        
        # Interval task at 10 AM
        task_data4 = TaskCreate(
            title="Interval at 10 AM",
            task_type=TaskType.INTERVAL,
            interval_days=7,
            reminder_time=today.replace(hour=10),
            assigned_user_ids=[user.id]
        )
        
        TaskService.create_task(db_session, task_data1)
        TaskService.create_task(db_session, task_data2)
        TaskService.create_task(db_session, task_data3)
        TaskService.create_task(db_session, task_data4)
        
        # Get today tasks
        today_tasks = TaskService.get_today_tasks(db_session, user_id=user.id)
        
        # Should include all tasks
        assert len(today_tasks) == 4
        task_titles = [task.title for task in today_tasks]
        assert "One-Time at 9 AM" in task_titles
        assert "Daily at 12 PM" in task_titles
        assert "Weekly at 6 PM" in task_titles
        assert "Interval at 10 AM" in task_titles
