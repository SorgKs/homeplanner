"""Tests for events API."""

from collections.abc import Generator
from datetime import datetime, timedelta
from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from backend.database import Base, get_db
from backend.main import app
from tests.utils import (
    api_path,
    create_sqlite_engine,
    isoformat,
    session_scope,
    test_client_with_session,
)

if TYPE_CHECKING:
    from _pytest.capture import CaptureFixture
    from _pytest.fixtures import FixtureRequest
    from _pytest.logging import LogCaptureFixture
    from _pytest.monkeypatch import MonkeyPatch


# Test database
engine, SessionLocal = create_sqlite_engine("test_events.db")


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


def test_create_event(client):
    """Test creating a new event."""
    event_data = {
        "title": "Test Event",
        "description": "Test Description",
        "event_date": isoformat(datetime.now() + timedelta(days=1)),
        "reminder_time": isoformat(datetime.now() + timedelta(hours=12)),
    }
    response = client.post(api_path("/events/"), json=event_data)
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
        "event_date": isoformat(datetime.now() + timedelta(days=1)),
    }
    client.post(api_path("/events/"), json=event_data)

    # Get all events
    response = client.get(api_path("/events/"))
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) >= 1


def test_get_event_by_id(client):
    """Test getting an event by ID."""
    # Create a test event
    event_data = {
        "title": "Test Event",
        "event_date": isoformat(datetime.now() + timedelta(days=1)),
    }
    create_response = client.post(api_path("/events/"), json=event_data)
    event_id = create_response.json()["id"]

    # Get event by ID
    response = client.get(api_path(f"/events/{event_id}"))
    assert response.status_code == 200
    data = response.json()
    assert data["id"] == event_id
    assert data["title"] == event_data["title"]


def test_update_event(client):
    """Test updating an event."""
    # Create a test event
    event_data = {
        "title": "Test Event",
        "event_date": isoformat(datetime.now() + timedelta(days=1)),
    }
    create_response = client.post(api_path("/events/"), json=event_data)
    event_id = create_response.json()["id"]

    # Update event
    update_data = {"title": "Updated Event"}
    response = client.put(api_path(f"/events/{event_id}"), json=update_data)
    assert response.status_code == 200
    data = response.json()
    assert data["title"] == "Updated Event"


def test_delete_event(client):
    """Test deleting an event."""
    # Create a test event
    event_data = {
        "title": "Test Event",
        "event_date": isoformat(datetime.now() + timedelta(days=1)),
    }
    create_response = client.post(api_path("/events/"), json=event_data)
    event_id = create_response.json()["id"]

    # Delete event
    response = client.delete(api_path(f"/events/{event_id}"))
    assert response.status_code == 204

    # Verify event is deleted
    get_response = client.get(api_path(f"/events/{event_id}"))
    assert get_response.status_code == 404


def test_complete_event(client):
    """Test completing an event."""
    # Create a test event
    event_data = {
        "title": "Test Event",
        "event_date": isoformat(datetime.now() + timedelta(days=1)),
    }
    create_response = client.post(api_path("/events/"), json=event_data)
    event_id = create_response.json()["id"]

    # Complete event
    response = client.post(api_path(f"/events/{event_id}/complete"))
    assert response.status_code == 200
    data = response.json()
    assert data["is_completed"] is True

