"""Unit tests for groups API router."""

from collections.abc import Generator
from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from backend.database import Base, get_db
from backend.main import app
from tests.utils import (
    api_path,
    create_sqlite_engine,
    session_scope,
    test_client_with_session,
)

if TYPE_CHECKING:
    from _pytest.fixtures import FixtureRequest


engine, SessionLocal = create_sqlite_engine("test_groups_router.db")


@pytest.fixture(scope="function")
def db_session() -> Generator[Session, None, None]:
    """Create test database session."""
    with session_scope(Base, engine, SessionLocal) as session:
        yield session


@pytest.fixture(scope="function")
def client(db_session: Session) -> Generator[TestClient, None, None]:
    """Create test client with test database."""
    with test_client_with_session(app, get_db, db_session) as test_client:
        yield test_client


class TestGroupsRouter:
    """Unit tests for groups API router."""

    def test_create_group(self, client: TestClient) -> None:
        """Test creating a group via API."""
        group_data = {
            "name": "Test Group",
            "description": "Test Description",
        }

        response = client.post(api_path("/groups/"), json=group_data)

        assert response.status_code == 201
        data = response.json()
        assert data["name"] == "Test Group"
        assert data["description"] == "Test Description"
        assert "id" in data

    def test_get_groups(self, client: TestClient) -> None:
        """Test getting all groups via API."""
        group1_data = {"name": "Group 1"}
        group2_data = {"name": "Group 2"}

        client.post(api_path("/groups/"), json=group1_data)
        client.post(api_path("/groups/"), json=group2_data)

        response = client.get(api_path("/groups/"))

        assert response.status_code == 200
        data = response.json()
        assert len(data) == 2

    def test_get_group(self, client: TestClient) -> None:
        """Test getting a specific group via API."""
        group_data = {"name": "Test Group"}

        create_response = client.post(api_path("/groups/"), json=group_data)
        group_id = create_response.json()["id"]

        response = client.get(api_path(f"/groups/{group_id}"))

        assert response.status_code == 200
        data = response.json()
        assert data["id"] == group_id
        assert data["name"] == "Test Group"

    def test_get_group_not_found(self, client: TestClient) -> None:
        """Test getting a non-existent group via API."""
        response = client.get(api_path("/groups/999"))

        assert response.status_code == 404

    def test_update_group(self, client: TestClient) -> None:
        """Test updating a group via API."""
        group_data = {"name": "Original Name"}

        create_response = client.post(api_path("/groups/"), json=group_data)
        group_id = create_response.json()["id"]

        update_data = {"name": "Updated Name"}
        response = client.put(api_path(f"/groups/{group_id}"), json=update_data)

        assert response.status_code == 200
        data = response.json()
        assert data["name"] == "Updated Name"

    def test_update_group_not_found(self, client: TestClient) -> None:
        """Test updating a non-existent group via API."""
        update_data = {"name": "Updated Name"}
        response = client.put(api_path("/groups/999"), json=update_data)

        assert response.status_code == 404

    def test_delete_group(self, client: TestClient) -> None:
        """Test deleting a group via API."""
        group_data = {"name": "Test Group"}

        create_response = client.post(api_path("/groups/"), json=group_data)
        group_id = create_response.json()["id"]

        response = client.delete(api_path(f"/groups/{group_id}"))

        assert response.status_code == 204

        # Verify group is deleted
        get_response = client.get(api_path(f"/groups/{group_id}"))
        assert get_response.status_code == 404

    def test_delete_group_not_found(self, client: TestClient) -> None:
        """Test deleting a non-existent group via API."""
        response = client.delete(api_path("/groups/999"))

        assert response.status_code == 404

