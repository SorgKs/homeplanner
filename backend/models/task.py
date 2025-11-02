"""Task model for recurring tasks."""

from datetime import datetime
from enum import Enum
from typing import TYPE_CHECKING

from sqlalchemy import Boolean, Column, DateTime, Enum as SQLEnum, ForeignKey, Integer, Interval, String, Text
from sqlalchemy.orm import relationship

from backend.database import Base

if TYPE_CHECKING:
    from backend.models.event import Event
    from backend.models.group import Group


class RecurrenceType(str, Enum):
    """Recurrence types for tasks."""

    DAILY = "daily"
    WEEKDAYS = "weekdays"  # Monday to Friday
    WEEKENDS = "weekends"  # Saturday and Sunday
    WEEKLY = "weekly"
    MONTHLY = "monthly"
    YEARLY = "yearly"
    CUSTOM = "custom"
    INTERVAL = "interval"


class TaskType(str, Enum):
    """Task type for scheduling."""

    ONE_TIME = "one_time"  # One-time task (no repetition)
    RECURRING = "recurring"  # Scheduled recurrence (daily, weekly, etc.)
    INTERVAL = "interval"  # Repeats after interval from last completion


class Task(Base):
    """Model for recurring tasks."""

    __tablename__ = "tasks"

    id = Column(Integer, primary_key=True, index=True)
    title = Column(String(255), nullable=False, index=True)
    description = Column(Text, nullable=True)
    task_type = Column(SQLEnum(TaskType), nullable=False, default=TaskType.ONE_TIME)
    recurrence_type = Column(SQLEnum(RecurrenceType), nullable=True)  # For recurring tasks only
    recurrence_interval = Column(Integer, nullable=True, default=None)  # Every N days/weeks/etc (for recurring tasks only)
    interval_days = Column(Integer, nullable=True)  # For interval tasks: days between completions
    next_due_date = Column(DateTime, nullable=False, index=True)
    reminder_time = Column(DateTime, nullable=True, index=True)
    is_active = Column(Boolean, default=True, nullable=False)
    group_id = Column(Integer, ForeignKey("groups.id"), nullable=True, index=True)
    last_completed_at = Column(DateTime, nullable=True)
    last_shown_at = Column(DateTime, nullable=True)  # Last time this iteration was shown
    created_at = Column(DateTime, default=datetime.now, nullable=False)
    updated_at = Column(DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)

    # Relationships
    group = relationship("Group", back_populates="tasks")

    def __repr__(self) -> str:
        """String representation of Task."""
        return f"<Task(id={self.id}, title='{self.title}', recurrence_type={self.recurrence_type})>"

