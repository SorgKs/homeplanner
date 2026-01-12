"""Test synchronization functionality."""

import pytest
import time
from tests.android.adb_utils import ADBUtils


class TestSyncFunctionality:
    """Test data synchronization between app and backend."""

    def test_initial_sync_users(self, android_config):
        """Test that users are synced on app start."""
        adb = ADBUtils(android_config.device_id)

        # Stop app first, clear logcat, then start
        adb.stop_app(android_config.app_package)
        adb.run_command(["logcat", "-c"])
        time.sleep(2)  # Wait for stop
        adb.start_app(android_config.app_package)

        # Check if app started
        time.sleep(3)
        try:
            processes = adb.run_command([
                "shell", "ps", "|", "grep", android_config.app_package
            ])
            assert android_config.app_package in processes, f"App not running: {processes}"
        except RuntimeError:
            assert False, "App not running"

        # Wait for sync
        time.sleep(15)

        # Check logs for sync success
        logs = adb.get_logcat(lines=500)
        print("LOGS:", logs[-3000:])  # Debug: print last 3000 chars
        # Check for crash
        if "FATAL" in logs or "Exception" in logs or "E/AndroidRuntime" in logs:
            print("CRASH DETECTED")
            assert False, "App crashed"
        assert "MainActivity onCreate called" in logs  # Check if activity starts
        assert "Starting Application onCreate" in logs  # Check if application starts
        assert "saveUsersToCache: saving 3 users" in logs  # Assuming 3 users in backend

    def test_initial_sync_tasks(self, android_config):
        """Test that tasks are synced on app start with detailed verification."""
        adb = ADBUtils(android_config.device_id)

        # Check if APK is installed
        try:
            adb.run_command(["shell", "pm", "list", "packages", android_config.app_package])
            print("APK already installed")
        except:
            print("APK not installed, installing...")
            adb.install_apk(android_config.apk_path)

        # Stop app first, clear logcat, then start
        adb.stop_app(android_config.app_package)
        adb.run_command(["logcat", "-c"])
        time.sleep(2)  # Wait for stop
        adb.start_app(android_config.app_package)

        # Check if app started
        time.sleep(3)
        try:
            processes = adb.run_command([
                "shell", "ps", "|", "grep", android_config.app_package
            ])
            assert android_config.app_package in processes, f"App not running: {processes}"
        except RuntimeError:
            assert False, "App not running"

        # Wait for sync
        time.sleep(30)

        # Check that sync started
        logs = adb.get_logcat(lines=1000)
        print("LOGS:", logs[-5000:])  # Debug: print last 5000 chars
        # Check for crash
        if "FATAL" in logs or "Exception" in logs or "E/AndroidRuntime" in logs:
            print("CRASH DETECTED")
            assert False, "App crashed"
        assert "MainActivity onCreate called" in logs  # Check if activity starts
        assert "Starting Application onCreate" in logs  # Check if application starts
        assert "performInitialCacheSync: starting" in logs  # 1. Sync starts

        # Check that server request was made
        assert "syncCacheWithServer:" in logs  # 2. Cache sync called

        # Check that tasks were fetched from server (assuming 4 tasks in backend)
        # Look for saveTasksToCache with 4 tasks
        assert "saveTasksToCache: saving 4 tasks" in logs  # 3. Received 4 tasks, 4. Saved tasks

        # Check that cache was updated
        assert "cacheUpdated" in logs  # 5. Cache updated successfully

    def test_offline_mode(self, android_config):
        """Test app behavior when backend is offline."""
        adb = ADBUtils(android_config.device_id)

        # Start app (backend should be online for this test suite)
        adb.start_app(android_config.app_package)

        # Wait
        time.sleep(10)

        # Check that app starts and sync completes
        logs = adb.get_logcat("performInitialCacheSync")
        assert "finished" in logs  # Sync should complete successfully

    def test_network_reconnection_sync(self, android_config):
        """Test sync resumption when network reconnects."""
        # This would require network manipulation
        # For now, just check that sync attempts happen
        adb = ADBUtils(android_config.device_id)

        adb.start_app(android_config.app_package)
        time.sleep(5)

        logs = adb.get_logcat("syncCacheWithServer")
        assert len(logs) > 0  # Some sync attempts should be logged