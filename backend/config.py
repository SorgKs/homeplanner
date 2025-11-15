"""Configuration management that reads exclusively from `config/settings.toml`."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import tomllib
from typing import Any


CONFIG_PATH = Path(__file__).resolve().parent.parent / "config" / "settings.toml"


class SettingsError(RuntimeError):
    """Raised when configuration cannot be loaded."""


def _load_config_file(path: Path) -> dict[str, Any]:
    """Load TOML configuration from disk."""
    if not path.exists():
        raise SettingsError(
            f"Configuration file '{path}' is missing. "
            "Copy and adjust 'config/settings.toml' before launching the backend."
        )
    with path.open("rb") as fp:
        return tomllib.load(fp)


def _require_section(raw: dict[str, Any], section: str) -> dict[str, Any]:
    if section not in raw or not isinstance(raw[section], dict):
        raise SettingsError(
            f"Section '[{section}]' is missing in '{CONFIG_PATH}'. "
            "All settings must be defined in the config file."
        )
    return raw[section]


def _require_value(section: dict[str, Any], key: str, *, section_name: str) -> Any:
    if key not in section:
        raise SettingsError(
            f"Missing key '{section_name}.{key}' in '{CONFIG_PATH}'. "
            "Configuration values cannot be overridden via environment variables "
            "or CLI flags."
        )
    return section[key]


def _extract_settings(raw: dict[str, Any]) -> dict[str, Any]:
    """Map nested TOML structure into flat settings attributes."""
    database = _require_section(raw, "database")
    security = _require_section(raw, "security")
    server = _require_section(raw, "server")
    cors = _require_section(raw, "cors")
    app = _require_section(raw, "app")

    return {
        "database_url": _require_value(database, "url", section_name="database"),
        "secret_key": _require_value(security, "secret_key", section_name="security"),
        "algorithm": _require_value(security, "algorithm", section_name="security"),
        "access_token_expire_minutes": _require_value(
            security,
            "access_token_expire_minutes",
            section_name="security",
        ),
        "host": _require_value(server, "host", section_name="server"),
        "port": _require_value(server, "port", section_name="server"),
        "debug": _require_value(server, "debug", section_name="server"),
        "cors_origins": _require_value(cors, "origins", section_name="cors"),
        "timezone": _require_value(app, "timezone", section_name="app"),
    }


@dataclass(slots=True)
class Settings:
    """Application settings loaded from a config file."""

    database_url: str
    secret_key: str
    algorithm: str
    access_token_expire_minutes: int
    host: str
    port: int
    debug: bool
    cors_origins: list[str]
    timezone: str

    @property
    def cors_origins_list(self) -> list[str]:
        """Return the list of configured CORS origins."""
        return self.cors_origins


_settings: Settings | None = None


def get_settings() -> Settings:
    """Get application settings (singleton pattern)."""
    global _settings
    if _settings is None:
        raw = _load_config_file(CONFIG_PATH)
        extracted = _extract_settings(raw)
        _settings = Settings(**extracted)
    return _settings

