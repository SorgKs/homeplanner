"""Unit tests for tasks API router."""

from collections.abc import Generator
from datetime import datetime, timedelta
from typing import TYPE_CHECKING

import importlib
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from backend.database import Base, get_db
from backend.main import create_app
from backend.models.task import RecurrenceType, TaskType
from backend.models.user import UserRole
from common.versioning import get_supported_api_versions
from tests.utils.api import api_path

if TYPE_CHECKING:
    from _pytest.fixtures import FixtureRequest
    from sqlalchemy.orm import Session


# Test database - используем in-memory SQLite для максимальной производительности
from sqlalchemy.pool import StaticPool

SQLALCHEMY_DATABASE_URL = "sqlite:///:memory:"

engine = create_engine(
    SQLALCHEMY_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,  # Переиспользование одного соединения для всех сессий
    echo=False,  # Отключаем логирование SQL для ускорения
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _create_user(client: TestClient, name: str, email: str) -> int:
    """Create a user via API and return its identifier."""
    response = client.post(api_path("/users/"), json={"name": name, "email": email})
    assert response.status_code == 201
    return response.json()["id"]


def _create_one_time_task(
    client: TestClient,
    title: str,
    reminder_time: datetime,
    assigned_user_ids: list[int],
) -> int:
    """Create one-time task assigned to provided users."""
    payload = {
        "title": title,
        "task_type": TaskType.ONE_TIME.value,
        "reminder_time": reminder_time.isoformat(),
        "is_active": True,
        "assigned_user_ids": assigned_user_ids,
    }
    response = client.post(api_path("/tasks/"), json=payload)
    assert response.status_code == 201
    return response.json()["id"]


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


@pytest.fixture(params=get_supported_api_versions() or ["0.2"], scope="function")
def api_version(request) -> str:
    """Параметр версии API для прогона по всем поддерживаемым версиям."""
    return request.param


@pytest.fixture(scope="function")
def client(db_session: "Session", api_version: str, monkeypatch: "FixtureRequest") -> TestClient:
    """Create test client with test database."""

    def override_get_db():
        try:
            yield db_session
        finally:
            pass

    # Подменяем загрузку конфигурации, чтобы выставить нужную версию API
    import backend.config as cfg
    original_loader = cfg._load_config_file

    def patched_loader(path):
        data = original_loader(path)
        # Переопределяем версию API
        if "api" not in data:
            data["api"] = {}
        data["api"]["version"] = api_version
        return data

    monkeypatch.setattr(cfg, "_load_config_file", patched_loader)
    # Сбрасываем кеш настроек
    monkeypatch.setattr(cfg, "_settings", None)

    # Также синхронизируем версию для tests.utils.api (get_api_prefix),
    # чтобы префикс путей совпадал с конфигом.
    import common.versioning as ver

    # Мокируем _get_primary_api_version() чтобы возвращала нужную версию
    def mock_get_primary_api_version() -> str:
        return api_version

    monkeypatch.setattr(ver, "_get_primary_api_version", mock_get_primary_api_version)

    # Сбрасываем кеш в tests.utils.api, так как там используется @lru_cache
    import tests.utils.api as test_api

    test_api._api_prefix.cache_clear()

    # Пересоздаём приложение с новой версией API
    test_app = create_app()

    test_app.dependency_overrides[get_db] = override_get_db
    with TestClient(test_app) as test_client:
        yield test_client
    test_app.dependency_overrides.clear()


class TestTasksRouter:
    """Unit tests for tasks API router."""

    def test_create_task(self, client: TestClient) -> None:
        """Test creating a task via API."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = {
            "title": "Test Task",
            "description": "Test Description",
            "task_type": TaskType.ONE_TIME.value,
            "reminder_time": today.isoformat(),
        }
        
        response = client.post(api_path("/tasks/"), json=task_data)
        
        assert response.status_code == 201
        data = response.json()
        assert data["title"] == "Test Task"
        assert data["description"] == "Test Description"
        assert data["task_type"] == TaskType.ONE_TIME.value
        assert "id" in data

    def test_get_tasks(self, client: TestClient) -> None:
        """Test getting all tasks via API."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        
        task1_data = {
            "title": "Task 1",
            "task_type": TaskType.ONE_TIME.value,
            "reminder_time": today.isoformat(),
        }
        task2_data = {
            "title": "Task 2",
            "task_type": TaskType.ONE_TIME.value,
            "reminder_time": (today + timedelta(days=1)).isoformat(),
        }
        
        client.post(api_path("/tasks/"), json=task1_data)
        client.post(api_path("/tasks/"), json=task2_data)
        
        response = client.get(api_path("/tasks/"))
        
        assert response.status_code == 200
        data = response.json()
        assert len(data) == 2

    def test_get_tasks_active_only(self, client: TestClient) -> None:
        """Test getting only active tasks via API."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)

        task1_data = {
            "title": "Active Task",
            "task_type": TaskType.ONE_TIME.value,
            "reminder_time": today.isoformat(),
        }
        task2_data = {
            "title": "Inactive Task",
            "task_type": TaskType.ONE_TIME.value,
            "reminder_time": today.isoformat(),
        }

        create_response1 = client.post(api_path("/tasks/"), json=task1_data)
        create_response2 = client.post(api_path("/tasks/"), json=task2_data)

        # Update task2 to be inactive
        task2_id = create_response2.json()["id"]
        current = client.get(api_path(f"/tasks/{task2_id}")).json()
        client.put(api_path(f"/tasks/{task2_id}"), json={"enabled": False})

        response = client.get(api_path("/tasks/") + "?enabled_only=true")

        assert response.status_code == 200
        data = response.json()
        assert len(data) == 1
        assert data[0]["title"] == "Active Task"

    def test_get_task(self, client: TestClient) -> None:
        """Test getting a specific task via API."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = {
            "title": "Test Task",
            "task_type": TaskType.ONE_TIME.value,
            "reminder_time": today.isoformat(),
        }
        
        create_response = client.post(api_path("/tasks/"), json=task_data)
        task_id = create_response.json()["id"]
        
        response = client.get(api_path(f"/tasks/{task_id}"))
        
        assert response.status_code == 200
        data = response.json()
        assert data["id"] == task_id
        assert data["title"] == "Test Task"

    def test_get_task_not_found(self, client: TestClient) -> None:
        """Test getting a non-existent task via API."""
        response = client.get(api_path("/tasks/999"))
        
        assert response.status_code == 404

    def test_update_task(self, client: TestClient) -> None:
        """Test updating a task via API."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = {
            "title": "Original Title",
            "task_type": TaskType.ONE_TIME.value,
            "reminder_time": today.isoformat(),
        }
        
        create_response = client.post(api_path("/tasks/"), json=task_data)
        task_id = create_response.json()["id"]
        
        update_data = {"title": "Updated Title"}
        response = client.put(api_path(f"/tasks/{task_id}"), json=update_data)
        
        assert response.status_code == 200
        data = response.json()
        assert data["title"] == "Updated Title"

    def test_update_task_not_found(self, client: TestClient) -> None:
        """Test updating a non-existent task via API."""
        update_data = {"title": "Updated Title"}
        response = client.put(api_path("/tasks/999"), json=update_data)
        
        assert response.status_code == 404

    def test_delete_task(self, client: TestClient) -> None:
        """Test deleting a task via API."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = {
            "title": "Test Task",
            "task_type": TaskType.ONE_TIME.value,
            "reminder_time": today.isoformat(),
        }
        
        create_response = client.post(api_path("/tasks/"), json=task_data)
        task_id = create_response.json()["id"]
        
        response = client.delete(api_path(f"/tasks/{task_id}"))
        
        assert response.status_code == 204
        
        # Verify task is deleted
        get_response = client.get(api_path(f"/tasks/{task_id}"))
        assert get_response.status_code == 404

    def test_delete_task_not_found(self, client: TestClient) -> None:
        """Test deleting a non-existent task via API."""
        response = client.delete(api_path("/tasks/999"))
        
        assert response.status_code == 404

    def test_task_with_assignees(self, client: TestClient) -> None:
        """Ensure tasks can be assigned to users."""
        user_response = client.post(
            api_path("/users/"),
            json={"name": "Assignee One", "email": "user1@example.com"},
        )
        assert user_response.status_code == 201
        user_id = user_response.json()["id"]

        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = {
            "title": "Task with assignee",
            "task_type": TaskType.ONE_TIME.value,
            "reminder_time": today.isoformat(),
            "assigned_user_ids": [user_id],
        }

        response = client.post(api_path("/tasks/"), json=task_data)
        assert response.status_code == 201
        created = response.json()
        assert created["assigned_user_ids"] == [user_id]
        assert len(created["assignees"]) == 1
        assert created["assignees"][0]["name"] == "Assignee One"
        assert created["assignees"][0]["role"] == UserRole.REGULAR.value
        assert created["assignees"][0]["is_active"] is True

        task_id = created["id"]
        # Конфликты обрабатываются только на сервере по времени обновления
        update_resp = client.put(
            api_path(f"/tasks/{task_id}"),
            json={"assigned_user_ids": []},
        )
        assert update_resp.status_code == 200
        updated = update_resp.json()
        assert updated["assigned_user_ids"] == []
        assert updated["assignees"] == []

    def test_user_role_and_status_crud(self, client: TestClient) -> None:
        """Ensure user CRUD handles role and active status."""
        create_payload = {
            "name": "Admin User",
            "email": "admin@example.com",
            "role": UserRole.ADMIN.value,
            "is_active": True,
        }
        create_resp = client.post(api_path("/users/"), json=create_payload)
        assert create_resp.status_code == 201
        created = create_resp.json()
        assert created["role"] == UserRole.ADMIN.value
        assert created["is_active"] is True
        user_id = created["id"]

        list_resp = client.get(api_path("/users/"))
        assert list_resp.status_code == 200
        data = list_resp.json()
        assert any(u["id"] == user_id for u in data)

        update_resp = client.put(api_path(f"/users/{user_id}"), json={"role": UserRole.GUEST.value, "is_active": False})
        assert update_resp.status_code == 200
        updated = update_resp.json()
        assert updated["role"] == UserRole.GUEST.value
        assert updated["is_active"] is False

    def test_complete_task(self, client: TestClient) -> None:
        """Test completing a task via API."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = {
            "title": "Test Task",
            "task_type": TaskType.ONE_TIME.value,
            "reminder_time": today.isoformat(),
        }

        create_response = client.post(api_path("/tasks/"), json=task_data)
        task_id = create_response.json()["id"]

        response = client.post(api_path(f"/tasks/{task_id}/complete"))

        assert response.status_code == 200
        data = response.json()
        # For one-time tasks: enabled -> False, completed -> True, reminder_time unchanged
        assert data["completed"] is True
        assert data["enabled"] is False

    def test_complete_task_not_found(self, client: TestClient) -> None:
        """Test completing a non-existent task via API."""
        response = client.post(api_path("/tasks/999/complete"))
        
        assert response.status_code == 404

    def test_get_upcoming_tasks(self, client: TestClient) -> None:
        """Test getting upcoming tasks via API."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        
        task1_data = {
            "title": "Task Today",
            "task_type": TaskType.ONE_TIME.value,
            "reminder_time": today.isoformat(),
            "is_active": True,
        }
        task2_data = {
            "title": "Task Tomorrow",
            "task_type": TaskType.ONE_TIME.value,
            "reminder_time": (today + timedelta(days=1)).isoformat(),
            "is_active": True,
        }
        
        client.post(api_path("/tasks/"), json=task1_data)
        client.post(api_path("/tasks/"), json=task2_data)
        
        response = client.get(api_path("/tasks/") + "?days_ahead=3")
        
        assert response.status_code == 200
        data = response.json()
        assert len(data) >= 1  # At least tasks due in next 3 days

    def test_get_today_task_ids_without_cookie_returns_empty(self, client: TestClient) -> None:
        """Request without cookie should return empty list."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        user_id = _create_user(client, "NoCookie User", "nocookie@example.com")

        _create_one_time_task(client, "Task Today", today, [user_id])

        response = client.get(api_path("/tasks/today/ids"))
        assert response.status_code == 200
        assert response.json() == []

    def test_get_today_task_ids_with_unknown_user_returns_empty(self, client: TestClient) -> None:
        """Cookie with non-existing user ID should yield empty list."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        valid_user_id = _create_user(client, "Known User", "known@example.com")
        _create_one_time_task(client, "Task Today", today, [valid_user_id])

        client.cookies.set("hp.selectedUserId", str(valid_user_id + 999))

        response = client.get(api_path("/tasks/today/ids"))
        assert response.status_code == 200
        assert response.json() == []

    def test_get_today_task_ids_with_valid_user(self, client: TestClient) -> None:
        """Returned IDs must match tasks assigned to selected user."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        yesterday = today - timedelta(days=1)
        tomorrow = today + timedelta(days=1)
        user_id = _create_user(client, "Today User", "today@example.com")

        id_today = _create_one_time_task(client, "Task Today", today, [user_id])
        id_overdue = _create_one_time_task(client, "Task Overdue", yesterday, [user_id])
        _create_one_time_task(client, "Task Future", tomorrow, [user_id])

        client.cookies.set("hp.selectedUserId", str(user_id))

        response = client.get(api_path("/tasks/today/ids"))
        assert response.status_code == 200
        assert set(response.json()) == {id_today, id_overdue}

