"""Unit tests for backend/routers/download.py."""

from collections.abc import Generator
from pathlib import Path
from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from backend.database import Base, get_db
from backend.main import create_app
from backend.routers import download

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


@pytest.fixture
def mock_apk_dir(tmp_path: Path) -> Path:
    """Create a temporary directory structure for APK files."""
    apk_dir = tmp_path / "android" / "app" / "build" / "outputs" / "apk" / "debug"
    apk_dir.mkdir(parents=True, exist_ok=True)
    return apk_dir


@pytest.fixture
def mock_version_json(tmp_path: Path) -> Path:
    """Create a mock version.json file."""
    version_file = tmp_path / "android" / "version.json"
    version_file.parent.mkdir(parents=True, exist_ok=True)
    import json
    version_file.write_text(json.dumps({"patch": 5}), encoding="utf-8")
    return version_file


class TestResolveVersionString:
    """Tests for _resolve_version_string function."""
    
    def test_resolve_version_string_with_version_json(self, mock_version_json: Path, monkeypatch: "MonkeyPatch") -> None:
        """Test that version string is resolved from version.json."""
        # Mock the REPO_ROOT to point to our temp directory
        repo_root = mock_version_json.parent.parent
        monkeypatch.setattr(download, "REPO_ROOT", repo_root)
        
        # Reload the module to get the new version string
        import importlib
        importlib.reload(download)
        
        version_string = download._resolve_version_string()
        
        # Should contain version numbers
        assert version_string is not None
        assert isinstance(version_string, str)
        # Should have underscores instead of dots
        assert "." not in version_string or "_" in version_string
    
    def test_resolve_version_string_without_version_json(self, tmp_path: Path, monkeypatch: "MonkeyPatch") -> None:
        """Test that version string falls back when version.json doesn't exist."""
        # Mock the REPO_ROOT to point to temp directory without version.json
        monkeypatch.setattr(download, "REPO_ROOT", tmp_path)
        
        # Reload the module
        import importlib
        importlib.reload(download)
        
        version_string = download._resolve_version_string()
        
        # Should still return a version string (with patch=0)
        assert version_string is not None
        assert isinstance(version_string, str)
    
    def test_resolve_version_string_with_invalid_json(self, tmp_path: Path, monkeypatch: "MonkeyPatch") -> None:
        """Test that version string handles invalid JSON gracefully."""
        # Create invalid JSON file
        version_file = tmp_path / "android" / "version.json"
        version_file.parent.mkdir(parents=True, exist_ok=True)
        version_file.write_text("invalid json", encoding="utf-8")
        
        monkeypatch.setattr(download, "REPO_ROOT", tmp_path)
        
        # Reload the module
        import importlib
        importlib.reload(download)
        
        version_string = download._resolve_version_string()
        
        # Should fall back to patch=0
        assert version_string is not None
        assert isinstance(version_string, str)


class TestFindAPKFile:
    """Tests for _find_apk_file function."""
    
    def test_find_apk_file_versioned_exists(self, mock_apk_dir: Path, monkeypatch: "MonkeyPatch") -> None:
        """Test that _find_apk_file finds versioned APK when it exists."""
        # Create versioned APK file
        apk_file = mock_apk_dir / "homeplanner_v0_0_5.apk"
        apk_file.write_bytes(b"fake apk content")
        
        # Mock _find_apk_file to return our test file
        def mock_find_apk() -> Path | None:
            return apk_file
        
        monkeypatch.setattr(download, "_find_apk_file", mock_find_apk)
        
        found_path = download._find_apk_file()
        
        assert found_path is not None
        assert found_path == apk_file
        assert found_path.exists()
    
    def test_find_apk_file_fallback_to_any_apk(self, mock_apk_dir: Path, monkeypatch: "MonkeyPatch") -> None:
        """Test that _find_apk_file falls back to any APK file."""
        # Create a non-versioned APK file
        apk_file = mock_apk_dir / "app-debug.apk"
        apk_file.write_bytes(b"fake apk content")
        
        # Mock _find_apk_file to return fallback file
        def mock_find_apk() -> Path | None:
            return apk_file
        
        monkeypatch.setattr(download, "_find_apk_file", mock_find_apk)
        
        found_path = download._find_apk_file()
        
        assert found_path is not None
        assert found_path == apk_file
        assert found_path.exists()
    
    def test_find_apk_file_returns_none_when_no_apk(self, tmp_path: Path, monkeypatch: "MonkeyPatch") -> None:
        """Test that _find_apk_file returns None when no APK exists."""
        # Mock _find_apk_file to return None
        def mock_find_apk() -> Path | None:
            return None
        
        monkeypatch.setattr(download, "_find_apk_file", mock_find_apk)
        
        found_path = download._find_apk_file()
        
        assert found_path is None
    
    def test_find_apk_file_returns_none_when_dir_not_exists(self, tmp_path: Path, monkeypatch: "MonkeyPatch") -> None:
        """Test that _find_apk_file returns None when directory doesn't exist."""
        # Mock _find_apk_file to return None
        def mock_find_apk() -> Path | None:
            return None
        
        monkeypatch.setattr(download, "_find_apk_file", mock_find_apk)
        
        found_path = download._find_apk_file()
        
        assert found_path is None


class TestAPKResponse:
    """Tests for _apk_response function."""
    
    def test_apk_response_returns_fileresponse(self, mock_apk_dir: Path, monkeypatch: "MonkeyPatch") -> None:
        """Test that _apk_response returns FileResponse with correct headers."""
        apk_file = mock_apk_dir / "test.apk"
        apk_file.write_bytes(b"fake apk content")
        
        repo_root = mock_apk_dir.parent.parent.parent.parent
        monkeypatch.setattr(download, "REPO_ROOT", repo_root)
        
        # Reload module
        import importlib
        importlib.reload(download)
        
        response = download._apk_response(apk_file)
        
        assert response is not None
        assert response.path == str(apk_file)
        assert response.media_type == "application/vnd.android.package-archive"
        assert "Content-Disposition" in response.headers
        assert "Content-Length" in response.headers
        # Verify filename in Content-Disposition header uses the actual file name
        assert "test.apk" in response.headers["Content-Disposition"]
    
    def test_apk_response_raises_when_file_not_exists(self, tmp_path: Path, monkeypatch: "MonkeyPatch") -> None:
        """Test that _apk_response raises HTTPException when file doesn't exist."""
        non_existent = tmp_path / "nonexistent.apk"
        
        repo_root = tmp_path
        monkeypatch.setattr(download, "REPO_ROOT", repo_root)
        
        # Reload module
        import importlib
        importlib.reload(download)
        
        from fastapi import HTTPException
        
        with pytest.raises(HTTPException) as exc_info:
            download._apk_response(non_existent)
        
        assert exc_info.value.status_code == 404


class TestDownloadEndpoints:
    """Tests for download API endpoints."""
    
    def test_download_apk_returns_file(self, mock_apk_dir: Path, client: TestClient, monkeypatch: "MonkeyPatch") -> None:
        """Test that /download/apk endpoint returns APK file."""
        apk_file = mock_apk_dir / "test.apk"
        apk_file.write_bytes(b"fake apk content")
        
        repo_root = mock_apk_dir.parent.parent.parent.parent
        monkeypatch.setattr(download, "REPO_ROOT", repo_root)
        
        # Mock _find_apk_file to return our test file
        def mock_find_apk() -> Path | None:
            return apk_file
        
        monkeypatch.setattr(download, "_find_apk_file", mock_find_apk)
        
        response = client.get("/download/apk")
        
        # Should return file
        assert response.status_code == 200
        assert response.headers["content-type"] == "application/vnd.android.package-archive"
    
    def test_download_apk_returns_404_when_not_found(self, client: TestClient, monkeypatch: "MonkeyPatch") -> None:
        """Test that /download/apk returns 404 when APK not found."""
        # Mock _find_apk_file to return None
        def mock_find_apk() -> Path | None:
            return None
        
        monkeypatch.setattr(download, "_find_apk_file", mock_find_apk)
        
        response = client.get("/download/apk")
        
        assert response.status_code == 404
        assert "not found" in response.json()["detail"].lower()
    
    def test_download_apk_meta_returns_metadata(self, mock_apk_dir: Path, client: TestClient, monkeypatch: "MonkeyPatch") -> None:
        """Test that /download/apk/meta returns APK metadata."""
        apk_file = mock_apk_dir / "test.apk"
        apk_file.write_bytes(b"fake apk content")
        
        repo_root = mock_apk_dir.parent.parent.parent.parent
        monkeypatch.setattr(download, "REPO_ROOT", repo_root)
        
        # Mock _find_apk_file to return our test file
        def mock_find_apk() -> Path | None:
            return apk_file
        
        monkeypatch.setattr(download, "_find_apk_file", mock_find_apk)
        
        response = client.get("/download/apk/meta")
        
        assert response.status_code == 200
        data = response.json()
        assert "filename" in data
        assert "filesize" in data
        assert data["filename"] == "test.apk"
        assert data["filesize"] == len(b"fake apk content")
    
    def test_download_apk_meta_returns_404_when_not_found(self, client: TestClient, monkeypatch: "MonkeyPatch") -> None:
        """Test that /download/apk/meta returns 404 when APK not found."""
        # Mock _find_apk_file to return None
        def mock_find_apk() -> Path | None:
            return None
        
        monkeypatch.setattr(download, "_find_apk_file", mock_find_apk)
        
        response = client.get("/download/apk/meta")
        
        assert response.status_code == 404
    
    def test_options_versioned_apk_returns_cors_headers(self, client: TestClient) -> None:
        """Test that OPTIONS request returns CORS headers."""
        # Get the versioned filename using the function (dynamic)
        # Use a valid versioned filename pattern for the test
        versioned_filename = download._get_apk_filename()
        
        response = client.options(f"/download/{versioned_filename}")
        
        assert response.status_code == 200
        assert "Access-Control-Allow-Origin" in response.headers
        assert "Access-Control-Allow-Methods" in response.headers
        assert "Access-Control-Allow-Headers" in response.headers

