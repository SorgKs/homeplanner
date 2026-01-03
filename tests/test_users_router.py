"""Unit tests for users API router."""

from collections.abc import Generator
from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from backend.database import Base, get_db
from backend.main import app
from tests.utils import (
    create_sqlite_engine,
    session_scope,
    test_client_with_session,
)

if TYPE_CHECKING:
    from _pytest.fixtures import FixtureRequest


engine, SessionLocal = create_sqlite_engine("test_users_router.db")


@pytest.fixture(scope="module")
def db_setup() -> Generator[None, None, None]:
    """Create test database schema once per module."""
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


@pytest.fixture(scope="function")
def db_session(db_setup: None) -> Generator[Session, None, None]:
    """Create test database session and clean data between tests using optimized DELETE."""
    from sqlalchemy import text

    db = SessionLocal()
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


@pytest.fixture(scope="function")
def client(db_session: Session) -> Generator[TestClient, None, None]:
    """Create test client with test database."""
    with test_client_with_session(app, get_db, db_session) as test_client:
        yield test_client


class TestUsersRouter:
    """Unit tests for users API router."""

    @pytest.mark.parametrize("api_version", ["/api/v0.2", "/api/v0.3"])
    def test_get_users_full(self, client: TestClient, db_session: Session, api_version: str) -> None:
        """Test getting all users (full mode) via API."""
        # Create test users using service
        from backend.services.user_service import UserService
        from backend.schemas.user import UserCreate

        user1 = UserCreate(name="User 1", email="user1@example.com")
        user2 = UserCreate(name="User 2", email="user2@example.com")
        user3 = UserCreate(name="Inactive User", email="inactive@example.com", is_active=False)

        UserService.create_user(db_session, user1)
        UserService.create_user(db_session, user2)
        UserService.create_user(db_session, user3)

        response = client.get(f"{api_version}/users/")

        assert response.status_code == 200
        data = response.json()
        assert len(data) == 3
        # Check that all fields are present
        for user in data:
            assert "id" in user
            assert "name" in user
            assert "email" in user
            assert "role" in user
            assert "is_active" in user
            assert "created_at" in user
            assert "updated_at" in user

    @pytest.mark.parametrize("api_version", ["/api/v0.2", "/api/v0.3"])
    def test_get_users_simple(self, client: TestClient, db_session: Session, api_version: str) -> None:
        """Test getting active users (simple mode) via API."""
        # Create test users using service
        from backend.services.user_service import UserService
        from backend.schemas.user import UserCreate

        user1 = UserCreate(name="Active User 1", email="active1@example.com")
        user2 = UserCreate(name="Active User 2", email="active2@example.com")
        user3 = UserCreate(name="Inactive User", email="inactive@example.com", is_active=False)

        UserService.create_user(db_session, user1)
        UserService.create_user(db_session, user2)
        UserService.create_user(db_session, user3)

        response = client.get(f"{api_version}/users/?simple=true")

        assert response.status_code == 200
        data = response.json()
        assert len(data) == 2  # Only active users
        # Check that only id and name are present
        for user in data:
            assert "id" in user
            assert "name" in user
            assert "email" not in user
            assert "role" not in user
            assert "is_active" not in user
            assert "created_at" not in user
            assert "updated_at" not in user
        # Verify names
        names = [user["name"] for user in data]
        assert "Active User 1" in names
        assert "Active User 2" in names
        assert "Inactive User" not in names

    @pytest.mark.parametrize("api_version", ["/api/v0.2", "/api/v0.3"])
    def test_get_users_simple_false(self, client: TestClient, db_session: Session, api_version: str) -> None:
        """Test getting all users with simple=false via API."""
        # Create test users using service
        from backend.services.user_service import UserService
        from backend.schemas.user import UserCreate

        user1 = UserCreate(name="User 1", email="user1@example.com")
        user2 = UserCreate(name="User 2", email="user2@example.com")

        UserService.create_user(db_session, user1)
        UserService.create_user(db_session, user2)

        response = client.get(f"{api_version}/users/?simple=false")

        assert response.status_code == 200
        data = response.json()
        assert len(data) == 2
        # Check that all fields are present (full mode)
        for user in data:
            assert "id" in user
            assert "name" in user
            assert "email" in user
            assert "role" in user
            assert "is_active" in user
            assert "created_at" in user
            assert "updated_at" in user