"""Запуск инструментальных тестов TasksApi через pytest."""

from __future__ import annotations

import subprocess
from pathlib import Path

import pytest

from .adb_utils import install_apk, run_adb

GRADLEW_PATH = Path("/home/sorg/ZFS/Dev/HomePlanner/android/gradlew")
INSTRUMENTATION_APK = Path(
    "/home/sorg/ZFS/Dev/HomePlanner/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
)
INSTRUMENTATION_RUNNER = "com.homeplanner.test/androidx.test.runner.AndroidJUnitRunner"
INSTRUMENTATION_TEST_CLASSES = [
    "com.homeplanner.api.TasksApiInstrumentedTest",
    "com.homeplanner.ui.MainActivityVersionTest",
]


def _run_gradle(*args: str) -> subprocess.CompletedProcess[str]:
    """Выполнить gradle команду в каталоге android и вернуть результат."""

    command = [str(GRADLEW_PATH), *args]
    return subprocess.run(
        command,
        cwd=GRADLEW_PATH.parent,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
    )


@pytest.mark.usefixtures("adb_path", "emulator_serial", "install_latest_apk")
def test_tasks_api_instrumentation_installed(emulator_serial: str) -> None:
    """Убедиться, что опубликованный APK проходит TasksApiInstrumentedTest."""

    if not GRADLEW_PATH.exists():
        pytest.skip("Gradle wrapper недоступен, пропускаем запуск инструментальных тестов.")

    if not INSTRUMENTATION_APK.exists():
        build_result = _run_gradle(":app:assembleDebugAndroidTest")
        assert build_result.returncode == 0, (
            "Не удалось собрать androidTest APK.\n"
            f"STDOUT:\n{build_result.stdout}\nSTDERR:\n{build_result.stderr}"
        )

    install_result = install_apk(emulator_serial, INSTRUMENTATION_APK, replace=True)
    if install_result.returncode != 0:
        pytest.skip(
            "Не удалось установить instrumentation APK на эмулятор.\n"
            f"STDOUT: {install_result.stdout}\nSTDERR: {install_result.stderr}"
        )

    instrument_result = run_adb(
        [
            "shell",
            "am",
            "instrument",
            "-w",
            "-e",
            "class",
            ",".join(INSTRUMENTATION_TEST_CLASSES),
            INSTRUMENTATION_RUNNER,
        ],
        timeout=120,
    )

    if instrument_result.returncode != 0:
        raise AssertionError(
            "Инструментальные тесты завершились с ошибкой.\n"
            f"STDOUT:\n{instrument_result.stdout}\nSTDERR:\n{instrument_result.stderr}"
        )


