"""Интеграционные проверки Android-приложения в запущенном эмуляторе."""

from __future__ import annotations

import time
from typing import TYPE_CHECKING

import pytest

from .adb_utils import PACKAGE_NAME, run_adb

if TYPE_CHECKING:
    from _pytest.capture import CaptureFixture
    from _pytest.fixtures import FixtureRequest
    from _pytest.logging import LogCaptureFixture
    from _pytest.monkeypatch import MonkeyPatch
    from pytest_mock.plugin import MockerFixture


MAIN_ACTIVITY = "com.homeplanner/.MainActivity"


@pytest.mark.usefixtures("install_latest_apk")
def test_homeplanner_package_installed(adb_path: str, emulator_serial: str) -> None:
    """Пакет приложения должен быть установлен на эмуляторе."""

    result = run_adb(["-s", emulator_serial, "shell", "pm", "list", "packages", PACKAGE_NAME])
    assert result.returncode == 0, f"Не удалось получить список пакетов: {result.stderr}"
    assert f"package:{PACKAGE_NAME}" in result.stdout, (
        f"Пакет {PACKAGE_NAME} отсутствует на устройстве. stdout={result.stdout!r}"
    )


@pytest.mark.usefixtures("install_latest_apk")
def test_main_activity_starts(adb_path: str, emulator_serial: str) -> None:
    """Главная активность должна успешно запускаться и попадать в фокус."""

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
    assert launch.returncode == 0, f"Не удалось запустить приложение: {launch.stderr}"
    time.sleep(3)

    focus_result = run_adb(["-s", emulator_serial, "shell", "dumpsys", "window", "windows"], timeout=30)
    assert focus_result.returncode == 0, f"dumpsys window завершился с ошибкой: {focus_result.stderr}"
    focus_stdout = focus_result.stdout
    is_focused = (
        "com.homeplanner/.MainActivity" in focus_stdout
        or "com.homeplanner/com.homeplanner.MainActivity" in focus_stdout
    )
    assert is_focused, (
        "Главная активность не в фокусе. Фрагмент dumpsys:\n"
        f"{focus_stdout[-1000:]}"
    )


@pytest.mark.usefixtures("install_latest_apk")
def test_today_tab_present_in_ui(adb_path: str, emulator_serial: str) -> None:
    """В корневом UI должна присутствовать вкладка «Сегодня»."""

    run_adb(
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
    time.sleep(3)

    dump_result = run_adb(
        ["-s", emulator_serial, "exec-out", "uiautomator", "dump", "--compressed", "/dev/tty"],
        timeout=30,
    )
    if dump_result.returncode != 0:
        pytest.skip(f"uiautomator dump недоступен: {dump_result.stderr.strip()}")

    ui_dump = dump_result.stdout
    if "<?xml" not in ui_dump:
        pytest.skip("Не удалось получить XML иерархии UI (пустой вывод uiautomator).")

    assert "Сегодня" in ui_dump, (
        "Текст «Сегодня» не найден в иерархии UI. Проверьте локализацию и актуальность экрана.\n"
        f"Фрагмент дампа:\n{ui_dump[:2000]}"
    )


