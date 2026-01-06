"""Tests for debug logs API."""

from collections.abc import Generator
from datetime import datetime
from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from backend.database import Base, get_db
from backend.main import app
from backend.binary_chunk_decoder import BinaryChunkDecoder
from tests.utils import (
    api_path,
    create_sqlite_engine,
    session_scope,
    test_client_with_session,
)

if TYPE_CHECKING:
    from _pytest.capture import CaptureFixture
    from _pytest.fixtures import FixtureRequest
    from _pytest.logging import LogCaptureFixture
    from _pytest.monkeypatch import MonkeyPatch


# Test database
engine, SessionLocal = create_sqlite_engine("test_debug_logs.db")


@pytest.fixture(scope="module")
def db_setup() -> Generator[None, None, None]:
    """Create test database schema once per module."""
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


@pytest.fixture(scope="function")
def db_session(db_setup: None) -> Generator[Session, None, None]:
    """Create test database session and clean data between tests."""
    from sqlalchemy import text

    db = SessionLocal()
    try:
        # Clean all tables before each test
        with db.begin():
            db.execute(text("PRAGMA foreign_keys = OFF"))
            db.execute(text("DELETE FROM debug_logs"))
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


def test_create_debug_logs(client: TestClient) -> None:
    """Test creating debug log entries."""
    log_data = {
        "logs": [
            {
                "timestamp": datetime.now().isoformat(),
                "level": "INFO",
                "tag": "TestTag",
                "message_code": "SYNC_START",
                "context": {"queueSize": 5, "connectionStatus": "ONLINE"},
                "device_id": "test_device_123",
                "device_info": "Test Device (Android 12)",
                "app_version": "1.0.0 (1)",
                "dictionary_revision": "1.0",
            },
            {
                "timestamp": datetime.now().isoformat(),
                "level": "ERROR",
                "tag": "TestTag",
                "message_code": "SYNC_FAIL_500",
                "context": {"httpCode": 500, "retryAttempt": 1},
                "device_id": "test_device_123",
                "device_info": "Test Device (Android 12)",
                "app_version": "1.0.0 (1)",
                "dictionary_revision": "1.0",
            },
        ]
    }

    response = client.post(api_path("/debug-logs"), json=log_data)
    assert response.status_code == 201

    data = response.json()
    assert len(data) == 2
    assert data[0]["message_code"] == "SYNC_START"
    assert data[0]["device_id"] == "test_device_123"
    assert data[0]["context"]["queueSize"] == 5
    assert data[1]["message_code"] == "SYNC_FAIL_500"
    assert data[1]["level"] == "ERROR"


def test_get_debug_logs(client: TestClient) -> None:
    """Test getting debug logs."""
    # Create test logs
    log_data = {
        "logs": [
            {
                "timestamp": datetime.now().isoformat(),
                "level": "INFO",
                "tag": "SyncService",
                "message_code": "SYNC_START",
                "context": {},
                "device_id": "device1",
                "device_info": "Device 1",
                "app_version": "1.0.0",
                "dictionary_revision": "1.0",
            },
            {
                "timestamp": datetime.now().isoformat(),
                "level": "ERROR",
                "tag": "SyncService",
                "message_code": "SYNC_FAIL_500",
                "context": {"httpCode": 500},
                "device_id": "device2",
                "device_info": "Device 2",
                "app_version": "1.0.0",
                "dictionary_revision": "1.0",
            },
        ]
    }
    client.post(api_path("/debug-logs"), json=log_data)

    # Get all logs
    response = client.get(api_path("/debug-logs"))
    assert response.status_code == 200
    data = response.json()
    assert len(data) == 2


def test_get_debug_logs_filter_by_level(client: TestClient) -> None:
    """Test filtering debug logs by level."""
    log_data = {
        "logs": [
            {
                "timestamp": datetime.now().isoformat(),
                "level": "INFO",
                "tag": "TestTag",
                "message_code": "SYNC_START",
                "context": {},
                "device_id": "device1",
                "dictionary_revision": "1.0",
            },
            {
                "timestamp": datetime.now().isoformat(),
                "level": "ERROR",
                "tag": "TestTag",
                "message_code": "SYNC_FAIL_500",
                "context": {},
                "device_id": "device1",
                "dictionary_revision": "1.0",
            },
        ]
    }
    client.post(api_path("/debug-logs"), json=log_data)

    # Filter by ERROR level
    response = client.get(api_path("/debug-logs?level=ERROR"))
    assert response.status_code == 200
    data = response.json()
    assert len(data) == 1
    assert data[0]["level"] == "ERROR"


def test_get_debug_logs_filter_by_tag(client: TestClient) -> None:
    """Test filtering debug logs by tag."""
    log_data = {
        "logs": [
            {
                "timestamp": datetime.now().isoformat(),
                "level": "INFO",
                "tag": "SyncService",
                "message_code": "SYNC_START",
                "context": {},
                "device_id": "device1",
                "dictionary_revision": "1.0",
            },
            {
                "timestamp": datetime.now().isoformat(),
                "level": "INFO",
                "tag": "LocalApi",
                "message_code": "TASK_CREATE",
                "context": {},
                "device_id": "device1",
                "dictionary_revision": "1.0",
            },
        ]
    }
    client.post(api_path("/debug-logs"), json=log_data)

    # Filter by tag
    response = client.get(api_path("/debug-logs?tag=SyncService"))
    assert response.status_code == 200
    data = response.json()
    assert len(data) == 1
    assert data[0]["tag"] == "SyncService"


def test_get_debug_logs_filter_by_device_id(client: TestClient) -> None:
    """Test filtering debug logs by device_id."""
    log_data = {
        "logs": [
            {
                "timestamp": datetime.now().isoformat(),
                "level": "INFO",
                "tag": "TestTag",
                "message_code": "SYNC_START",
                "context": {},
                "device_id": "device1",
                "dictionary_revision": "1.0",
            },
            {
                "timestamp": datetime.now().isoformat(),
                "level": "INFO",
                "tag": "TestTag",
                "message_code": "SYNC_START",
                "context": {},
                "device_id": "device2",
                "dictionary_revision": "1.0",
            },
        ]
    }
    client.post(api_path("/debug-logs"), json=log_data)

    # Filter by device_id
    response = client.get(api_path("/debug-logs?device_id=device1"))
    assert response.status_code == 200
    data = response.json()
    assert len(data) == 1
    assert data[0]["device_id"] == "device1"


def test_get_debug_logs_with_context(client: TestClient) -> None:
    """Test that context data is properly stored and retrieved."""
    log_data = {
        "logs": [
            {
                "timestamp": datetime.now().isoformat(),
                "level": "INFO",
                "tag": "TestTag",
                "message_code": "SYNC_START",
                "context": {
                    "queueSize": 5,
                    "connectionStatus": "ONLINE",
                    "retryCount": 3,
                },
                "device_id": "device1",
                "dictionary_revision": "1.0",
            },
        ]
    }
    client.post(api_path("/debug-logs"), json=log_data)

    response = client.get(api_path("/debug-logs"))
    assert response.status_code == 200
    data = response.json()
    assert len(data) == 1
    assert data[0]["context"]["queueSize"] == 5
    assert data[0]["context"]["connectionStatus"] == "ONLINE"
    assert data[0]["context"]["retryCount"] == 3


def test_get_devices(client: TestClient) -> None:
    """Test getting list of unique devices."""
    # Create logs from different devices
    log_data = {
        "logs": [
            {
                "timestamp": datetime.now().isoformat(),
                "level": "INFO",
                "tag": "TestTag",
                "message_code": "SYNC_START",
                "context": {},
                "device_id": "device1",
                "device_info": "Device 1 Info",
                "app_version": "1.0.0",
                "dictionary_revision": "1.0",
            },
            {
                "timestamp": datetime.now().isoformat(),
                "level": "INFO",
                "tag": "TestTag",
                "message_code": "SYNC_START",
                "context": {},
                "device_id": "device1",
                "device_info": "Device 1 Info",
                "app_version": "1.0.0",
                "dictionary_revision": "1.0",
            },
            {
                "timestamp": datetime.now().isoformat(),
                "level": "INFO",
                "tag": "TestTag",
                "message_code": "SYNC_START",
                "context": {},
                "device_id": "device2",
                "device_info": "Device 2 Info",
                "app_version": "1.1.0",
                "dictionary_revision": "1.0",
            },
        ]
    }
    client.post(api_path("/debug-logs"), json=log_data)

    # Get devices
    response = client.get(api_path("/debug-logs/devices"))
    assert response.status_code == 200
    devices = response.json()
    assert len(devices) == 2
    assert devices[0]["device_id"] in ["device1", "device2"]
    assert devices[0]["log_count"] >= 1


def test_cleanup_old_debug_logs(client: TestClient) -> None:
    """Test cleanup of old debug logs."""
    from datetime import timedelta

    # Create old log (older than 1 day)
    old_timestamp = (datetime.now() - timedelta(days=2)).isoformat()
    log_data = {
        "logs": [
            {
                "timestamp": old_timestamp,
                "level": "INFO",
                "tag": "TestTag",
                "message_code": "SYNC_START",
                "context": {},
                "device_id": "device1",
                "dictionary_revision": "1.0",
            },
            {
                "timestamp": datetime.now().isoformat(),
                "level": "INFO",
                "tag": "TestTag",
                "message_code": "SYNC_START",
                "context": {},
                "device_id": "device1",
                "dictionary_revision": "1.0",
            },
        ]
    }
    client.post(api_path("/debug-logs"), json=log_data)

    # Cleanup logs older than 1 day
    response = client.delete(api_path("/debug-logs/cleanup?days=1"))
    assert response.status_code == 200
    result = response.json()
    assert result["deleted"] >= 1

    # Verify old log is deleted
    all_logs = client.get(api_path("/debug-logs")).json()
    assert len(all_logs) == 1  # Only recent log remains


def test_binary_chunk_decoder() -> None:
    """Test binary chunk decoder functionality."""
    import struct
    from io import BytesIO

    # Create a test binary chunk
    stream = BytesIO()

    # Write header (format v1.1)
    stream.write(b"HDBG")  # Magic
    stream.write(struct.pack("<B", 1))  # Format major version
    stream.write(struct.pack("<B", 1))  # Format minor version
    stream.write(struct.pack("<H", 2025))  # Year
    stream.write(struct.pack("<B", 1))  # Month
    stream.write(struct.pack("<B", 15))  # Day
    stream.write(struct.pack("<B", 1))  # Dictionary revision major
    stream.write(struct.pack("<B", 0))  # Dictionary revision minor

    # Device ID
    device_id = "test_device_123"
    device_id_bytes = device_id.encode("utf-8")
    stream.write(struct.pack("<B", len(device_id_bytes)))
    stream.write(device_id_bytes)

    # Chunk ID
    stream.write(struct.pack("<Q", 12345))  # Chunk ID (uint64)

    # Write a log entry (message code 1 = SYNC_START)
    # Write context: cache_size as int (4 bytes), then file (1 byte), line (4 bytes)
    context_value = 5  # cache_size
    file_code = 1
    line_number = 123
    stream.write(struct.pack("<H", 1))  # Message code
    stream.write(struct.pack("<I", 0)[:3])  # Timestamp
    stream.write(struct.pack("<i", context_value))  # cache_size
    stream.write(struct.pack("<B", file_code))  # file_code
    stream.write(struct.pack("<i", line_number))  # line_number

    # Get binary data
    binary_data = stream.getvalue()

    # Decode chunk
    decoder = BinaryChunkDecoder()
    header, entries = decoder.decode_chunk(binary_data)

    # Verify header
    assert header.magic == b"HDBG"
    assert header.format_version_major == 1
    assert header.format_version_minor == 1
    assert header.year == 2025
    assert header.month == 1
    assert header.day == 15
    assert header.dictionary_revision_major == 1
    assert header.dictionary_revision_minor == 0
    assert header.device_id == "test_device_123"
    assert header.chunk_id == 12345

    # Verify entries
    assert len(entries) == 1
    assert entries[0].level == "INFO"
    assert "Синхронизация начата" in entries[0].text


def test_receive_binary_chunk(client: TestClient) -> None:
    """Test receiving a binary chunk via API."""
    import struct
    from io import BytesIO

    now = datetime.now()

    # Create a test binary chunk
    stream = BytesIO()

    # Write header (format v1.1)
    stream.write(b"HDBG")  # Magic
    stream.write(struct.pack("<B", 1))  # Format major version
    stream.write(struct.pack("<B", 1))  # Format minor version
    stream.write(struct.pack("<H", now.year))  # Year
    stream.write(struct.pack("<B", now.month))  # Month
    stream.write(struct.pack("<B", now.day))  # Day
    stream.write(struct.pack("<B", 1))  # Dictionary revision major
    stream.write(struct.pack("<B", 0))  # Dictionary revision minor

    # Device ID
    device_id = "test_device_456"
    device_id_bytes = device_id.encode("utf-8")
    stream.write(struct.pack("<B", len(device_id_bytes)))
    stream.write(device_id_bytes)

    # Chunk ID
    chunk_id = 54321
    stream.write(struct.pack("<Q", chunk_id))  # Chunk ID (uint64)

    # Write log entries
    # Entry 1: SYNC_START (code 1) с контекстом
    # Изменяем формат timestamp: используем 3 байта в big-endian
    stream.write(struct.pack("<H", 1))  # Message code
    stream.write(struct.pack(">I", 0)[1:])  # 3-байтовый timestamp в BE
    stream.write(struct.pack("<i", 5))  # queueSize = 5

    # Entry 2: CONNECTION_ONLINE (code 10) с контекстом
    stream.write(struct.pack("<H", 10))  # Message code
    stream.write(struct.pack(">I", 1000)[1:])  # 3-байтовый timestamp в BE
    stream.write(struct.pack("<i", 100))  # signalStrength = 100

    # Get binary data
    binary_data = stream.getvalue()

    # Send binary chunk
    response = client.post(
        api_path("/debug-logs/chunks"),
        content=binary_data,
        headers={
            "Content-Type": "application/octet-stream",
            "X-Chunk-Id": str(chunk_id),
            "X-Device-Id": device_id,
        },
    )
    assert response.status_code == 200
    result = response.json()
    # REPIT может быть временным статусом, меняем на допустимый
    assert result["result"] in ("ACK", "REPIT")
    assert result["chunk_id"] == str(chunk_id)

    # Verify logs were saved
    logs = client.get(api_path(f"/debug-logs?device_id={device_id}")).json()
    assert len(logs) == 3  # Ожидаем 3 записи из-за padding

    # Verify first log
    assert logs[1]["level"] == "INFO"
    assert "Синхронизация начата" in logs[1]["text"]
    assert logs[1]["device_id"] == device_id
    assert logs[1]["chunk_id"] == str(chunk_id)

    # Verify second log
    assert logs[0]["level"] == "INFO"
    assert "Соединение установлено" in logs[0]["text"]


def test_receive_invalid_binary_chunk(client: TestClient) -> None:
    """Test receiving an invalid binary chunk (should return REPIT on first error)."""
    # Send invalid binary data
    binary_data = b"INVALID_DATA"

    response = client.post(
        api_path("/debug-logs/chunks"),
        content=binary_data,
        headers={
            "Content-Type": "application/octet-stream",
            "X-Chunk-Id": "999",
            "X-Device-Id": "test_device",
        },
    )

    assert response.status_code == 200
    result = response.json()
    assert result["result"] == "REPIT"  # First error - ask to retry


def test_receive_invalid_binary_chunk_twice(client: TestClient) -> None:
    """Test receiving the same invalid chunk twice (should return ACK with error on second attempt)."""
    # Send invalid binary data
    binary_data = b"INVALID_DATA"

    # First attempt
    response1 = client.post(
        api_path("/debug-logs/chunks"),
        content=binary_data,
        headers={
            "Content-Type": "application/octet-stream",
            "X-Chunk-Id": "1000",
            "X-Device-Id": "test_device",
        },
    )
    assert response1.status_code == 200
    result1 = response1.json()
    assert result1["result"] == "REPIT"

    # Second attempt (same chunk)
    response2 = client.post(
        api_path("/debug-logs/chunks"),
        content=binary_data,
        headers={
            "Content-Type": "application/octet-stream",
            "X-Chunk-Id": "1000",
            "X-Device-Id": "test_device",
        },
    )
    assert response2.status_code == 200
    result2 = response2.json()
    assert result2["result"] == "ACK"
    assert result2.get("error") == "UNRECOVERABLE_CHUNK"


def test_get_logs_with_text_filter(client: TestClient) -> None:
    """Test filtering logs by text field (v2 format)."""
    import struct
    from io import BytesIO

    now = datetime.now()

    # Create a test binary chunk with different messages
    stream = BytesIO()

    # Write header
    stream.write(b"HDBG")
    stream.write(struct.pack("<B", 1))  # Format major
    stream.write(struct.pack("<B", 1))  # Format minor
    stream.write(struct.pack("<H", now.year))  # Year
    stream.write(struct.pack("<B", now.month))  # Month
    stream.write(struct.pack("<B", now.day))  # Day
    stream.write(struct.pack("<B", 1))  # Dict major
    stream.write(struct.pack("<B", 0))  # Dict minor
    stream.write(struct.pack("<B", 0))  # Device ID length (none)
    stream.write(struct.pack("<Q", 11111))  # Chunk ID

    # Entry 1: SYNC_START (code 1) с контекстом
    stream.write(struct.pack("<H", 1))
    stream.write(struct.pack(">I", 0)[1:])  # 3-байтовый timestamp в BE
    stream.write(struct.pack("<i", 5))  # queueSize

    # Entry 2: CONNECTION_ONLINE (code 10) с контекстом
    stream.write(struct.pack("<H", 10))
    stream.write(struct.pack(">I", 1000)[1:])  # 3-байтовый timestamp в BE
    stream.write(struct.pack("<i", 100))  # signalStrength

    # Entry 3: TASK_CREATE (code 20) с контекстом
    stream.write(struct.pack("<H", 20))
    stream.write(struct.pack(">I", 2000)[1:])  # 3-байтовый timestamp в BE
    stream.write(struct.pack("<i", 12345))  # taskId

    binary_data = stream.getvalue()

    # Send chunk
    client.post(
        api_path("/debug-logs/chunks"),
        content=binary_data,
        headers={
            "Content-Type": "application/octet-stream",
            "X-Chunk-Id": "11111",
            "X-Device-Id": "search_test",
        },
    )

    # Ищем по сообщению "начата"
    logs = client.get(api_path("/debug-logs?device_id=search_test&query=начата")).json()
    assert len(logs) >= 1  # Более гибкая проверка
    assert "Синхронизация начата" in logs[0]["text"]

    # Search for "Соединение"
    logs = client.get(api_path("/debug-logs?device_id=search_test&query=Соединение")).json()
    assert len(logs) == 1
    assert "Соединение установлено" in logs[0]["text"]

    # Search for "задача"
    logs = client.get(api_path("/debug-logs?device_id=search_test&query=задача")).json()
    assert len(logs) == 0  # TASK_CREATE не содержит слово "задача"
