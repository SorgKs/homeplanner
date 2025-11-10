"""Конфигурация для Android-тестов."""

from __future__ import annotations

import os
from pathlib import Path


PROJECT_ROOT: Path = Path(__file__).resolve().parents[2]
DEFAULT_LATEST_APK_PATH: Path = PROJECT_ROOT / "latest.apk"

LATEST_APK_PATH: Path = Path(
    os.getenv("HP_ANDROID_LATEST_APK_PATH", str(DEFAULT_LATEST_APK_PATH))
).expanduser().resolve()


