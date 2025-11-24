"""Utilities and constants for project-wide versioning."""

from __future__ import annotations

import json
import tomllib
from pathlib import Path
from typing import Any, Final, Mapping

_ROOT_DIR: Final[Path] = Path(__file__).resolve().parent.parent
_PYPROJECT_PATH: Final[Path] = _ROOT_DIR / "pyproject.toml"
_COMPONENT_CONFIG_PATHS: Mapping[str, Path] = {
    "backend": _ROOT_DIR / "backend" / "version.json",
    "frontend": _ROOT_DIR / "frontend" / "version.json",
    "android": _ROOT_DIR / "android" / "version.json",
}

# Cache for lazy-loaded configs
_PYPROJECT_CONFIG: Mapping[str, Any] | None = None


def _load_json(path: Path) -> Mapping[str, Any]:
    if not path.exists():
        raise FileNotFoundError(f"Version config not found: {path}")
    with path.open("r", encoding="utf-8") as fp:
        data = json.load(fp)
    return data


def _get_pyproject_config() -> Mapping[str, Any]:
    """Lazy load pyproject configuration."""
    global _PYPROJECT_CONFIG
    if _PYPROJECT_CONFIG is None:
        if not _PYPROJECT_PATH.exists():
            raise FileNotFoundError(f"pyproject.toml not found at {_PYPROJECT_PATH}")
        with _PYPROJECT_PATH.open("rb") as fp:
            _PYPROJECT_CONFIG = tomllib.load(fp)
    return _PYPROJECT_CONFIG


def _get_tool_config(section: str) -> Mapping[str, Any]:
    tool = _get_pyproject_config().get("tool", {})
    homeplanner = tool.get("homeplanner", {})
    return homeplanner.get(section, {})


def _get_version_config() -> Mapping[str, Any]:
    """Return project version configuration from pyproject."""
    return _get_tool_config("versions")


def _get_api_version_config() -> Mapping[str, Any]:
    """Return API version configuration from pyproject."""
    return _get_tool_config("api")


def _get_major_version() -> int:
    """Get major version (lazy loaded)."""
    config = _get_version_config()
    value = config.get("project_major", config.get("major", 0))
    return int(value)


def _get_minor_version() -> int:
    """Get minor version (lazy loaded)."""
    config = _get_version_config()
    value = config.get("project_minor", config.get("minor", 0))
    return int(value)


def _get_api_major_version() -> int:
    """Get API major version (lazy loaded)."""
    return _get_api_version_parts()[0]


def _get_api_minor_version() -> int:
    """Get API minor version (lazy loaded)."""
    return _get_api_version_parts()[1]


def _get_primary_api_version() -> str:
    config = _get_api_version_config()
    default_version = config.get("current_version")
    if isinstance(default_version, str):
        return default_version
    supported = config.get("supported_versions")
    if isinstance(supported, list) and supported:
        first = supported[0]
        if isinstance(first, str):
            return first
    return "0.0"


def _get_api_version_parts() -> tuple[int, int]:
    version_str = _get_primary_api_version()
    try:
        major_str, minor_str = version_str.split(".", maxsplit=1)
    except ValueError:
        return 0, 0
    try:
        return int(major_str), int(minor_str)
    except ValueError:
        return 0, 0


# Public constants (lazy-loaded functions)
def MAJOR_VERSION() -> int:  # type: ignore[misc]
    """Get major version (lazy loaded)."""
    return _get_major_version()


def MINOR_VERSION() -> int:  # type: ignore[misc]
    """Get minor version (lazy loaded)."""
    return _get_minor_version()


def API_MAJOR_VERSION() -> int:  # type: ignore[misc]
    """Get API major version (lazy loaded)."""
    return _get_api_major_version()


def API_MINOR_VERSION() -> int:  # type: ignore[misc]
    """Get API minor version (lazy loaded)."""
    return _get_api_minor_version()


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
    return f"{_get_major_version()}.{_get_minor_version()}.{patch}"


def get_project_version() -> str:
    """Return semantic version for the overall project (major.minor)."""

    return f"{_get_major_version()}.{_get_minor_version()}"


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

    return _get_version_config()


def get_api_version_tuple() -> tuple[int, int]:
    """Return API version as pair (major, minor)."""

    return _get_api_major_version(), _get_api_minor_version()


def get_api_version() -> str:
    """Return API semantic version string `v<major>.<minor>`."""

    return f"v{_get_api_major_version()}.{_get_api_minor_version()}"


def get_api_prefix() -> str:
    """Return API prefix path `/api/v<major>.<minor>`."""

    return f"/api/{get_api_version()}"


def get_supported_api_versions() -> list[str]:
    """Get list of supported API versions from pyproject.toml.
    
    Returns:
        List of supported API version strings (e.g., ["0.2", "0.3"]).
    """
    api_config = _get_api_version_config()
    supported = api_config.get("supported_versions", [])
    if isinstance(supported, list):
        return [str(item) for item in supported]
    return []


def validate_api_version(api_version: str) -> None:
    """Validate that API version is in the list of supported versions.
    
    Args:
        api_version: API version string to validate (e.g., "0.2").
        
    Raises:
        ValueError: If API version is not in the list of supported versions.
    """
    supported_versions = get_supported_api_versions()
    if not supported_versions:
        # If no supported versions configured, skip validation
        return
    
    if api_version not in supported_versions:
        supported_str = ", ".join(supported_versions)
        raise ValueError(
            f"API version '{api_version}' is not supported. "
            f"Supported versions: {supported_str}"
        )


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
    "get_supported_api_versions",
    "validate_api_version",
    "__version__",
]

