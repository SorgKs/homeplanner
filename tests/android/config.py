# Android test configuration
import json
import os

class AndroidTestConfig:
    """Configuration for Android instrumentation tests."""

    def __init__(self):
        self.device_id = "2f0db29a"  # Default device
        self.app_package = "com.homeplanner"
        self.test_package = f"{self.app_package}.test"

        # Read version from android/version.json and pyproject.toml
        version_file = "android/version.json"
        pyproject_file = "pyproject.toml"

        if os.path.exists(version_file):
            with open(version_file, 'r') as f:
                version_data = json.load(f)
                patch = version_data.get("patch", 0) - 1  # Use the patch from the last successful build
        else:
            patch = 0

        # For simplicity, hardcode major and minor as per pyproject.toml
        major = 0
        minor = 3

        apk_name = f"homeplanner_v{major}_{minor}_{patch}.apk"
        self.apk_path = f"android/app/build/outputs/apk/debug/{apk_name}"
        self.test_apk_path = "android/app/build/outputs/apk/androidTest/debug/homeplanner-debug-androidTest.apk"
        self.timeout = 30000  # 30 seconds
        self.retry_attempts = 3