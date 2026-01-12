# Android test configuration

class AndroidTestConfig:
    """Configuration for Android instrumentation tests."""

    def __init__(self):
        self.device_id = "2f0db29a"  # Default device
        self.app_package = "com.homeplanner"
        self.test_package = f"{self.app_package}.test"
        self.apk_path = "android/app/build/outputs/apk/debug/homeplanner_v0_3_59.apk"
        self.test_apk_path = "android/app/build/outputs/apk/androidTest/debug/homeplanner-debug-androidTest.apk"
        self.timeout = 30000  # 30 seconds
        self.retry_attempts = 3