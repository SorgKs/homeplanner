"""Pydantic schemas for TaskHistory model."""

from datetime import datetime

from pydantic import BaseModel, Field, field_serializer, ConfigDict

from backend.models.task_history import TaskHistoryAction
from backend.services.time_manager import get_current_time


class TaskHistoryBase(BaseModel):
    """Base schema for TaskHistory."""

    task_id: int | None = Field(None, description="Task ID (may be NULL for deleted tasks)")
    action: TaskHistoryAction = Field(..., description="Action type")
    action_timestamp: datetime = Field(default_factory=get_current_time, description="Action timestamp")
    iteration_date: datetime | None = Field(None, description="Task iteration date")
    meta_data: str | None = Field(None, description="Additional metadata (JSON string)")
    comment: str | None = Field(None, description="Comment about the action")
    
    @field_serializer('action_timestamp', 'iteration_date', when_used='always')
    def serialize_datetime(self, value: datetime | None) -> str | None:
        """Serialize datetime to string (local time, no timezone conversion)."""
        if value is None:
            return None
        # Remove timezone info if present (all datetimes are stored as local time)
        if value.tzinfo is not None:
            value = value.replace(tzinfo=None)
        # Return ISO format string
        return value.isoformat()


class TaskHistoryCreate(TaskHistoryBase):
    """Schema for creating a new task history entry."""

    pass


class TaskHistoryResponse(TaskHistoryBase):
    """Schema for task history response."""

    id: int

    model_config = ConfigDict(from_attributes=True)
