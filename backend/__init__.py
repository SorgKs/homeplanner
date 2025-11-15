"""Backend package for HomePlanner application."""

import tomllib
from pathlib import Path

# Calculate version from pyproject.toml
_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_PYPROJECT_PATH = _PROJECT_ROOT / "pyproject.toml"


def _get_version() -> str:
    """Get backend version from pyproject.toml."""
    if not _PYPROJECT_PATH.exists():
        return "0.0.0"
    
    with _PYPROJECT_PATH.open("rb") as fp:
        config = tomllib.load(fp)
    
    # Get project version (major.minor)
    versions = config.get("tool", {}).get("homeplanner", {}).get("versions", {})
    major = versions.get("project_major", "0")
    minor = versions.get("project_minor", "0")
    
    # Get backend suffix (e.g., ".0" -> patch version)
    suffixes = config.get("tool", {}).get("homeplanner", {}).get("package_suffixes", {})
    backend_suffix = suffixes.get("backend", ".0")
    
    # Combine: major.minor + suffix (e.g., "0.2" + ".0" = "0.2.0")
    return f"{major}.{minor}{backend_suffix}"


__version__ = _get_version()

