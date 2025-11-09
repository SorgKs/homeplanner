"""Интеграционные тесты FastAPI-роутера задач."""

from __future__ import annotations

from collections.abc import Generator
from datetime import datetime, timedelta
from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from backend.database import Base, get_db
from backend.main import app
from backend.models.task import RecurrenceType, TaskType
from tests.utils import (
    api_path,
    create_sqlite_engine,
    isoformat,
    session_scope,
    test_client_with_session,
)

if TYPE_CHECKING:
    from _pytest.fixtures import FixtureRequest


engine, SessionLocal = create_sqlite_engine("test_tasks_router.db")


@pytest.fixture(scope="function")
def db_session() -> Generator[Session, None, None]:
    """Создать и очистить тестовую БД."""

    with session_scope(Base, engine, SessionLocal) as session:
        yield session


@pytest.fixture(scope="function")
def client(db_session: Session) -> Generator[TestClient, None, None]:
    """Вернуть тестовый клиент с подменённой БД."""

    with test_client_with_session(app, get_db, db_session) as test_client:
        yield test_client


class TestTasksRouter:
    """Набор тестов REST-роутов задач."""

    def test_create_task(self, client: TestClient) -> None:
        """Создание задачи возвращает корректные поля."""

        reminder_time = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        task_data = {
            "title": "Test Task",
            "description": "Test Description",
            "task_type": TaskType.ONE_TIME.value,
            "reminder_time": isoformat(reminder_time),
        }
        
        response = client.post(api_path("/tasks/"), json=task_data)
        
        assert response.status_code == 201
        data = response.json()
        assert data["title"] == "Test Task"
        assert data["description"] == "Test Description"
        assert data["task_type"] == TaskType.ONE_TIME.value
        assert data["reminder_time"] == isoformat(reminder_time)
        assert data["completed"] is False
        assert data["active"] is True

    def test_get_tasks(self, client: TestClient) -> None:
        """Получение всех задач возвращает созданные элементы."""

        reminder_time = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        
        client.post(
            api_path("/tasks/"),
            json={
            "title": "Task 1",
            "task_type": TaskType.ONE_TIME.value,
                "reminder_time": isoformat(reminder_time),
            },
        )
        client.post(
            api_path("/tasks/"),
            json={
            "title": "Task 2",
            "task_type": TaskType.ONE_TIME.value,
                "reminder_time": isoformat(reminder_time + timedelta(days=1)),
            },
        )
        
        response = client.get(api_path("/tasks/"))
        
        assert response.status_code == 200
        data = response.json()
        assert len(data) == 2

    def test_get_tasks_active_only(self, client: TestClient) -> None:
        """Фильтр active_only возвращает только активные задачи."""

        reminder_time = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        
        active = client.post(
            api_path("/tasks/"),
            json={
            "title": "Active Task",
            "task_type": TaskType.ONE_TIME.value,
                "reminder_time": isoformat(reminder_time),
            },
        ).json()
        inactive = client.post(
            api_path("/tasks/"),
            json={
            "title": "Inactive Task",
            "task_type": TaskType.ONE_TIME.value,
                "reminder_time": isoformat(reminder_time),
            },
        ).json()

        client.put(
            api_path(f"/tasks/{inactive['id']}"),
            json={"revision": inactive["revision"], "active": False},
        )
        
        response = client.get(f"{api_path('/tasks/')}?active_only=true")
        
        assert response.status_code == 200
        data = response.json()
        assert len(data) == 1
        assert data[0]["title"] == active["title"]

    def test_get_task(self, client: TestClient) -> None:
        """Получение задачи по ID возвращает созданный объект."""

        reminder_time = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        created = client.post(
            api_path("/tasks/"),
            json={
            "title": "Test Task",
            "task_type": TaskType.ONE_TIME.value,
                "reminder_time": isoformat(reminder_time),
            },
        ).json()
        
        response = client.get(api_path(f"/tasks/{created['id']}"))
        
        assert response.status_code == 200
        data = response.json()
        assert data["id"] == created["id"]
        assert data["title"] == created["title"]

    def test_get_task_not_found(self, client: TestClient) -> None:
        """404 при запросе несуществующей задачи."""

        response = client.get(api_path("/tasks/999"))
        
        assert response.status_code == 404

    def test_update_task(self, client: TestClient) -> None:
        """Обновление задачи меняет поля и ревизию."""

        reminder_time = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        created = client.post(
            api_path("/tasks/"),
            json={
            "title": "Original Title",
            "task_type": TaskType.ONE_TIME.value,
                "reminder_time": isoformat(reminder_time),
            },
        ).json()
        
        update_data = {"revision": created["revision"], "title": "Updated Title"}
        response = client.put(api_path(f"/tasks/{created['id']}"), json=update_data)
        
        assert response.status_code == 200
        data = response.json()
        assert data["title"] == "Updated Title"
        assert data["revision"] == created["revision"] + 1

    def test_update_task_not_found(self, client: TestClient) -> None:
        """Обновление несуществующей задачи возвращает 404."""

        update_data = {"revision": 0, "title": "Updated Title"}
        response = client.put(api_path("/tasks/999"), json=update_data)
        
        assert response.status_code == 404

    def test_delete_task(self, client: TestClient) -> None:
        """Удаление задачи доступно по REST."""

        reminder_time = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        created = client.post(
            api_path("/tasks/"),
            json={
            "title": "Test Task",
            "task_type": TaskType.ONE_TIME.value,
                "reminder_time": isoformat(reminder_time),
            },
        ).json()
        
        response = client.delete(api_path(f"/tasks/{created['id']}"))
        
        assert response.status_code == 204
        assert client.get(api_path(f"/tasks/{created['id']}")).status_code == 404

    def test_delete_task_not_found(self, client: TestClient) -> None:
        """Удаление несуществующей задачи возвращает 404."""

        response = client.delete(api_path("/tasks/999"))
        
        assert response.status_code == 404

    def test_complete_task(self, client: TestClient) -> None:
        """Выполнение задачи проставляет флаг completed."""

        reminder_time = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
        created = client.post(
            api_path("/tasks/"),
            json={
            "title": "Test Task",
            "task_type": TaskType.ONE_TIME.value,
                "reminder_time": isoformat(reminder_time),
            },
        ).json()
        
        response = client.post(api_path(f"/tasks/{created['id']}/complete"))
        
        assert response.status_code == 200
        data = response.json()
        assert data["completed"] is True
        assert data["active"] is False

    def test_complete_task_not_found(self, client: TestClient) -> None:
        """404 при выполнении несуществующей задачи."""

        response = client.post(api_path("/tasks/999/complete"))
        
        assert response.status_code == 404

    def test_get_upcoming_tasks(self, client: TestClient) -> None:
        """Параметр days_ahead возвращает задачи в окне по времени."""
        
        now = datetime.now().replace(microsecond=0)
        client.post(
            api_path("/tasks/"),
            json={
            "title": "Task Today",
            "task_type": TaskType.ONE_TIME.value,
                "reminder_time": isoformat(now),
                "active": True,
            },
        )
        client.post(
            api_path("/tasks/"),
            json={
            "title": "Task Tomorrow",
            "task_type": TaskType.ONE_TIME.value,
                "reminder_time": isoformat(now + timedelta(days=1)),
                "active": True,
            },
        )
        
        response = client.get(f"{api_path('/tasks/')}?days_ahead=3")
        
        assert response.status_code == 200
        data = response.json()
        titles = {task["title"] for task in data}
        assert "Task Today" in titles

