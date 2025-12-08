"""Task model for recurring tasks."""

from enum import Enum
from typing import TYPE_CHECKING

from sqlalchemy import Boolean, Column, DateTime, Enum as SQLEnum, ForeignKey, Integer, Interval, String, Text
from sqlalchemy.orm import relationship

from backend.database import Base
from backend.services.time_manager import get_current_time
from backend.models.task_assignment import task_user_association

if TYPE_CHECKING:
    from backend.models.event import Event
    from backend.models.group import Group


class RecurrenceType(str, Enum):
    """Recurrence types for tasks."""

    DAILY = "daily"
    WEEKDAYS = "weekdays"  # Monday to Friday
    WEEKENDS = "weekends"  # Saturday and Sunday
    WEEKLY = "weekly"
    MONTHLY = "monthly"  # By day of month (e.g., 15th of each month)
    MONTHLY_WEEKDAY = "monthly_weekday"  # By weekday of month (e.g., 2nd Tuesday of each month)
    YEARLY = "yearly"  # By date (e.g., January 15th each year)
    YEARLY_WEEKDAY = "yearly_weekday"  # By weekday of year (e.g., 1st Monday of January each year)
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
    reminder_time = Column(DateTime, nullable=False, index=True)  # Reminder date and time (required for all tasks)
    active = Column(Boolean, default=True, nullable=False)  # Task is active (replaces is_active)
    completed = Column(Boolean, default=False, nullable=False)  # Task is completed (replaces last_completed_at)
    group_id = Column(Integer, ForeignKey("groups.id"), nullable=True, index=True)
    last_shown_at = Column(DateTime, nullable=True)  # Last time this iteration was shown
    created_at = Column(DateTime, default=get_current_time, nullable=False)
    updated_at = Column(DateTime, default=get_current_time, onupdate=get_current_time, nullable=False)

    # Relationships
    group = relationship("Group", back_populates="tasks")
    assignees = relationship(
        "User",
        secondary=task_user_association,
        back_populates="tasks",
        lazy="selectin",
    )

    def __repr__(self) -> str:
        """String representation of Task."""
        return f"<Task(id={self.id}, title='{self.title}', recurrence_type={self.recurrence_type})>"

