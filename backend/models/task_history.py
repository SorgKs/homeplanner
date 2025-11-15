"""Task history model for tracking task iterations and user actions."""

from enum import Enum
from typing import TYPE_CHECKING

from sqlalchemy import Column, DateTime, Enum as SQLEnum, ForeignKey, Integer, String, Text
from sqlalchemy.orm import relationship

from backend.database import Base
from backend.services.time_manager import get_current_time

if TYPE_CHECKING:
    from backend.models.task import Task


class TaskHistoryAction(str, Enum):
    """Actions that can be recorded in task history."""

    CREATED = "created"
    FIRST_SHOWN = "first_shown"  # Первый показ итерации задачи
    CONFIRMED = "confirmed"  # Подтверждение выполнения
    UNCONFIRMED = "unconfirmed"  # Отмена подтверждения
    EDITED = "edited"
    DELETED = "deleted"
    ACTIVATED = "activated"
    DEACTIVATED = "deactivated"


class TaskHistory(Base):
    """Model for tracking task history and iterations."""

    __tablename__ = "task_history"

    id = Column(Integer, primary_key=True, index=True)
    task_id = Column(Integer, ForeignKey("tasks.id", ondelete="SET NULL"), nullable=True, index=True)
    action = Column(SQLEnum(TaskHistoryAction), nullable=False, index=True)
    action_timestamp = Column(DateTime, default=get_current_time, nullable=False, index=True)
    
    # Дополнительные данные для контекста
    iteration_date = Column(DateTime, nullable=True, index=True)  # Дата итерации задачи (reminder_time на момент действия)
    meta_data = Column(Text, nullable=True)  # JSON метаданные (старые/новые значения при редактировании и т.д.)
    comment = Column(Text, nullable=True)  # Комментарий к действию
    
    # Relationships
    task = relationship("Task", backref="history")

    def __repr__(self) -> str:
        """String representation of TaskHistory."""
        return f"<TaskHistory(id={self.id}, task_id={self.task_id}, action={self.action}, timestamp={self.action_timestamp})>"
