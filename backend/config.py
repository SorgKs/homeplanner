"""Configuration management using Pydantic settings."""

from __future__ import annotations

from datetime import datetime
from pathlib import Path
import zoneinfo

from pydantic_settings import BaseSettings, SettingsConfigDict

from common.networking import get_backend_host, get_backend_port
from common.versioning import (
    compose_component_version,
    get_project_version,
    get_component_patch,
    get_api_prefix,
    get_api_version,
)


BACKEND_ROOT: Path = Path(__file__).resolve().parent
PROJECT_ROOT: Path = BACKEND_ROOT.parent
DEFAULT_RUNTIME_DIR: Path = PROJECT_ROOT / "runtime"
DEFAULT_DB_DIR: Path = DEFAULT_RUNTIME_DIR / "db"
DEFAULT_LOG_DIR: Path = DEFAULT_RUNTIME_DIR / "logs"
DEFAULT_DB_PATH: Path = DEFAULT_DB_DIR / "homeplanner.db"
DEFAULT_DB_URL: str = f"sqlite:///{DEFAULT_DB_PATH.resolve().as_posix()}"


def get_system_timezone() -> str:
    """Get system timezone."""
    try:
        # Try to get from system
        tz = datetime.now().astimezone().tzinfo
        if isinstance(tz, zoneinfo.ZoneInfo):
            return str(tz.key)
    except Exception:
        pass
    # Fallback to Europe/Moscow
    return "Europe/Moscow"


def init_env_file() -> None:
    """Initialize .env file with system timezone if it doesn't exist."""
    import os
    env_path = ".env"
    if not os.path.exists(env_path):
        tz = get_system_timezone()
        with open(env_path, "w") as f:
            f.write(f"TIMEZONE={tz}\n")


class Settings(BaseSettings):
    """Application settings."""

    # Database
    database_url: str = DEFAULT_DB_URL
    db_directory: str = str(DEFAULT_DB_DIR)
    log_directory: str = str(DEFAULT_LOG_DIR)
    database_filename: str = DEFAULT_DB_PATH.name
    backend_patch_version: int = get_component_patch("backend")

    # Security
    secret_key: str = "change-me-in-production"
    algorithm: str = "HS256"
    access_token_expire_minutes: int = 30

    # Server
    host: str = get_backend_host()
    port: int = get_backend_port()
    debug: bool = True

    # CORS
    cors_origins: str = ",".join(
        [
            f"http://{get_backend_host()}:{get_backend_port()}",
            f"http://{get_backend_host()}:8080",
            "http://localhost:8080",
            "http://localhost:8000",
        ]
    )
    
    # Timezone
    timezone: str = get_system_timezone()

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
    )

    @property
    def cors_origins_list(self) -> list[str]:
        """Parse CORS origins from comma-separated string."""
        if isinstance(self.cors_origins, str):
            return [origin.strip() for origin in self.cors_origins.split(",")]
        return list(self.cors_origins) if isinstance(self.cors_origins, list) else []

    @property
    def project_version(self) -> str:
        """Return проектную версию (major.minor)."""

        return get_project_version()

    @property
    def backend_version(self) -> str:
        """Return backend-версию (major.minor.patch)."""

        return compose_component_version("backend", self.backend_patch_version)

    @property
    def api_version(self) -> str:
        """Return версию API в формате v<MAJOR>.<MINOR>."""

        return get_api_version()

    @property
    def api_prefix(self) -> str:
        """Return prefix REST API `/api/v<MAJOR>.<MINOR>`."""

        return get_api_prefix()

    @property
    def db_directory_path(self) -> Path:
        """Return путь к каталогу БД."""

        return Path(self.db_directory)

    @property
    def log_directory_path(self) -> Path:
        """Return путь к каталогу логов backend."""

        return Path(self.log_directory)

    @property
    def database_path(self) -> Path:
        """Return полный путь к файлу базы данных."""

        return self.db_directory_path / self.database_filename

    @property
    def backend_log_file(self) -> Path:
        """Return путь к основному файлу логов backend."""

        return self.log_directory_path / "backend.log"

    def ensure_directories(self) -> None:
        """Создать необходимые каталоги (БД, логи) при необходимости."""

        self.db_directory_path.mkdir(parents=True, exist_ok=True)
        self.log_directory_path.mkdir(parents=True, exist_ok=True)


_settings: Settings | None = None


def get_settings() -> Settings:
    """Get application settings (singleton pattern)."""
    global _settings
    if _settings is None:
        # Initialize .env file on first run
        init_env_file()
        _settings = Settings()
        _settings.ensure_directories()
    return _settings

