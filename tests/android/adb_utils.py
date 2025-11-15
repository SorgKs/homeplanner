"""Вспомогательные функции для взаимодействия с adb."""

from __future__ import annotations

import shutil
import subprocess
from pathlib import Path
from typing import Final, List

from .config import LATEST_APK_PATH

ADB_EXECUTABLE: Final[str | None] = shutil.which("adb")
PACKAGE_NAME: Final[str] = "com.homeplanner"


def run_adb(args: List[str], *, timeout: int = 15) -> subprocess.CompletedProcess[str]:
    """Выполнить команду adb и вернуть результат без выбрасывания исключений."""

    if ADB_EXECUTABLE is None:
        raise RuntimeError("ADB executable is required but not available.")
    return subprocess.run(
        [ADB_EXECUTABLE, *args],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
        timeout=timeout,
    )


def list_attached_devices() -> list[str]:
    """Вернуть список сериалов подключённых устройств/эмуляторов."""

    result = run_adb(["devices"])
    if result.returncode != 0:
        raise RuntimeError(f"adb devices failed: {result.stderr.strip()}")

    devices: list[str] = []
    for line in result.stdout.splitlines():
        line = line.strip()
        if not line or line.startswith("List of devices"):
            continue
        serial, *rest = line.split()
        state = rest[0] if rest else ""
        if state == "device":
            devices.append(serial)
    return devices


def install_apk(serial: str, apk_path: Path, *, replace: bool = True) -> subprocess.CompletedProcess[str]:
    """Установить APK на устройство/эмулятор."""

    if not apk_path.exists():
        raise FileNotFoundError(f"APK not found: {apk_path}")

    args = ["-s", serial, "install"]
    if replace:
        args.append("-r")
    args.append(str(apk_path))
    result = run_adb(args, timeout=120)
    return result


