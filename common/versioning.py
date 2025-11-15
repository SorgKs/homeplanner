"""Utilities and constants for project-wide versioning."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Final, Mapping

_ROOT_DIR: Final[Path] = Path(__file__).resolve().parent.parent
_CONFIG_PATH: Final[Path] = _ROOT_DIR / "config" / "version.json"
_API_VERSION_CONFIG_PATH: Final[Path] = _ROOT_DIR / "config" / "api_version.json"
_COMPONENT_CONFIG_PATHS: Mapping[str, Path] = {
    "backend": _ROOT_DIR / "backend" / "version.json",
    "frontend": _ROOT_DIR / "frontend" / "version.json",
    "android": _ROOT_DIR / "android" / "version.json",
}


def _load_json(path: Path) -> Mapping[str, Any]:
    if not path.exists():
        raise FileNotFoundError(f"Version config not found: {path}")
    with path.open("r", encoding="utf-8") as fp:
        data = json.load(fp)
    return data


_VERSION_CONFIG = _load_json(_CONFIG_PATH)

MAJOR_VERSION: Final[int] = int(_VERSION_CONFIG.get("major", 0))
MINOR_VERSION: Final[int] = int(_VERSION_CONFIG.get("minor", 0))

try:
    _API_VERSION_CONFIG = _load_json(_API_VERSION_CONFIG_PATH)
except FileNotFoundError:
    _API_VERSION_CONFIG = {"major": MAJOR_VERSION, "minor": MINOR_VERSION}

API_MAJOR_VERSION: Final[int] = int(_API_VERSION_CONFIG.get("major", 0))
API_MINOR_VERSION: Final[int] = int(_API_VERSION_CONFIG.get("minor", 0))


def compose_component_version(component: str, patch: int | None = None) -> str:
    """Compose semantic version string for a component.

    Args:
        component: Имя подсистемы (`backend`, `frontend`, `android`).
        patch: Явное значение патча. Если None, читается из версии компонента.

    Returns:
        Строка в формате `<MAJOR>.<MINOR>.<PATCH>`.
    """

    if patch is None:
        patch = get_component_patch(component)
    if patch < 0:
        raise ValueError("Patch version must be non-negative")
    return f"{MAJOR_VERSION}.{MINOR_VERSION}.{patch}"


def get_project_version() -> str:
    """Return semantic version for the overall project (major.minor)."""

    return f"{MAJOR_VERSION}.{MINOR_VERSION}"


def get_component_patch(component: str) -> int:
    """Return patch version for a specific component."""

    path = _COMPONENT_CONFIG_PATHS.get(component)
    if path is None or not path.exists():
        return 0
    try:
        data = _load_json(path)
    except FileNotFoundError:
        return 0
    return int(data.get("patch", 0))


def get_version_config() -> Mapping[str, Any]:
    """Return raw version configuration as mapping."""

    return _VERSION_CONFIG


def get_api_version_tuple() -> tuple[int, int]:
    """Return API version as pair (major, minor)."""

    return API_MAJOR_VERSION, API_MINOR_VERSION


def get_api_version() -> str:
    """Return API semantic version string `v<major>.<minor>`."""

    return f"v{API_MAJOR_VERSION}.{API_MINOR_VERSION}"


def get_api_prefix() -> str:
    """Return API prefix path `/api/v<major>.<minor>`."""

    return f"/api/{get_api_version()}"


__all__ = [
    "MAJOR_VERSION",
    "MINOR_VERSION",
    "compose_component_version",
    "get_project_version",
    "get_component_patch",
    "get_version_config",
    "API_MAJOR_VERSION",
    "API_MINOR_VERSION",
    "get_api_version_tuple",
    "get_api_version",
    "get_api_prefix",
]

