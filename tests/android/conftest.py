# Android test configuration

import pytest
from tests.android.config import AndroidTestConfig


@pytest.fixture(scope="session")
def android_config():
    """Fixture providing Android test configuration."""
    return AndroidTestConfig()


@pytest.fixture(scope="session")
def adb_device(android_config):
    """Fixture providing ADB device connection."""
    # Implementation would connect to device
    return android_config.device_id