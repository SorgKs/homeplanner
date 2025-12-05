"""Unit tests for backend/routers/realtime.py."""

from collections.abc import Generator
from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from backend.database import Base, get_db
from backend.main import create_app
from backend.routers.realtime import ConnectionManager

if TYPE_CHECKING:
    from _pytest.capture import CaptureFixture
    from _pytest.fixtures import FixtureRequest
    from _pytest.logging import LogCaptureFixture
    from _pytest.monkeypatch import MonkeyPatch
    from pytest_mock.plugin import MockerFixture
    from sqlalchemy.orm import Session


# Test database - используем in-memory SQLite для максимальной производительности
from sqlalchemy.pool import StaticPool

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
    from sqlalchemy import text
    
    db = TestingSessionLocal()
    try:
        # Clean all tables before each test
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


class TestConnectionManager:
    """Tests for ConnectionManager class."""
    
    def test_initial_state(self) -> None:
        """Test that ConnectionManager starts with no connections."""
        manager = ConnectionManager()
        
        assert manager._connections == set()
        assert manager._meta == {}
        status = manager.status()
        assert status["active_connections"] == 0
        assert status["clients"] == []
    
    def test_connect_accepts_websocket(self, client: TestClient) -> None:
        """Test that connect accepts a WebSocket connection."""
        from common.versioning import get_api_prefix
        
        api_prefix = get_api_prefix()
        with client.websocket_connect(f"{api_prefix}/tasks/stream") as websocket:
            # Connection should be established
            assert websocket is not None
    
    def test_disconnect_removes_connection(self) -> None:
        """Test that disconnect removes a connection."""
        manager = ConnectionManager()
        
        # Create a mock WebSocket
        class MockWebSocket:
            def __init__(self) -> None:
                self.client = type("Client", (), {"host": "127.0.0.1", "port": 8000})()
        
        mock_ws = MockWebSocket()
        
        # Add connection manually for testing
        manager._connections.add(mock_ws)  # type: ignore[arg-type]
        manager._meta[mock_ws] = {"id": id(mock_ws), "host": "127.0.0.1", "port": 8000}  # type: ignore[index]
        
        assert len(manager._connections) == 1
        
        # Disconnect
        manager.disconnect(mock_ws)  # type: ignore[arg-type]
        
        assert len(manager._connections) == 0
        assert mock_ws not in manager._meta
    
    def test_broadcast_sends_to_all_connections(self) -> None:
        """Test that broadcast sends message to all connections."""
        manager = ConnectionManager()
        
        # Create mock WebSockets
        messages_received: list[dict] = []
        
        class MockWebSocket:
            def __init__(self, ws_id: int) -> None:
                self.ws_id = ws_id
                self.client = type("Client", (), {"host": "127.0.0.1", "port": 8000})()
            
            async def send_json(self, message: dict) -> None:
                messages_received.append(message)
        
        mock_ws1 = MockWebSocket(1)
        mock_ws2 = MockWebSocket(2)
        
        # Add connections manually
        manager._connections.add(mock_ws1)  # type: ignore[arg-type]
        manager._connections.add(mock_ws2)  # type: ignore[arg-type]
        manager._meta[mock_ws1] = {"id": 1, "host": "127.0.0.1", "port": 8000}  # type: ignore[index]
        manager._meta[mock_ws2] = {"id": 2, "host": "127.0.0.1", "port": 8000}  # type: ignore[index]
        
        # Broadcast message
        import asyncio
        test_message = {"type": "task_update", "action": "created", "task_id": 123}
        asyncio.run(manager.broadcast(test_message))
        
        # Both connections should receive the message
        assert len(messages_received) == 2
        assert all(msg == test_message for msg in messages_received)
    
    def test_broadcast_removes_failed_connections(self) -> None:
        """Test that broadcast removes connections that fail to send."""
        manager = ConnectionManager()
        
        class MockWebSocket:
            def __init__(self, should_fail: bool = False) -> None:
                self.should_fail = should_fail
                self.client = type("Client", (), {"host": "127.0.0.1", "port": 8000})()
            
            async def send_json(self, message: dict) -> None:
                if self.should_fail:
                    raise Exception("Send failed")
        
        mock_ws1 = MockWebSocket(should_fail=False)
        mock_ws2 = MockWebSocket(should_fail=True)
        
        # Add connections manually
        manager._connections.add(mock_ws1)  # type: ignore[arg-type]
        manager._connections.add(mock_ws2)  # type: ignore[arg-type]
        manager._meta[mock_ws1] = {"id": 1, "host": "127.0.0.1", "port": 8000}  # type: ignore[index]
        manager._meta[mock_ws2] = {"id": 2, "host": "127.0.0.1", "port": 8000}  # type: ignore[index]
        
        assert len(manager._connections) == 2
        
        # Broadcast message
        import asyncio
        test_message = {"type": "task_update", "action": "created", "task_id": 123}
        asyncio.run(manager.broadcast(test_message))
        
        # Failed connection should be removed
        assert len(manager._connections) == 1
        assert mock_ws1 in manager._connections
        assert mock_ws2 not in manager._connections
    
    def test_status_returns_connection_info(self) -> None:
        """Test that status returns current connection information."""
        manager = ConnectionManager()
        
        # Initially empty
        status = manager.status()
        assert status["active_connections"] == 0
        assert status["clients"] == []
        
        # Add a connection
        class MockWebSocket:
            def __init__(self) -> None:
                self.client = type("Client", (), {"host": "127.0.0.1", "port": 8000})()
        
        mock_ws = MockWebSocket()
        manager._connections.add(mock_ws)  # type: ignore[arg-type]
        manager._meta[mock_ws] = {"id": id(mock_ws), "host": "127.0.0.1", "port": 8000}  # type: ignore[index]
        
        status = manager.status()
        assert status["active_connections"] == 1
        assert len(status["clients"]) == 1
        assert status["clients"][0]["host"] == "127.0.0.1"


class TestWebSocketEndpoint:
    """Tests for WebSocket endpoint in realtime router."""
    
    def test_websocket_endpoint_connects(self, client: TestClient) -> None:
        """Test that WebSocket endpoint accepts connections."""
        from common.versioning import get_api_prefix
        
        api_prefix = get_api_prefix()
        with client.websocket_connect(f"{api_prefix}/tasks/stream") as websocket:
            assert websocket is not None
    
    def test_websocket_endpoint_receives_messages(self, client: TestClient) -> None:
        """Test that WebSocket endpoint can receive messages."""
        from common.versioning import get_api_prefix
        
        api_prefix = get_api_prefix()
        with client.websocket_connect(f"{api_prefix}/tasks/stream") as websocket:
            # Send a message
            websocket.send_text("test message")
            # Connection should remain open
            assert websocket is not None
    
    def test_websocket_endpoint_disconnects_gracefully(self, client: TestClient) -> None:
        """Test that WebSocket endpoint handles disconnection gracefully."""
        from common.versioning import get_api_prefix
        
        api_prefix = get_api_prefix()
        with client.websocket_connect(f"{api_prefix}/tasks/stream") as websocket:
            # Close connection
            websocket.close()
            # Should not raise exception


class TestWebSocketStatusEndpoint:
    """Tests for WebSocket status HTTP endpoint."""
    
    def test_status_endpoint_returns_info(self, client: TestClient) -> None:
        """Test that status endpoint returns connection information."""
        from common.versioning import get_api_prefix
        
        api_prefix = get_api_prefix()
        response = client.get(f"{api_prefix}/tasks/stream/status")
        
        assert response.status_code == 200
        data = response.json()
        assert "active_connections" in data
        assert "clients" in data
        assert isinstance(data["active_connections"], int)
        assert isinstance(data["clients"], list)
    
    def test_status_endpoint_shows_active_connections(self, client: TestClient) -> None:
        """Test that status endpoint shows active WebSocket connections."""
        from common.versioning import get_api_prefix
        
        api_prefix = get_api_prefix()
        
        # Initially no connections
        response = client.get(f"{api_prefix}/tasks/stream/status")
        data = response.json()
        initial_connections = data["active_connections"]
        
        # Connect a WebSocket
        with client.websocket_connect(f"{api_prefix}/tasks/stream"):
            # Check status while connected
            response = client.get(f"{api_prefix}/tasks/stream/status")
            data = response.json()
            assert data["active_connections"] == initial_connections + 1
        
        # After disconnect, connection count should decrease
        response = client.get(f"{api_prefix}/tasks/stream/status")
        data = response.json()
        assert data["active_connections"] == initial_connections

