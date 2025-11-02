"""Tests for tasks API."""

from datetime import datetime, timedelta
from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from backend.database import Base, get_db
from backend.main import app
from backend.models.task import RecurrenceType

if TYPE_CHECKING:
    from _pytest.capture import CaptureFixture
    from _pytest.fixtures import FixtureRequest
    from _pytest.logging import LogCaptureFixture
    from _pytest.monkeypatch import MonkeyPatch
    from pytest_mock.plugin import MockerFixture


# Test database
SQLALCHEMY_DATABASE_URL = "sqlite:///./test_tasks.db"

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


def test_create_task(client):
    """Test creating a new task."""
    task_data = {
        "title": "Test Task",
        "description": "Test Description",
        "recurrence_type": RecurrenceType.DAILY.value,
        "recurrence_interval": 1,
        "next_due_date": (datetime.now() + timedelta(days=1)).isoformat(),
        "reminder_time": (datetime.now() + timedelta(hours=12)).isoformat(),
    }
    response = client.post("/api/v1/tasks/", json=task_data)
    assert response.status_code == 201
    data = response.json()
    assert data["title"] == task_data["title"]
    assert data["description"] == task_data["description"]
    assert data["recurrence_type"] == task_data["recurrence_type"]
    assert "id" in data


def test_get_tasks(client):
    """Test getting all tasks."""
    # Create a test task
    task_data = {
        "title": "Test Task",
        "recurrence_type": RecurrenceType.DAILY.value,
        "recurrence_interval": 1,
        "next_due_date": (datetime.now() + timedelta(days=1)).isoformat(),
    }
    client.post("/api/v1/tasks/", json=task_data)

    # Get all tasks
    response = client.get("/api/v1/tasks/")
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) >= 1


def test_get_task_by_id(client):
    """Test getting a task by ID."""
    # Create a test task
    task_data = {
        "title": "Test Task",
        "recurrence_type": RecurrenceType.DAILY.value,
        "recurrence_interval": 1,
        "next_due_date": (datetime.now() + timedelta(days=1)).isoformat(),
    }
    create_response = client.post("/api/v1/tasks/", json=task_data)
    task_id = create_response.json()["id"]

    # Get task by ID
    response = client.get(f"/api/v1/tasks/{task_id}")
    assert response.status_code == 200
    data = response.json()
    assert data["id"] == task_id
    assert data["title"] == task_data["title"]


def test_update_task(client):
    """Test updating a task."""
    # Create a test task
    task_data = {
        "title": "Test Task",
        "recurrence_type": RecurrenceType.DAILY.value,
        "recurrence_interval": 1,
        "next_due_date": (datetime.now() + timedelta(days=1)).isoformat(),
    }
    create_response = client.post("/api/v1/tasks/", json=task_data)
    task_id = create_response.json()["id"]

    # Update task
    update_data = {"title": "Updated Task", "is_active": False}
    response = client.put(f"/api/v1/tasks/{task_id}", json=update_data)
    assert response.status_code == 200
    data = response.json()
    assert data["title"] == "Updated Task"
    assert data["is_active"] is False


def test_delete_task(client):
    """Test deleting a task."""
    # Create a test task
    task_data = {
        "title": "Test Task",
        "recurrence_type": RecurrenceType.DAILY.value,
        "recurrence_interval": 1,
        "next_due_date": (datetime.now() + timedelta(days=1)).isoformat(),
    }
    create_response = client.post("/api/v1/tasks/", json=task_data)
    task_id = create_response.json()["id"]

    # Delete task
    response = client.delete(f"/api/v1/tasks/{task_id}")
    assert response.status_code == 204

    # Verify task is deleted
    get_response = client.get(f"/api/v1/tasks/{task_id}")
    assert get_response.status_code == 404


def test_complete_task(client):
    """Test completing a task."""
    # Create a test task due today
    today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
    task_data = {
        "title": "Test Task",
        "task_type": "recurring",
        "recurrence_type": RecurrenceType.DAILY.value,
        "recurrence_interval": 1,
        "next_due_date": today.isoformat(),
    }
    create_response = client.post("/api/v1/tasks/", json=task_data)
    task_id = create_response.json()["id"]
    original_due_date = create_response.json()["next_due_date"]

    # Complete task
    response = client.post(f"/api/v1/tasks/{task_id}/complete")
    assert response.status_code == 200
    data = response.json()
    assert data["last_completed_at"] is not None
    # Next due date should NOT be updated immediately if due today
    # Date will be updated only after midnight (next day)
    assert data["next_due_date"] == original_due_date

