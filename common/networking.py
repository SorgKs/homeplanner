"""Utilities for accessing shared network configuration."""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path
import tomllib
from typing import Any, TypedDict


class NetworkConfig(TypedDict):
    """Typed representation of network configuration."""

    host: str
    port: int


_SETTINGS_PATH: Path = Path(__file__).resolve().parent / "config" / "settings.toml"
_DEFAULT_HOST = "localhost"
_DEFAULT_PORT = 8000


@lru_cache(maxsize=1)
def _load_settings() -> dict[str, Any]:
    if not _SETTINGS_PATH.exists():
        raise FileNotFoundError(f"Settings file not found: {_SETTINGS_PATH}")
    with _SETTINGS_PATH.open("rb") as settings_file:
        return tomllib.load(settings_file)


@lru_cache(maxsize=1)
def get_network_config() -> NetworkConfig:
    """Load network configuration shared across components."""

    settings = _load_settings()
    section = settings.get("network", {})
    host = str(section.get("host", settings.get("server", {}).get("host", _DEFAULT_HOST)))
    port = int(section.get("port", settings.get("server", {}).get("port", _DEFAULT_PORT)))
    return NetworkConfig(host=host, port=port)


def get_backend_host() -> str:
    """Return shared backend host."""

    return get_network_config()["host"]


def get_backend_port() -> int:
    """Return shared backend port."""

    return get_network_config()["port"]


def get_backend_base_http_url(api_prefix: str) -> str:
    """Return base HTTP URL for backend including API prefix."""

    config = get_network_config()
    return f"http://{config['host']}:{config['port']}{api_prefix}"


def get_backend_base_ws_url(api_prefix: str, ws_path: str) -> str:
    """Return base WebSocket URL for backend including API prefix."""

    config = get_network_config()
    base = f"ws://{config['host']}:{config['port']}{api_prefix}"
    if ws_path.startswith("/"):
        return f"{base}{ws_path}"
    return f"{base}/{ws_path}"


