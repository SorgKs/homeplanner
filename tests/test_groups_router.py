"""Unit tests for groups API router."""

from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from backend.database import Base, get_db
from backend.main import app

if TYPE_CHECKING:
    from _pytest.fixtures import FixtureRequest
    from sqlalchemy.orm import Session


# Test database
SQLALCHEMY_DATABASE_URL = "sqlite:///./test_groups_router.db"

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


class TestGroupsRouter:
    """Unit tests for groups API router."""

    def test_create_group(self, client: TestClient) -> None:
        """Test creating a group via API."""
        group_data = {
            "name": "Test Group",
            "description": "Test Description",
        }
        
        response = client.post("/api/v1/groups/", json=group_data)
        
        assert response.status_code == 201
        data = response.json()
        assert data["name"] == "Test Group"
        assert data["description"] == "Test Description"
        assert "id" in data

    def test_get_groups(self, client: TestClient) -> None:
        """Test getting all groups via API."""
        group1_data = {"name": "Group 1"}
        group2_data = {"name": "Group 2"}
        
        client.post("/api/v1/groups/", json=group1_data)
        client.post("/api/v1/groups/", json=group2_data)
        
        response = client.get("/api/v1/groups/")
        
        assert response.status_code == 200
        data = response.json()
        assert len(data) == 2

    def test_get_group(self, client: TestClient) -> None:
        """Test getting a specific group via API."""
        group_data = {"name": "Test Group"}
        
        create_response = client.post("/api/v1/groups/", json=group_data)
        group_id = create_response.json()["id"]
        
        response = client.get(f"/api/v1/groups/{group_id}")
        
        assert response.status_code == 200
        data = response.json()
        assert data["id"] == group_id
        assert data["name"] == "Test Group"

    def test_get_group_not_found(self, client: TestClient) -> None:
        """Test getting a non-existent group via API."""
        response = client.get("/api/v1/groups/999")
        
        assert response.status_code == 404

    def test_update_group(self, client: TestClient) -> None:
        """Test updating a group via API."""
        group_data = {"name": "Original Name"}
        
        create_response = client.post("/api/v1/groups/", json=group_data)
        group_id = create_response.json()["id"]
        
        update_data = {"name": "Updated Name"}
        response = client.put(f"/api/v1/groups/{group_id}", json=update_data)
        
        assert response.status_code == 200
        data = response.json()
        assert data["name"] == "Updated Name"

    def test_update_group_not_found(self, client: TestClient) -> None:
        """Test updating a non-existent group via API."""
        update_data = {"name": "Updated Name"}
        response = client.put("/api/v1/groups/999", json=update_data)
        
        assert response.status_code == 404

    def test_delete_group(self, client: TestClient) -> None:
        """Test deleting a group via API."""
        group_data = {"name": "Test Group"}
        
        create_response = client.post("/api/v1/groups/", json=group_data)
        group_id = create_response.json()["id"]
        
        response = client.delete(f"/api/v1/groups/{group_id}")
        
        assert response.status_code == 204
        
        # Verify group is deleted
        get_response = client.get(f"/api/v1/groups/{group_id}")
        assert get_response.status_code == 404

    def test_delete_group_not_found(self, client: TestClient) -> None:
        """Test deleting a non-existent group via API."""
        response = client.delete("/api/v1/groups/999")
        
        assert response.status_code == 404

