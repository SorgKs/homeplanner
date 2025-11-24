"""Общие вспомогательные функции для модульных тестов."""

from __future__ import annotations

from collections.abc import Generator
from contextlib import contextmanager
from datetime import datetime
from typing import Callable

from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session, sessionmaker

from .api import api_path
from .datetime_helpers import normalize_datetime, isoformat_no_microseconds

__all__ = [
    "api_path",
    "normalize_datetime",
    "isoformat_no_microseconds",
    "isoformat",
    "create_sqlite_engine",
    "session_scope",
    "test_client_with_session",
]


def isoformat(dt: datetime) -> str:
    """Вернуть ISO-представление без микросекунд."""

    return isoformat_no_microseconds(dt)


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


test_client_with_session.__test__ = False  # type: ignore[attr-defined]

