"""Unit tests for tasks API router."""

from datetime import datetime, timedelta
from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from backend.database import Base, get_db
from backend.main import app
from backend.models.task import RecurrenceType, TaskType

if TYPE_CHECKING:
    from _pytest.fixtures import FixtureRequest
    from sqlalchemy.orm import Session


# Test database
SQLALCHEMY_DATABASE_URL = "sqlite:///./test_tasks_router.db"

engine = create_engine(
    SQLALCHEMY_DATABASE_URL, connect_args={"check_same_thread": False}
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


@pytest.fixture(scope="function")
def db_session() -> "Session":
    """Create test database session."""
    Base.metadata.create_all(bind=engine)
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()
        Base.metadata.drop_all(bind=engine)


@pytest.fixture(scope="function")
def client(db_session: "Session") -> TestClient:
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


class TestTasksRouter:
    """Unit tests for tasks API router."""

    def test_create_task(self, client: TestClient) -> None:
        """Test creating a task via API."""
        today = datetime.utcnow().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = {
            "title": "Test Task",
            "description": "Test Description",
            "task_type": TaskType.ONE_TIME.value,
            "next_due_date": today.isoformat(),
        }
        
        response = client.post("/api/v1/tasks/", json=task_data)
        
        assert response.status_code == 201
        data = response.json()
        assert data["title"] == "Test Task"
        assert data["description"] == "Test Description"
        assert data["task_type"] == TaskType.ONE_TIME.value
        assert "id" in data

    def test_get_tasks(self, client: TestClient) -> None:
        """Test getting all tasks via API."""
        today = datetime.utcnow().replace(hour=9, minute=0, second=0, microsecond=0)
        
        task1_data = {
            "title": "Task 1",
            "task_type": TaskType.ONE_TIME.value,
            "next_due_date": today.isoformat(),
        }
        task2_data = {
            "title": "Task 2",
            "task_type": TaskType.ONE_TIME.value,
            "next_due_date": (today + timedelta(days=1)).isoformat(),
        }
        
        client.post("/api/v1/tasks/", json=task1_data)
        client.post("/api/v1/tasks/", json=task2_data)
        
        response = client.get("/api/v1/tasks/")
        
        assert response.status_code == 200
        data = response.json()
        assert len(data) == 2

    def test_get_tasks_active_only(self, client: TestClient) -> None:
        """Test getting only active tasks via API."""
        today = datetime.utcnow().replace(hour=9, minute=0, second=0, microsecond=0)
        
        task1_data = {
            "title": "Active Task",
            "task_type": TaskType.ONE_TIME.value,
            "next_due_date": today.isoformat(),
        }
        task2_data = {
            "title": "Inactive Task",
            "task_type": TaskType.ONE_TIME.value,
            "next_due_date": today.isoformat(),
        }
        
        create_response1 = client.post("/api/v1/tasks/", json=task1_data)
        create_response2 = client.post("/api/v1/tasks/", json=task2_data)
        
        # Update task2 to be inactive
        task2_id = create_response2.json()["id"]
        client.put(f"/api/v1/tasks/{task2_id}", json={"is_active": False})
        
        response = client.get("/api/v1/tasks/?active_only=true")
        
        assert response.status_code == 200
        data = response.json()
        assert len(data) == 1
        assert data[0]["title"] == "Active Task"

    def test_get_task(self, client: TestClient) -> None:
        """Test getting a specific task via API."""
        today = datetime.utcnow().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = {
            "title": "Test Task",
            "task_type": TaskType.ONE_TIME.value,
            "next_due_date": today.isoformat(),
        }
        
        create_response = client.post("/api/v1/tasks/", json=task_data)
        task_id = create_response.json()["id"]
        
        response = client.get(f"/api/v1/tasks/{task_id}")
        
        assert response.status_code == 200
        data = response.json()
        assert data["id"] == task_id
        assert data["title"] == "Test Task"

    def test_get_task_not_found(self, client: TestClient) -> None:
        """Test getting a non-existent task via API."""
        response = client.get("/api/v1/tasks/999")
        
        assert response.status_code == 404

    def test_update_task(self, client: TestClient) -> None:
        """Test updating a task via API."""
        today = datetime.utcnow().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = {
            "title": "Original Title",
            "task_type": TaskType.ONE_TIME.value,
            "next_due_date": today.isoformat(),
        }
        
        create_response = client.post("/api/v1/tasks/", json=task_data)
        task_id = create_response.json()["id"]
        
        update_data = {"title": "Updated Title"}
        response = client.put(f"/api/v1/tasks/{task_id}", json=update_data)
        
        assert response.status_code == 200
        data = response.json()
        assert data["title"] == "Updated Title"

    def test_update_task_not_found(self, client: TestClient) -> None:
        """Test updating a non-existent task via API."""
        update_data = {"title": "Updated Title"}
        response = client.put("/api/v1/tasks/999", json=update_data)
        
        assert response.status_code == 404

    def test_delete_task(self, client: TestClient) -> None:
        """Test deleting a task via API."""
        today = datetime.utcnow().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = {
            "title": "Test Task",
            "task_type": TaskType.ONE_TIME.value,
            "next_due_date": today.isoformat(),
        }
        
        create_response = client.post("/api/v1/tasks/", json=task_data)
        task_id = create_response.json()["id"]
        
        response = client.delete(f"/api/v1/tasks/{task_id}")
        
        assert response.status_code == 204
        
        # Verify task is deleted
        get_response = client.get(f"/api/v1/tasks/{task_id}")
        assert get_response.status_code == 404

    def test_delete_task_not_found(self, client: TestClient) -> None:
        """Test deleting a non-existent task via API."""
        response = client.delete("/api/v1/tasks/999")
        
        assert response.status_code == 404

    def test_complete_task(self, client: TestClient) -> None:
        """Test completing a task via API."""
        today = datetime.utcnow().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = {
            "title": "Test Task",
            "task_type": TaskType.ONE_TIME.value,
            "next_due_date": today.isoformat(),
        }
        
        create_response = client.post("/api/v1/tasks/", json=task_data)
        task_id = create_response.json()["id"]
        
        response = client.post(f"/api/v1/tasks/{task_id}/complete")
        
        assert response.status_code == 200
        data = response.json()
        assert data["last_completed_at"] is not None

    def test_complete_task_not_found(self, client: TestClient) -> None:
        """Test completing a non-existent task via API."""
        response = client.post("/api/v1/tasks/999/complete")
        
        assert response.status_code == 404

    def test_get_upcoming_tasks(self, client: TestClient) -> None:
        """Test getting upcoming tasks via API."""
        today = datetime.utcnow().replace(hour=9, minute=0, second=0, microsecond=0)
        
        task1_data = {
            "title": "Task Today",
            "task_type": TaskType.ONE_TIME.value,
            "next_due_date": today.isoformat(),
            "is_active": True,
        }
        task2_data = {
            "title": "Task Tomorrow",
            "task_type": TaskType.ONE_TIME.value,
            "next_due_date": (today + timedelta(days=1)).isoformat(),
            "is_active": True,
        }
        
        client.post("/api/v1/tasks/", json=task1_data)
        client.post("/api/v1/tasks/", json=task2_data)
        
        response = client.get("/api/v1/tasks/?days_ahead=3")
        
        assert response.status_code == 200
        data = response.json()
        assert len(data) >= 1  # At least tasks due in next 3 days

