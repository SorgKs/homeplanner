"""Time management service."""

from datetime import datetime, timedelta
from typing import Optional


class TimeManager:
    """Manages virtual time for time control functionality."""

    _virtual_time: Optional[datetime] = None
    _override_enabled: bool = False

    @classmethod
    def get_current_time(cls) -> datetime:
        """Get the current time (virtual if override is enabled, otherwise real)."""
        if cls._override_enabled and cls._virtual_time is not None:
            return cls._virtual_time
        return datetime.now()

    @classmethod
    def get_real_time(cls) -> datetime:
        """Get the real current time."""
        return datetime.now()

    @classmethod
    def is_override_enabled(cls) -> bool:
        """Check if virtual time override is enabled."""
        return cls._override_enabled

    @classmethod
    def get_virtual_time(cls) -> Optional[datetime]:
        """Get the current virtual time."""
        return cls._virtual_time

    @classmethod
    def shift_time(cls, days: int = 0, hours: int = 0, minutes: int = 0) -> None:
        """Shift virtual time by the specified amount."""
        if not cls._override_enabled:
            cls._override_enabled = True
            cls._virtual_time = datetime.now()

        if cls._virtual_time is None:
            cls._virtual_time = datetime.now()

        cls._virtual_time += timedelta(days=days, hours=hours, minutes=minutes)

    @classmethod
    def set_time(cls, target_datetime: datetime) -> None:
        """Set virtual time to a specific datetime."""
        cls._override_enabled = True
        cls._virtual_time = target_datetime

    @classmethod
    def reset_time(cls) -> None:
        """Reset to real time."""
        cls._override_enabled = False
        cls._virtual_time = None

    @classmethod
    def get_state(cls) -> dict:
        """Get the current time control state."""
        return {
            "override_enabled": cls._override_enabled,
            "virtual_now": cls._virtual_time.isoformat() if cls._virtual_time else None,
            "real_now": cls.get_real_time().isoformat(),
        }


# Backward compatibility
def get_current_time() -> datetime:
    """Get the current time (virtual if override is enabled, otherwise real)."""
    return TimeManager.get_current_time()