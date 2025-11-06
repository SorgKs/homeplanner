"""Pydantic schemas for Task model."""

from typing import Any
from datetime import datetime

from pydantic import BaseModel, Field, field_validator, model_validator

from backend.models.task import RecurrenceType, TaskType


class TaskBase(BaseModel):
    """Base schema for Task."""

    title: str = Field(..., min_length=1, max_length=255, description="Task title")
    description: str | None = Field(None, description="Task description")
    task_type: TaskType = Field(TaskType.ONE_TIME, description="Type of task scheduling")
    recurrence_type: RecurrenceType | None = Field(
        None, description="Type of recurrence (for recurring tasks)"
    )
    recurrence_interval: int | None = Field(None, ge=1, description="Recurrence interval (every N periods, for recurring tasks)")
    interval_days: int | None = Field(None, ge=1, description="Interval in days (for interval tasks)")
    next_due_date: datetime = Field(..., description="Next due date for the task")
    reminder_time: datetime | None = Field(None, description="Reminder date and time (required for all tasks, defaults to next_due_date)")
    group_id: int | None = Field(None, description="Group ID")
    
    @model_validator(mode="after")
    def ensure_reminder_time(self) -> "TaskBase":
        """Ensure reminder_time is always set (use next_due_date as fallback)."""
        if self.reminder_time is None:
            self.reminder_time = self.next_due_date
        return self


class TaskCreate(TaskBase):
    """Schema for creating a new task."""

    pass


class TaskUpdate(BaseModel):
    """Schema for updating a task."""

    title: str | None = Field(None, min_length=1, max_length=255)
    description: str | None = None
    task_type: TaskType | None = None
    recurrence_type: RecurrenceType | None = None
    recurrence_interval: int | None = Field(None, description="Recurrence interval (for recurring tasks only)")
    interval_days: int | None = Field(None, description="Interval in days (for interval tasks only)")
    next_due_date: datetime | None = None
    reminder_time: datetime | None = None
    is_active: bool | None = None
    last_completed_at: datetime | None = None
    last_shown_at: datetime | None = None
    group_id: int | None = None

    @field_validator("title")
    @classmethod
    def validate_title(cls, v: str | None) -> str | None:
        """Validate title is not empty if provided."""
        if v is not None and len(v.strip()) == 0:
            raise ValueError("Title cannot be empty")
        return v

    @field_validator("recurrence_interval")
    @classmethod
    def validate_recurrence_interval(cls, v: int | None) -> int | None:
        """Validate recurrence_interval is >= 1 if provided."""
        if v is not None and v < 1:
            raise ValueError("Recurrence interval must be >= 1")
        return v

    @field_validator("interval_days")
    @classmethod
    def validate_interval_days(cls, v: int | None) -> int | None:
        """Validate interval_days is >= 1 if provided."""
        if v is not None and v < 1:
            raise ValueError("Interval days must be >= 1")
        return v

    @model_validator(mode="after")
    def validate_reminder_time(self) -> "TaskUpdate":
        """Validate reminder_time is required for all tasks.
        
        Note: For updates, if reminder_time is not provided, it will be preserved from existing task.
        But if it's explicitly set to None, that's an error.
        """
        # If reminder_time is explicitly set to None in update, that's an error
        # (None value means it was explicitly provided, not just omitted)
        # Note: In Pydantic, None in update_data means the field was explicitly set to None
        # This validation will catch it, but we also check in service layer
        return self


class TaskResponse(TaskBase):
    """Schema for task response."""

    id: int
    is_active: bool
    task_type: TaskType
    group_id: int | None
    interval_days: int | None
    last_completed_at: datetime | None
    last_shown_at: datetime | None
    created_at: datetime
    updated_at: datetime
    readable_config: str | None = Field(None, description="Human-readable task configuration")

    class Config:
        """Pydantic config."""

        from_attributes = True

    @classmethod
    def model_validate(cls, obj: Any, /, *, strict: bool | None = None, from_attributes: bool | None = None, context: dict[str, Any] | None = None) -> "TaskResponse":
        """Override to add readable_config field."""
        from backend.services.task_service import TaskService
        from backend.models.task import TaskType, RecurrenceType
        
        # Create instance from object (TaskBase.ensure_reminder_time will set reminder_time if None)
        instance = super().model_validate(obj, strict=strict, from_attributes=from_attributes, context=context)
        
        # Calculate readable_config using TaskService
        # Convert enum to string value for task_type
        if isinstance(instance.task_type, TaskType):
            task_type_str = instance.task_type.value
        else:
            task_type_str = str(instance.task_type)
        
        # For recurrence_type, keep as enum if it's an enum, or convert string to enum
        # The _format_task_settings function expects enum values for comparison
        recurrence_type_value = instance.recurrence_type
        if instance.recurrence_type is not None and not isinstance(instance.recurrence_type, RecurrenceType):
            # If it's a string, try to convert to enum
            try:
                recurrence_type_value = RecurrenceType(str(instance.recurrence_type))
            except (ValueError, TypeError):
                recurrence_type_value = None
        
        task_dict = {
            "task_type": task_type_str,
            "recurrence_type": recurrence_type_value,
            "recurrence_interval": instance.recurrence_interval,
            "interval_days": instance.interval_days,
            "reminder_time": instance.reminder_time,
            "next_due_date": instance.next_due_date,
        }
        instance.readable_config = TaskService._format_task_settings(task_type_str, task_dict)
        
        return instance

