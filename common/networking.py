"""Utilities for accessing shared network configuration."""

from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path
from typing import TypedDict


class NetworkConfig(TypedDict):
    """Typed representation of network configuration."""

    host: str
    port: int


_NETWORK_CONFIG_PATH: Path = Path(__file__).resolve().parent.parent / "config" / "network.json"
_DEFAULT_HOST = "localhost"
_DEFAULT_PORT = 8000


@lru_cache(maxsize=1)
def get_network_config() -> NetworkConfig:
    """Load network configuration shared across components."""

    if not _NETWORK_CONFIG_PATH.exists():
        return NetworkConfig(host=_DEFAULT_HOST, port=_DEFAULT_PORT)
    with _NETWORK_CONFIG_PATH.open("r", encoding="utf-8") as config_file:
        data = json.load(config_file)
    host = str(data.get("host", _DEFAULT_HOST))
    port = int(data.get("port", _DEFAULT_PORT))
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


