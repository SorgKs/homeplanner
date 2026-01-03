"""Общие вспомогательные функции для тестов HomePlanner."""

from __future__ import annotations

from collections.abc import Generator
from contextlib import contextmanager
from datetime import datetime
from typing import Callable

from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session, sessionmaker

from backend.config import get_settings


def normalize_datetime(dt: datetime) -> datetime:
    """Нормализовать datetime, обнуляя микросекунды."""

    return dt.replace(microsecond=0)


def isoformat(dt: datetime) -> str:
    """Вернуть ISO-представление без микросекунд."""

    return normalize_datetime(dt).isoformat()


def api_path(path: str) -> str:
    """Построить абсолютный REST-путь с текущей версией API."""

    if not path.startswith("/"):
        path = f"/{path}"
    return f"/api/v0.3{path}"


def create_sqlite_engine(db_name: str) -> tuple[Engine, sessionmaker]:
    """Создать SQLite-движок и фабрику сессий для тестов.
    
    Использует in-memory SQLite для максимальной производительности.
    Параметр db_name игнорируется, но сохраняется для обратной совместимости.
    
    Использует StaticPool для переиспользования одного соединения,
    что необходимо для in-memory SQLite, чтобы все сессии видели одну БД.
    """

    from sqlalchemy.pool import StaticPool

    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,  # Переиспользование одного соединения для всех сессий
        echo=False,  # Отключаем логирование SQL для ускорения
    )
    session_factory = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    return engine, session_factory


@contextmanager
def session_scope(base, engine: Engine, session_factory: sessionmaker) -> Generator[Session, None, None]:
    """Контекст управления жизненным циклом тестовой базы."""

    base.metadata.create_all(bind=engine)
    db = session_factory()
    try:
        yield db
    finally:
        db.close()
        base.metadata.drop_all(bind=engine)


@contextmanager
def test_client_with_session(
    app,
    dependency: Callable[..., Generator[Session, None, None]],
    session: Session,
) -> Generator[TestClient, None, None]:
    """Предоставить TestClient c подменой зависимости БД."""

    def override_dependency() -> Generator[Session, None, None]:
        yield session

    app.dependency_overrides[dependency] = override_dependency
    try:
        with TestClient(app) as test_client:
            yield test_client
    finally:
        app.dependency_overrides.clear()


