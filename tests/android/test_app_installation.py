"""Test app installation and basic functionality."""

import pytest
from tests.android.adb_utils import ADBUtils


class TestAppInstallation:
    """Test app installation, start, and basic operations."""

    def test_app_installation(self, android_config):
        """Test that app can be installed successfully."""
        adb = ADBUtils(android_config.device_id)

        # Install APK
        adb.install_apk(android_config.apk_path)

        # Verify app is installed
        installed_packages = adb.run_command([
            "shell", "pm", "list", "packages", android_config.app_package
        ])
        assert android_config.app_package in installed_packages

    def test_app_start(self, android_config):
        """Test that app can start successfully."""
        adb = ADBUtils(android_config.device_id)

        # Start app
        adb.start_app(android_config.app_package)

        # Wait a bit for app to start
        import time
        time.sleep(5)

        # Check if app process is running
        processes = adb.run_command([
            "shell", "ps", "|", "grep", android_config.app_package
        ])
        assert android_config.app_package in processes

    def test_app_stop(self, android_config):
        """Test that app can be stopped."""
        adb = ADBUtils(android_config.device_id)

        # Stop app
        adb.stop_app(android_config.app_package)

        # Check that stop command succeeded (process may not be found if already stopped)
        # We just verify the command doesn't fail

    def test_app_restart(self, android_config):
        """Test that app can be restarted."""
        adb = ADBUtils(android_config.device_id)

        # Stop app
        adb.stop_app(android_config.app_package)

        # Start app again
        adb.start_app(android_config.app_package)

        # Check that app started
        import time
        time.sleep(3)
        processes = adb.run_command([
            "shell", "ps", "|", "grep", android_config.app_package
        ])
        assert android_config.app_package in processes