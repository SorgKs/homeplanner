"""Tests for task completion date logic."""

from datetime import datetime, timedelta
from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from backend.database import Base, get_db
from backend.main import app
from backend.models.task import RecurrenceType, TaskType
from backend.services.task_service import TaskService

if TYPE_CHECKING:
    from _pytest.capture import CaptureFixture
    from _pytest.fixtures import FixtureRequest
    from _pytest.logging import LogCaptureFixture
    from _pytest.monkeypatch import MonkeyPatch
    from pytest_mock.plugin import MockerFixture


# Test database
SQLALCHEMY_DATABASE_URL = "sqlite:///./test_completion_dates.db"

engine = create_engine(
    SQLALCHEMY_DATABASE_URL, connect_args={"check_same_thread": False}
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


@pytest.fixture(scope="function")
def db_session():
    """Create test database session."""
    Base.metadata.create_all(bind=engine)
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()
        Base.metadata.drop_all(bind=engine)


@pytest.fixture(scope="function")
def client(db_session):
    """Create test client with test database."""
    def override_get_db():
        try:
            yield db_session
        finally:
            pass

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()


class TestTaskCompletionDates:
    """Test task date changes on completion."""

    def test_completion_does_not_change_date_immediately_for_today(self, client):
        """Test that completing a task due today does not change its date immediately."""
        # Create a recurring task due today
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        today_start = today.replace(hour=0, minute=0, second=0, microsecond=0)
        
        task_data = {
            "title": "Daily task",
            "task_type": TaskType.RECURRING.value,
            "recurrence_type": RecurrenceType.DAILY.value,
            "recurrence_interval": 1,
            "next_due_date": today.isoformat(),
        }
        
        create_response = client.post("/api/v1/tasks/", json=task_data)
        assert create_response.status_code == 201
        task_id = create_response.json()["id"]
        original_due_date = create_response.json()["next_due_date"]
        
        # Complete the task
        complete_response = client.post(f"/api/v1/tasks/{task_id}/complete")
        assert complete_response.status_code == 200
        completed_task = complete_response.json()
        
        # Date should not change immediately if task was due today
        completed_due_date = datetime.fromisoformat(completed_task["next_due_date"].replace("Z", "+00:00"))
        completed_due_date_start = completed_due_date.replace(hour=0, minute=0, second=0, microsecond=0)
        
        # Date should remain today (not updated to tomorrow)
        assert completed_due_date_start.date() == today_start.date(), (
            f"Date should remain today ({today_start.date()}), "
            f"but got {completed_due_date_start.date()}"
        )
        
        # Task should have last_completed_at set
        assert completed_task["last_completed_at"] is not None

    def test_completion_does_not_change_date_immediately_for_overdue_task(self, client):
        """Test that completing an overdue task does not change its date immediately."""
        # Create a recurring task due yesterday (overdue)
        yesterday = datetime.now() - timedelta(days=1)
        yesterday = yesterday.replace(hour=9, minute=0, second=0, microsecond=0)
        yesterday_start = yesterday.replace(hour=0, minute=0, second=0, microsecond=0)
        
        task_data = {
            "title": "Daily task",
            "task_type": TaskType.RECURRING.value,
            "recurrence_type": RecurrenceType.DAILY.value,
            "recurrence_interval": 1,
            "next_due_date": yesterday.isoformat(),
        }
        
        create_response = client.post("/api/v1/tasks/", json=task_data)
        assert create_response.status_code == 201
        task_id = create_response.json()["id"]
        original_due_date = datetime.fromisoformat(
            create_response.json()["next_due_date"].replace("Z", "+00:00")
        )
        original_due_date_start = original_due_date.replace(hour=0, minute=0, second=0, microsecond=0)
        
        # Complete the overdue task
        complete_response = client.post(f"/api/v1/tasks/{task_id}/complete")
        assert complete_response.status_code == 200
        completed_task = complete_response.json()
        
        # Date should not change immediately if task was overdue
        completed_due_date = datetime.fromisoformat(completed_task["next_due_date"].replace("Z", "+00:00"))
        completed_due_date_start = completed_due_date.replace(hour=0, minute=0, second=0, microsecond=0)
        
        # Date should remain the same (not updated immediately)
        assert completed_due_date_start.date() == original_due_date_start.date(), (
            f"Date should remain the same ({original_due_date_start.date()}), "
            f"but got {completed_due_date_start.date()}"
        )
        
        # Task should have last_completed_at set
        assert completed_task["last_completed_at"] is not None

    def test_completion_changes_date_for_future_tasks(self, client):
        """Test that completing a task due in the future changes its date immediately."""
        # Create a recurring task due tomorrow
        tomorrow = datetime.now() + timedelta(days=1)
        tomorrow = tomorrow.replace(hour=9, minute=0, second=0, microsecond=0)
        
        task_data = {
            "title": "Daily task",
            "task_type": TaskType.RECURRING.value,
            "recurrence_type": RecurrenceType.DAILY.value,
            "recurrence_interval": 1,
            "next_due_date": tomorrow.isoformat(),
        }
        
        create_response = client.post("/api/v1/tasks/", json=task_data)
        assert create_response.status_code == 201
        task_id = create_response.json()["id"]
        original_due_date = datetime.fromisoformat(
            create_response.json()["next_due_date"].replace("Z", "+00:00")
        )
        
        # Complete the task
        complete_response = client.post(f"/api/v1/tasks/{task_id}/complete")
        assert complete_response.status_code == 200
        completed_task = complete_response.json()
        
        # Date should change immediately if task was due in the future
        completed_due_date = datetime.fromisoformat(
            completed_task["next_due_date"].replace("Z", "+00:00")
        )
        
        # Date should be updated (next occurrence)
        assert completed_due_date > original_due_date, (
            f"Date should be updated, but {completed_due_date} <= {original_due_date}"
        )

    def test_date_updates_on_next_day_for_completed_tasks(self, client, db_session):
        """Test that date updates on next day for tasks completed today."""
        # Create a recurring task due today
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        today_start = today.replace(hour=0, minute=0, second=0, microsecond=0)
        
        task_data = {
            "title": "Daily task",
            "task_type": TaskType.RECURRING.value,
            "recurrence_type": RecurrenceType.DAILY.value,
            "recurrence_interval": 1,
            "next_due_date": today.isoformat(),
        }
        
        create_response = client.post("/api/v1/tasks/", json=task_data)
        assert create_response.status_code == 201
        task_id = create_response.json()["id"]
        
        # Complete the task today
        complete_response = client.post(f"/api/v1/tasks/{task_id}/complete")
        assert complete_response.status_code == 200
        completed_task = complete_response.json()
        
        # Verify date is still today after completion
        completed_due_date = datetime.fromisoformat(
            completed_task["next_due_date"].replace("Z", "+00:00")
        )
        assert completed_due_date.date() == today_start.date()
        
        # Simulate next day: set last_completed_at to yesterday
        # and next_due_date to yesterday
        from backend.models.task import Task
        
        # Get the task from database and update dates to simulate next day
        task = db_session.query(Task).filter(Task.id == task_id).first()
        assert task is not None
        
        yesterday = today_start - timedelta(days=1)
        task.last_completed_at = yesterday.replace(hour=21, minute=0, second=0)
        task.next_due_date = yesterday.replace(hour=9, minute=0, second=0)
        db_session.commit()
        
        # Call get_all_tasks - this should update the date
        updated_tasks = TaskService.get_all_tasks(db_session)
        task_in_list = next((t for t in updated_tasks if t.id == task_id), None)
        
        assert task_in_list is not None
        
        # Date should be updated to today or future
        updated_due_date = task_in_list.next_due_date
        updated_due_date_start = updated_due_date.replace(hour=0, minute=0, second=0, microsecond=0)
        today_check = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
        
        # Date should be updated (today or future)
        assert updated_due_date_start >= today_check, (
            f"Date should be updated to today or future ({today_check.date()}), "
            f"but got {updated_due_date_start.date()}"
        )

    def test_interval_task_completion_date_logic(self, client):
        """Test interval task date logic on completion."""
        # Create an interval task due today
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        today_start = today.replace(hour=0, minute=0, second=0, microsecond=0)
        
        task_data = {
            "title": "Interval task",
            "task_type": TaskType.INTERVAL.value,
            "interval_days": 7,
            "next_due_date": today.isoformat(),
        }
        
        create_response = client.post("/api/v1/tasks/", json=task_data)
        assert create_response.status_code == 201
        task_id = create_response.json()["id"]
        
        # Complete the task today
        complete_response = client.post(f"/api/v1/tasks/{task_id}/complete")
        assert complete_response.status_code == 200
        completed_task = complete_response.json()
        
        # Verify date is still today after completion (not updated immediately)
        completed_due_date = datetime.fromisoformat(
            completed_task["next_due_date"].replace("Z", "+00:00")
        )
        assert completed_due_date.date() == today_start.date(), (
            f"Date should remain today ({today_start.date()}), "
            f"but got {completed_due_date.date()}"
        )
        
        # Verify last_completed_at is set
        assert completed_task["last_completed_at"] is not None

    def test_one_time_task_completion_does_not_change_date(self, client):
        """Test that one-time task completion does not change date."""
        # Create a one-time task due today
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        today_start = today.replace(hour=0, minute=0, second=0, microsecond=0)
        
        task_data = {
            "title": "One-time task",
            "task_type": TaskType.ONE_TIME.value,
            "next_due_date": today.isoformat(),
        }
        
        create_response = client.post("/api/v1/tasks/", json=task_data)
        assert create_response.status_code == 201
        task_id = create_response.json()["id"]
        original_due_date = datetime.fromisoformat(
            create_response.json()["next_due_date"].replace("Z", "+00:00")
        )
        
        # Complete the task
        complete_response = client.post(f"/api/v1/tasks/{task_id}/complete")
        assert complete_response.status_code == 200
        completed_task = complete_response.json()
        
        # Date should not change for one-time tasks
        completed_due_date = datetime.fromisoformat(
            completed_task["next_due_date"].replace("Z", "+00:00")
        )
        assert completed_due_date.date() == original_due_date.date(), (
            f"Date should not change for one-time tasks, "
            f"but {completed_due_date.date()} != {original_due_date.date()}"
        )
        
        # Task should be inactive
        assert completed_task["is_active"] is False

