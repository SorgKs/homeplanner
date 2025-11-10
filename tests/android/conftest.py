"""Общие pytest-фикстуры для тестов Android-клиента."""

from __future__ import annotations

import time
from pathlib import Path

import pytest

from .adb_utils import (
    ADB_EXECUTABLE,
    PACKAGE_NAME,
    install_apk,
    list_attached_devices,
    run_adb,
)
from .config import LATEST_APK_PATH


@pytest.fixture(scope="module")
def adb_path() -> str:
    """Убедиться, что adb доступен; при отсутствии пропустить тесты."""

    if ADB_EXECUTABLE is None:
        pytest.skip("adb не найден в PATH — Android-тесты будут пропущены.")
    return ADB_EXECUTABLE


@pytest.fixture(scope="module")
def emulator_serial(adb_path: str) -> str:
    """Вернуть serial доступного эмулятора, иначе пропустить тест."""

    devices = list_attached_devices()
    if not devices:
        pytest.skip("Не обнаружено доступных Android-устройств или эмуляторов.")
    return devices[0]


@pytest.fixture(scope="module")
def latest_apk_path() -> Path:
    """Путь до опубликованного APK."""

    if not LATEST_APK_PATH.exists():
        pytest.skip(f"Файл {LATEST_APK_PATH} не найден — пропуск Android тестов.")
    return LATEST_APK_PATH


@pytest.fixture(scope="module")
def install_latest_apk(emulator_serial: str, latest_apk_path: Path) -> None:
    """Установить опубликованный APK на эмулятор перед запуском тестов."""

    run_adb(["-s", emulator_serial, "uninstall", PACKAGE_NAME])
    result = install_apk(emulator_serial, latest_apk_path, replace=True)
    if result.returncode != 0:
        pytest.skip(
            "Не удалось установить latest.apk на эмулятор.\n"
            f"STDOUT: {result.stdout}\nSTDERR: {result.stderr}"
        )

    launch = run_adb(
        [
            "-s",
            emulator_serial,
            "shell",
            "monkey",
            "-p",
            PACKAGE_NAME,
            "-c",
            "android.intent.category.LAUNCHER",
            "1",
        ],
        timeout=30,
    )
    if launch.returncode != 0:
        pytest.skip(
            "Не удалось запустить приложение после установки.\n"
            f"STDOUT: {launch.stdout}\nSTDERR: {launch.stderr}"
        )
    time.sleep(5)


