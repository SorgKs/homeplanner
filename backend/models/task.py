"""Task domain models for HomePlanner vNext."""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import TYPE_CHECKING
from uuid import uuid4

from sqlalchemy import (
    Boolean,
    DateTime,
    Enum as SQLEnum,
    ForeignKey,
    Integer,
    String,
    Text,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from backend.database import Base

if TYPE_CHECKING:
    from backend.models.group import Group


def _uuid_str() -> str:
    """Generate a UUIDv4 string."""

    return str(uuid4())


class RecurrenceType(str, Enum):
    """Типы повторяемости задач."""

    DAILY = "daily"
    WEEKDAYS = "weekdays"  # Monday to Friday
    WEEKENDS = "weekends"  # Saturday and Sunday
    WEEKLY = "weekly"
    MONTHLY = "monthly"  # By day of month (e.g., 15th of each month)
    YEARLY = "yearly"  # By date (e.g., January 15th each year)
    CUSTOM = "custom"
    INTERVAL = "interval"


class TaskType(str, Enum):
    """Типы задач по характеру планирования."""

    ONE_TIME = "one_time"
    RECURRING = "recurring"
    INTERVAL = "interval"


class Task(Base):
    """Модель задачи с учётом ревизий, напоминаний и повторяемости."""

    __tablename__ = "tasks"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    title: Mapped[str] = mapped_column(String(255), nullable=False, index=True)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    task_type: Mapped[TaskType] = mapped_column(SQLEnum(TaskType), nullable=False, default=TaskType.ONE_TIME)
    recurrence_type: Mapped[RecurrenceType | None] = mapped_column(SQLEnum(RecurrenceType), nullable=True)
    recurrence_interval: Mapped[int | None] = mapped_column(Integer, nullable=True, default=None)
    interval_days: Mapped[int | None] = mapped_column(Integer, nullable=True)
    reminder_time: Mapped[datetime] = mapped_column(DateTime, nullable=False, index=True)
    completed: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    group_id: Mapped[int | None] = mapped_column(ForeignKey("groups.id"), nullable=True, index=True)
    revision: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    soft_lock_owner: Mapped[str | None] = mapped_column(String(64), nullable=True)
    soft_lock_expires_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    last_shown_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.now, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)

    group: Mapped["Group | None"] = relationship("Group", back_populates="tasks")
    recurrence: Mapped["TaskRecurrence | None"] = relationship(
        "TaskRecurrence",
        back_populates="task",
        cascade="all, delete-orphan",
        uselist=False,
    )
    notifications: Mapped[list["TaskNotification"]] = relationship(
        "TaskNotification",
        back_populates="task",
        cascade="all, delete-orphan",
    )

    def __repr__(self) -> str:
        """Вернуть строковое представление задачи."""

        return f"<Task(id={self.id}, title='{self.title}', revision={self.revision})>"


class TaskRecurrence(Base):
    """Recurrence configuration extracted to a separate table."""

    __tablename__ = "task_recurrence"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid_str)
    task_id: Mapped[int] = mapped_column(ForeignKey("tasks.id", ondelete="CASCADE"), unique=True, nullable=False)
    rrule: Mapped[str] = mapped_column(Text, nullable=False)
    end_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.now, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)

    task: Mapped["Task"] = relationship("Task", back_populates="recurrence", uselist=False)


class TaskNotification(Base):
    """Notification configuration for tasks."""

    __tablename__ = "task_notifications"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid_str)
    task_id: Mapped[int] = mapped_column(ForeignKey("tasks.id", ondelete="CASCADE"), nullable=False, index=True)
    notification_type: Mapped[str] = mapped_column(String(32), nullable=False, default="reminder")
    channel: Mapped[str] = mapped_column(String(32), nullable=False, default="local")
    offset_minutes: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.now, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)

    task: Mapped["Task"] = relationship("Task", back_populates="notifications")


