"""Configuration management using Pydantic settings."""

import zoneinfo
from datetime import datetime

from pydantic_settings import BaseSettings, SettingsConfigDict


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
    database_url: str = "sqlite:///./homeplanner.db"

    # Security
    secret_key: str = "change-me-in-production"
    algorithm: str = "HS256"
    access_token_expire_minutes: int = 30

    # Server
    host: str = "0.0.0.0"
    port: int = 8000
    debug: bool = True

    # CORS
    cors_origins: str = (
        "http://localhost:3000,http://localhost:8080,http://localhost:8081,"
        "http://192.168.1.2:8080"
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


_settings: Settings | None = None


def get_settings() -> Settings:
    """Get application settings (singleton pattern)."""
    global _settings
    if _settings is None:
        # Initialize .env file on first run
        init_env_file()
        _settings = Settings()
    return _settings

