"""Utility for controlling current time with optional override."""

from __future__ import annotations

from datetime import datetime, timedelta
from threading import Lock


class TimeManager:
    """Manage virtual current time for debugging/testing."""

    _offset: timedelta | None = None
    _lock = Lock()

    @classmethod
    def get_current_time(cls) -> datetime:
        """Return the effective current time respecting override offset."""
        real_now = datetime.now()
        with cls._lock:
            if cls._offset is None:
                return real_now
            return real_now + cls._offset

    @classmethod
    def get_state(cls) -> dict:
        """Get current override state details."""
        real_now = datetime.now()
        with cls._lock:
            offset = cls._offset
        virtual_now = real_now if offset is None else real_now + offset
        return {
            "real_now": real_now,
            "virtual_now": virtual_now,
            "override_enabled": offset is not None,
            "offset_seconds": offset.total_seconds() if offset is not None else None,
        }

    @classmethod
    def set_override(cls, target_time: datetime) -> datetime:
        """Set absolute virtual time."""
        # Normalize to minute precision (drop seconds and microseconds)
        target_time = target_time.replace(second=0, microsecond=0)
        real_now = datetime.now()
        with cls._lock:
            cls._offset = target_time - real_now
        return cls.get_current_time()

    @classmethod
    def shift(cls, delta: timedelta) -> datetime:
        """Shift current virtual time by delta."""
        with cls._lock:
            base = cls._offset or timedelta()
            cls._offset = base + delta
        return cls.get_current_time()

    @classmethod
    def clear_override(cls) -> None:
        """Reset to real system time."""
        with cls._lock:
            cls._offset = None


def get_current_time() -> datetime:
    """Convenience function."""
    return TimeManager.get_current_time()


def shift_time(delta: timedelta) -> datetime:
    """Shift virtual time by delta and return new time."""
    return TimeManager.shift(delta)


def set_current_time(target_time: datetime) -> datetime:
    """Set virtual time to target."""
    return TimeManager.set_override(target_time)


def reset_time_override() -> None:
    """Disable time override."""
    TimeManager.clear_override()


def get_time_state() -> dict:
    """Expose current state for API."""
    state = TimeManager.get_state()
    return {
        "real_now": state["real_now"],
        "virtual_now": state["virtual_now"],
        "override_enabled": state["override_enabled"],
        "offset_seconds": state["offset_seconds"],
    }


