"""Utilities for date and time operations."""

from datetime import datetime, timedelta

from sqlalchemy.orm import Session

from backend.config import get_settings
from backend.services.time_manager import get_current_time

# Key for storing last task update timestamp in AppMetadata
LAST_UPDATE_KEY = "last_task_update"


def get_day_start(dt: datetime) -> datetime:
    """Get the start of day for given datetime using day_start_hour from config.
    
    Args:
        dt: Datetime to get day start for.
        
    Returns:
        Datetime representing start of current day (at day_start_hour).
    """
    settings = get_settings()
    day_start_hour = settings.day_start_hour
    
    # If current hour >= day_start_hour, day started today at day_start_hour
    # If current hour < day_start_hour, day started yesterday at day_start_hour
    if dt.hour >= day_start_hour:
        return dt.replace(hour=day_start_hour, minute=0, second=0, microsecond=0)
    else:
        # Day started yesterday at day_start_hour
        yesterday = dt - timedelta(days=1)
        return yesterday.replace(hour=day_start_hour, minute=0, second=0, microsecond=0)


def get_last_update(db: Session) -> datetime | None:
    """Get last task update timestamp from metadata.
    
    Args:
        db: Database session.
        
    Returns:
        Last update timestamp or None if not set.
    """
    from backend.models.app_metadata import AppMetadata
    
    metadata = db.query(AppMetadata).filter(AppMetadata.key == LAST_UPDATE_KEY).first()
    if metadata:
        return metadata.value
    return None


def set_last_update(db: Session, timestamp: datetime | None = None, commit: bool = False) -> None:
    """Set last task update timestamp in metadata.

    Args:
        db: Database session.
        timestamp: Timestamp to set (if None, uses real current time, not virtual).
        commit: If True, commit the transaction. If False, caller should commit.
    """
    from backend.models.app_metadata import AppMetadata

    if timestamp is None:
        from backend.services.time_manager import TimeManager
        timestamp = TimeManager.get_real_time()

    metadata = db.query(AppMetadata).filter(AppMetadata.key == LAST_UPDATE_KEY).first()
    if metadata:
        metadata.value = timestamp
    else:
        metadata = AppMetadata(key=LAST_UPDATE_KEY, value=timestamp)
        db.add(metadata)

    if commit:
        db.commit()


def is_new_day(db: Session) -> bool:
    """Check if a new day has started since last update.

    Uses real time to determine day boundaries, independent of virtual time overrides.

    Args:
        db: Database session.

    Returns:
        True if new day has started, False otherwise.
    """
    from backend.services.time_manager import TimeManager

    last_update = get_last_update(db)
    if last_update is None:
        # First time - consider it a new day
        return True

    now = TimeManager.get_real_time()
    last_update_day_start = get_day_start(last_update)
    current_day_start = get_day_start(now)

    return last_update_day_start < current_day_start


def format_datetime_short(dt: datetime) -> str:
    """Format datetime as short readable string."""
    months = {
        1: "января", 2: "февраля", 3: "марта", 4: "апреля",
        5: "мая", 6: "июня", 7: "июля", 8: "августа",
        9: "сентября", 10: "октября", 11: "ноября", 12: "декабря"
    }
    return f"{dt.day} {months[dt.month]} в {dt.strftime('%H:%M')}"


def format_datetime_for_history(dt: datetime) -> str:
    """Format datetime for history comments.
    
    Formats datetime in human-readable way (no timezone conversion).
    """
    if dt is None:
        return "не установлено"
    
    # Format in human-readable way (same as _format_datetime_short)
    months = {
        1: "января", 2: "февраля", 3: "марта", 4: "апреля",
        5: "мая", 6: "июня", 7: "июля", 8: "августа",
        9: "сентября", 10: "октября", 11: "ноября", 12: "декабря"
    }
    return f"{dt.day} {months[dt.month]} {dt.year} в {dt.strftime('%H:%M')}"