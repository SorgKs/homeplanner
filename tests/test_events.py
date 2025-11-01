"""Tests for events API."""

from datetime import datetime, timedelta
from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from backend.database import Base, get_db
from backend.main import app

if TYPE_CHECKING:
    from _pytest.capture import CaptureFixture
    from _pytest.fixtures import FixtureRequest
    from _pytest.logging import LogCaptureFixture
    from _pytest.monkeypatch import MonkeyPatch
    from pytest_mock.plugin import MockerFixture


# Test database
SQLALCHEMY_DATABASE_URL = "sqlite:///./test.db"

engine = create_engine(
    SQLALCHEMY_DATABASE_URL, connect_args={"check_same_thread": False}
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


@pytest.fixture(scope="function")
def db_session():
    """Create test database session."""
    Base.metadata.create_all(bind=engine)
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()
        Base.metadata.drop_all(bind=engine)


@pytest.fixture(scope="function")
def client(db_session):
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


def test_create_event(client):
    """Test creating a new event."""
    event_data = {
        "title": "Test Event",
        "description": "Test Description",
        "event_date": (datetime.utcnow() + timedelta(days=1)).isoformat(),
        "reminder_time": (datetime.utcnow() + timedelta(hours=12)).isoformat(),
    }
    response = client.post("/api/v1/events/", json=event_data)
    assert response.status_code == 201
    data = response.json()
    assert data["title"] == event_data["title"]
    assert data["description"] == event_data["description"]
    assert "id" in data


def test_get_events(client):
    """Test getting all events."""
    # Create a test event
    event_data = {
        "title": "Test Event",
        "event_date": (datetime.utcnow() + timedelta(days=1)).isoformat(),
    }
    client.post("/api/v1/events/", json=event_data)

    # Get all events
    response = client.get("/api/v1/events/")
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) >= 1


def test_get_event_by_id(client):
    """Test getting an event by ID."""
    # Create a test event
    event_data = {
        "title": "Test Event",
        "event_date": (datetime.utcnow() + timedelta(days=1)).isoformat(),
    }
    create_response = client.post("/api/v1/events/", json=event_data)
    event_id = create_response.json()["id"]

    # Get event by ID
    response = client.get(f"/api/v1/events/{event_id}")
    assert response.status_code == 200
    data = response.json()
    assert data["id"] == event_id
    assert data["title"] == event_data["title"]


def test_update_event(client):
    """Test updating an event."""
    # Create a test event
    event_data = {
        "title": "Test Event",
        "event_date": (datetime.utcnow() + timedelta(days=1)).isoformat(),
    }
    create_response = client.post("/api/v1/events/", json=event_data)
    event_id = create_response.json()["id"]

    # Update event
    update_data = {"title": "Updated Event"}
    response = client.put(f"/api/v1/events/{event_id}", json=update_data)
    assert response.status_code == 200
    data = response.json()
    assert data["title"] == "Updated Event"


def test_delete_event(client):
    """Test deleting an event."""
    # Create a test event
    event_data = {
        "title": "Test Event",
        "event_date": (datetime.utcnow() + timedelta(days=1)).isoformat(),
    }
    create_response = client.post("/api/v1/events/", json=event_data)
    event_id = create_response.json()["id"]

    # Delete event
    response = client.delete(f"/api/v1/events/{event_id}")
    assert response.status_code == 204

    # Verify event is deleted
    get_response = client.get(f"/api/v1/events/{event_id}")
    assert get_response.status_code == 404


def test_complete_event(client):
    """Test completing an event."""
    # Create a test event
    event_data = {
        "title": "Test Event",
        "event_date": (datetime.utcnow() + timedelta(days=1)).isoformat(),
    }
    create_response = client.post("/api/v1/events/", json=event_data)
    event_id = create_response.json()["id"]

    # Complete event
    response = client.post(f"/api/v1/events/{event_id}/complete")
    assert response.status_code == 200
    data = response.json()
    assert data["is_completed"] is True

