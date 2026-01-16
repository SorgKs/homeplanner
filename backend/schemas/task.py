"""Pydantic schemas for Task model."""

from typing import Any, List
from datetime import datetime
from enum import Enum

from pydantic import BaseModel, Field, field_validator, model_validator, ConfigDict

from backend.models.task import RecurrenceType, TaskType
from backend.schemas.user import UserSummary


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
    reminder_time: datetime = Field(..., description="Reminder date and time (required for all tasks)")
    alarm: bool = Field(default=False, description="Whether the task has an alarm")
    group_id: int | None = Field(None, description="Group ID")




class TaskCreate(TaskBase):
    """Schema for creating a new task."""

    assigned_user_ids: list[int] = Field(default_factory=list, description="IDs of users assigned to this task")


class TaskUpdate(BaseModel):
    """Schema for updating a task."""

    title: str | None = Field(None, min_length=1, max_length=255)
    description: str | None = None
    task_type: TaskType | None = None
    recurrence_type: RecurrenceType | None = None
    recurrence_interval: int | None = Field(None, description="Recurrence interval (for recurring tasks only)")
    interval_days: int | None = Field(None, description="Interval in days (for interval tasks only)")
    reminder_time: datetime | None = None
    enabled: bool | None = None  # Task is enabled (replaces active)
    completed: bool | None = None  # Task is completed (replaces last_completed_at)
    last_shown_at: datetime | None = None
    alarm: bool | None = None
    group_id: int | None = None
    assigned_user_ids: list[int] | None = Field(
        default=None,
        description="Full list of user IDs assigned to task (omit to keep unchanged)",
    )

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
    enabled: bool  # Task is enabled (replaces active)
    completed: bool  # Task is completed (replaces last_completed_at)
    task_type: TaskType
    group_id: int | None
    interval_days: int | None
    alarm: bool
    last_shown_at: datetime | None
    created_at: datetime
    updated_at: datetime
    readable_config: str | None = Field(None, description="Human-readable task configuration")
    assigned_user_ids: list[int] = Field(default_factory=list, description="List of user IDs assigned to the task")
    assignees: list[UserSummary] = Field(default_factory=list, description="Assigned user objects")

    model_config = ConfigDict(from_attributes=True)

    @classmethod
    def model_validate(cls, obj: Any, /, *, strict: bool | None = None, from_attributes: bool | None = None, context: dict[str, Any] | None = None) -> "TaskResponse":
        """Override to add readable_config field."""
        from backend.services.task_service import TaskService
        from backend.models.task import TaskType, RecurrenceType
        
        # Create instance from object
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
        }
        instance.readable_config = TaskService._format_task_settings(task_type_str, task_dict)

        # Populate assigned_user_ids from assignees if available
        if instance.assignees:
            instance.assigned_user_ids = [user.id for user in instance.assignees if user.id is not None]
        elif hasattr(obj, "assignees"):
            assignees = getattr(obj, "assignees") or []
            try:
                instance.assigned_user_ids = [getattr(user, "id") for user in assignees if getattr(user, "id", None) is not None]
            except Exception:
                instance.assigned_user_ids = []
        
        return instance


class TaskOperationType(str, Enum):
    """Supported operation types for batched task sync."""

    CREATE = "create"
    UPDATE = "update"
    DELETE = "delete"
    COMPLETE = "complete"
    UNCOMPLETE = "uncomplete"


class TaskSyncOperation(BaseModel):
    """Single operation in sync queue batch."""

    operation: TaskOperationType
    timestamp: datetime = Field(..., description="Client timestamp of operation")
    task_id: int | None = Field(
        None,
        description="Target task identifier (None for create)",
    )
    payload: dict[str, Any] | None = Field(
        default=None,
        description="Optional payload for create/update operations",
    )


class TaskSyncRequest(BaseModel):
    """Request payload for /tasks/sync-queue endpoint."""

    operations: list[TaskSyncOperation] = Field(
        default_factory=list,
        description="List of operations to apply in chronological order",
    )

