"""Tests for /tasks/sync-queue endpoint."""

from collections.abc import Generator
from datetime import datetime, timedelta
from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from backend.database import Base, get_db
from backend.main import create_app
from backend.models.task import TaskType
from backend.schemas.task import TaskCreate

if TYPE_CHECKING:
    from sqlalchemy.orm import Session


SQLALCHEMY_DATABASE_URL = "sqlite:///:memory:"

engine = create_engine(
    SQLALCHEMY_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
    echo=False,
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
    """Create test database session and clean data between tests."""
    db = TestingSessionLocal()
    try:
        with db.begin():
            db.execute(text("PRAGMA foreign_keys = OFF"))
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


@pytest.fixture
def client(db_session: "Session") -> Generator[TestClient, None, None]:
    """Create test client with database session override."""
    app = create_app()

    def override_get_db() -> Generator["Session", None, None]:
        yield db_session

    app.dependency_overrides[get_db] = override_get_db

    with TestClient(app) as test_client:
        yield test_client

    app.dependency_overrides.clear()


def _iso(dt: datetime) -> str:
    """Convert datetime to ISO format string (local time, no timezone)."""
    # Убираем timezone если есть, так как система использует только локальное время
    dt_naive = dt.replace(tzinfo=None) if dt.tzinfo else dt
    return dt_naive.isoformat()


class TestSyncQueueEndpoint:
    """Integration tests for /tasks/sync-queue."""

    def test_create_and_update_in_single_batch(self, client: TestClient) -> None:
        """Create task, then update title in one batch and get final state."""
        now = datetime(2025, 1, 1, 9, 0)

        create_payload = {
            "title": "Initial",
            "description": "Desc",
            "task_type": TaskType.ONE_TIME.value,
            "reminder_time": _iso(now),
        }

        body = {
            "operations": [
                {
                    "operation": "create",
                    "timestamp": _iso(now),
                    "task_id": None,
                    "payload": create_payload,
                },
                {
                    "operation": "update",
                    "timestamp": _iso(now + timedelta(seconds=1)),
                    "task_id": 1,
                    "payload": {"title": "Updated"},
                },
            ]
        }

        resp = client.post("/api/v0.2/tasks/sync-queue", json=body)
        assert resp.status_code == 200, resp.text
        tasks = resp.json()
        assert len(tasks) == 1
        assert tasks[0]["id"] == 1
        assert tasks[0]["title"] == "Updated"

    def test_delete_in_batch_removes_task(self, client: TestClient) -> None:
        """Task created then deleted in same batch should not be present."""
        now = datetime(2025, 1, 1, 9, 0)

        create_payload = {
            "title": "To delete",
            "description": None,
            "task_type": TaskType.ONE_TIME.value,
            "reminder_time": _iso(now),
        }

        body = {
            "operations": [
                {
                    "operation": "create",
                    "timestamp": _iso(now),
                    "task_id": None,
                    "payload": create_payload,
                },
                {
                    "operation": "delete",
                    "timestamp": _iso(now + timedelta(seconds=1)),
                    "task_id": 1,
                    "payload": None,
                },
            ]
        }

        resp = client.post("/api/v0.2/tasks/sync-queue", json=body)
        assert resp.status_code == 200, resp.text
        tasks = resp.json()
        # All tasks should be gone after create+delete
        assert tasks == []


