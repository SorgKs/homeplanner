"""Pydantic schemas for Task model."""

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
    reminder_time: datetime | None = Field(None, description="Reminder date and time")
    group_id: int | None = Field(None, description="Group ID")


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
    def validate_recurring_reminder_time(self) -> "TaskBase":
        """Validate reminder_time is required for all recurring tasks."""
        # For all recurring tasks, reminder_time is required
        if (self.task_type == TaskType.RECURRING and 
            self.reminder_time is None):
            raise ValueError("reminder_time is required for recurring tasks (время обязательно для задач типа расписание)")
        
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

    class Config:
        """Pydantic config."""

        from_attributes = True

