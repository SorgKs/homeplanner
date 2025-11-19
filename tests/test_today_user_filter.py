"""Тест 'Сегодня' должен возвращать задачи только выбранного пользователя.

Этот тест воспроизводит текущую проблему: в 'Сегодня' попадают задачи,
которые не назначены выбранному пользователю (например, Сергею).
Ожидаемое поведение: если текущий пользователь — Сергей, задачи, не
назначенные Сергею, не должны возвращаться эндпоинтом 'Сегодня'.
"""

from __future__ import annotations

from datetime import datetime
from typing import Generator, TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient

from backend.database import Base, get_db
from backend.main import app
from backend.models.task import TaskType
from tests.utils.api import api_path
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

if TYPE_CHECKING:
    from _pytest.capture import CaptureFixture
    from _pytest.fixtures import FixtureRequest
    from _pytest.logging import LogCaptureFixture
    from _pytest.monkeypatch import MonkeyPatch
    from pytest_mock.plugin import MockerFixture
    from sqlalchemy.orm import Session


# Локальная тестовая БД - используем in-memory SQLite для максимальной производительности
from sqlalchemy.pool import StaticPool

SQLALCHEMY_DATABASE_URL = "sqlite:///:memory:"
engine = create_engine(
    SQLALCHEMY_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,  # Переиспользование одного соединения для всех сессий
    echo=False,  # Отключаем логирование SQL для ускорения
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


@pytest.fixture(scope="module")
def db_setup() -> Generator[None, None, None]:
    """Create test database schema once per module."""
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


def _reset_database() -> None:
    """Очистить все таблицы in-memory БД, сохраняя структуру."""
    from sqlalchemy import text

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
    finally:
        db.close()


@pytest.fixture(scope="function")
def client(db_setup: None) -> Generator[TestClient, None, None]:
    """Создать FastAPI клиент с изолированной БД и очисткой данных между тестами."""

    def override_get_db() -> Generator["Session", None, None]:
        db = TestingSessionLocal()
        try:
            yield db
        finally:
            db.close()

    _reset_database()
    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()


def _create_user(client: TestClient, name: str, email: str | None = None) -> int:
    """Создать пользователя и вернуть его id."""
    payload = {"name": name}
    if email:
        payload["email"] = email
    r = client.post(api_path("/users/")), 
    r = client.post(api_path("/users/"), json=payload)
    assert r.status_code == 201, r.text
    return r.json()["id"]


def _create_task_for_users(client: TestClient, title: str, assigned_ids: list[int]) -> int:
    """Создать разовую задачу на сегодня, назначенную указанным пользователям."""
    today = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
    payload = {
        "title": title,
        "task_type": TaskType.ONE_TIME.value,
        "reminder_time": today.isoformat(),
        "assigned_user_ids": assigned_ids,
    }
    r = client.post(api_path("/tasks/"), json=payload)
    assert r.status_code == 201, r.text
    return r.json()["id"]


def test_today_excludes_tasks_not_assigned_to_selected_user(client: TestClient) -> None:
    """'Сегодня' не должно включать задачи, не назначенные выбранному пользователю (Сергею)."""
    user_sergey = _create_user(client, "Сергей", "sergey@example.com")
    user_anna = _create_user(client, "Анна", "anna@example.com")

    # Задача, назначенная Анне (не Сергею)
    _create_task_for_users(client, "Задача южное окно", [user_anna])
    # Задача, назначенная Сергею
    _create_task_for_users(client, "Полить цветы", [user_sergey])

    # Имитация выбранного пользователя «Сергей» через cookie.
    # Сервер читает выбранного пользователя из cookie 'hp.selectedUserId'.
    client.cookies.set("hp.selectedUserId", str(user_sergey))
    resp = client.get(api_path("/tasks/today"))
    assert resp.status_code == 200
    data = resp.json()

    # Ожидаем, что в ответе нет задачи «южное окно», назначенной Анне
    titles = [t["title"] for t in data]
    assert "Задача южное окно" not in titles, "В 'Сегодня' попала чужая задача, не назначенная Сергею"
    assert "Полить цветы" in titles


