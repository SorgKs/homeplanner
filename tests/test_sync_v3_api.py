"""Integration tests for sync API v0.3 endpoints."""

from collections.abc import Generator
from datetime import datetime, timedelta
from typing import TYPE_CHECKING

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


def _create_task(client: TestClient, title: str, reminder_time: datetime, assigned_user_ids: list[int] = None) -> dict:
    """Create a task via API and return task data."""
    task_data = {
        "title": title,
        "description": f"Description for {title}",
        "task_type": TaskType.ONE_TIME.value,
        "reminder_time": reminder_time.isoformat(),
        "is_active": True,
    }
    if assigned_user_ids:
        task_data["assigned_user_ids"] = assigned_user_ids

    response = client.post(api_path("/tasks/"), json=task_data)
    assert response.status_code == 201
    return response.json()


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


class TestSyncV3API:
    """Integration tests for sync API v0.3 endpoints."""

    def test_hash_check_empty_client_hashes(self, client: TestClient) -> None:
        """Test hash-check with empty client hashes."""
        request_data = {
            "entity_type": "tasks",
            "hashes": []
        }

        response = client.post(api_path("/sync/hash-check"), json=request_data)

        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "checked"
        assert data["conflicts"] == []
        assert data["missing_on_client"] == []
        assert data["missing_on_server"] == []

    def test_hash_check_with_matching_hashes(self, client: TestClient) -> None:
        """Test hash-check with client hashes matching server state."""
        # Create a task on server
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task = _create_task(client, "Test Task", today)

        # Calculate expected hash manually for verification
        # This mimics what Android client would send
        expected_data = f"{task['id']}|Test Task|Description for Test Task|{TaskType.ONE_TIME.value}||||{today.isoformat()}||True|False||{task['updated_at']}"
        import hashlib
        expected_hash = hashlib.sha256(expected_data.encode('utf-8')).hexdigest()

        request_data = {
            "entity_type": "tasks",
            "hashes": [{"id": task["id"], "hash": expected_hash}]
        }

        response = client.post(api_path("/sync/hash-check"), json=request_data)

        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "checked"
        assert data["conflicts"] == []
        assert data["missing_on_client"] == []
        assert data["missing_on_server"] == []

    def test_hash_check_with_conflicts(self, client: TestClient) -> None:
        """Test hash-check detecting conflicts."""
        # Create a task on server
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task = _create_task(client, "Test Task", today)

        # Send wrong hash (simulate conflict)
        request_data = {
            "entity_type": "tasks",
            "hashes": [{"id": task["id"], "hash": "wrong_hash_12345678901234567890123456789012"}]
        }

        response = client.post(api_path("/sync/hash-check"), json=request_data)

        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "checked"
        assert len(data["conflicts"]) == 1
        assert data["conflicts"][0]["id"] == task["id"]
        assert "client_hash" in data["conflicts"][0]
        assert "server_hash" in data["conflicts"][0]

    def test_hash_check_missing_on_client(self, client: TestClient) -> None:
        """Test hash-check detecting entities missing on client."""
        # Create a task on server
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task = _create_task(client, "Test Task", today)

        # Client sends no hashes (missing on client)
        request_data = {
            "entity_type": "tasks",
            "hashes": []
        }

        response = client.post(api_path("/sync/hash-check"), json=request_data)

        assert response.status_code == 200
        data = response.json()
        assert len(data["missing_on_client"]) == 1
        assert data["missing_on_client"][0]["id"] == task["id"]

    def test_hash_check_missing_on_server(self, client: TestClient) -> None:
        """Test hash-check detecting entities missing on server."""
        # Client has a hash for non-existent task
        request_data = {
            "entity_type": "tasks",
            "hashes": [{"id": 999, "hash": "some_hash_12345678901234567890123456789012"}]
        }

        response = client.post(api_path("/sync/hash-check"), json=request_data)

        assert response.status_code == 200
        data = response.json()
        assert len(data["missing_on_server"]) == 1
        assert data["missing_on_server"][0]["id"] == 999

    def test_full_state_tasks(self, client: TestClient) -> None:
        """Test getting full state of tasks."""
        # Create multiple tasks
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        user_id = _create_user(client, "Test User", "test@example.com")

        task1 = _create_task(client, "Task 1", today, [user_id])
        task2 = _create_task(client, "Task 2", today + timedelta(days=1))

        response = client.get(api_path("/sync/full-state/tasks"))

        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "full_state"
        assert data["entity_type"] == "tasks"
        assert len(data["entities"]) == 2

        # Check that entities contain required fields
        task_ids = {task["id"] for task in data["entities"]}
        assert task1["id"] in task_ids
        assert task2["id"] in task_ids

        # Verify timestamp
        assert "server_timestamp" in data

    def test_full_state_users(self, client: TestClient) -> None:
        """Test getting full state of users."""
        user1_id = _create_user(client, "User 1", "user1@example.com")
        user2_id = _create_user(client, "User 2", "user2@example.com")

        response = client.get(api_path("/sync/full-state/users"))

        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "full_state"
        assert data["entity_type"] == "users"
        assert len(data["entities"]) == 2

        user_ids = {user["id"] for user in data["entities"]}
        assert user1_id in user_ids
        assert user2_id in user_ids

    def test_full_state_groups(self, client: TestClient) -> None:
        """Test getting full state of groups (should be empty for now)."""
        response = client.get(api_path("/sync/full-state/groups"))

        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "full_state"
        assert data["entity_type"] == "groups"
        # Groups are not implemented yet, so should be empty
        assert len(data["entities"]) == 0

    def test_full_state_invalid_entity_type(self, client: TestClient) -> None:
        """Test full-state with invalid entity type."""
        response = client.get(api_path("/sync/full-state/invalid"))

        assert response.status_code == 400

    def test_resolve_conflicts_no_conflicts(self, client: TestClient) -> None:
        """Test resolve-conflicts with no conflicts to resolve."""
        request_data = {
            "entity_type": "tasks",
            "resolutions": []
        }

        response = client.post(api_path("/sync/resolve-conflicts"), json=request_data)

        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "resolved"
        assert data["resolved_count"] == 0
        assert data["failed_count"] == 0

    def test_resolve_conflicts_task_completion(self, client: TestClient) -> None:
        """Test resolving task completion conflict (last-write-wins)."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task = _create_task(client, "Test Task", today)

        # Simulate conflict resolution where client completed the task later
        client_timestamp = datetime.now() + timedelta(minutes=5)
        request_data = {
            "entity_type": "tasks",
            "resolutions": [{
                "id": task["id"],
                "client_data": {
                    "completed": True,
                    "updated_at": client_timestamp.isoformat()
                }
            }]
        }

        response = client.post(api_path("/sync/resolve-conflicts"), json=request_data)

        assert response.status_code == 200
        data = response.json()
        assert data["resolved_count"] == 1
        assert data["failed_count"] == 0

        # Verify task was updated
        get_response = client.get(api_path(f"/tasks/{task['id']}"))
        assert get_response.status_code == 200
        updated_task = get_response.json()
        assert updated_task["completed"] is True

    def test_resolve_conflicts_invalid_entity_type(self, client: TestClient) -> None:
        """Test resolve-conflicts with invalid entity type."""
        request_data = {
            "entity_type": "invalid",
            "resolutions": []
        }

        response = client.post(api_path("/sync/resolve-conflicts"), json=request_data)

        assert response.status_code == 200
        # Should fail for invalid entity type
        data = response.json()
        assert data["failed_count"] > 0

    def test_hash_check_users(self, client: TestClient) -> None:
        """Test hash-check for users entity type."""
        user = {"id": _create_user(client, "Test User", "test@example.com"), "name": "Test User"}

        # Calculate expected hash
        expected_data = f"{user['id']}|{user['name']}"
        import hashlib
        expected_hash = hashlib.sha256(expected_data.encode('utf-8')).hexdigest()

        request_data = {
            "entity_type": "users",
            "hashes": [{"id": user["id"], "hash": expected_hash}]
        }

        response = client.post(api_path("/sync/hash-check"), json=request_data)

        assert response.status_code == 200
        data = response.json()
        assert data["conflicts"] == []

    def test_end_to_end_sync_workflow(self, client: TestClient) -> None:
        """Test complete sync workflow: create -> hash-check -> resolve conflicts -> full-state."""
        today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        user_id = _create_user(client, "Sync Test User", "sync@example.com")

        # 1. Create task
        task = _create_task(client, "Sync Test Task", today, [user_id])

        # 2. Hash check - should match
        expected_data = f"{task['id']}|Sync Test Task|Description for Sync Test Task|{TaskType.ONE_TIME.value}||||{today.isoformat()}||True|False|{user_id}|{task['updated_at']}"
        import hashlib
        expected_hash = hashlib.sha256(expected_data.encode('utf-8')).hexdigest()

        request_data = {
            "entity_type": "tasks",
            "hashes": [{"id": task["id"], "hash": expected_hash}]
        }
        response = client.post(api_path("/sync/hash-check"), json=request_data)
        assert response.status_code == 200
        assert response.json()["conflicts"] == []

        # 3. Simulate conflict and resolve
        conflict_resolution = {
            "entity_type": "tasks",
            "resolutions": [{
                "id": task["id"],
                "client_data": {
                    "title": "Updated Title",
                    "updated_at": (datetime.now() + timedelta(minutes=1)).isoformat()
                }
            }]
        }
        response = client.post(api_path("/sync/resolve-conflicts"), json=conflict_resolution)
        assert response.status_code == 200

        # 4. Get full state to verify
        response = client.get(api_path("/sync/full-state/tasks"))
        assert response.status_code == 200
        entities = response.json()["entities"]
        updated_task = next(t for t in entities if t["id"] == task["id"])
        assert updated_task["title"] == "Updated Title"