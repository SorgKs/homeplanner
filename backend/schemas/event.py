"""Pydantic schemas for Event model."""

from datetime import datetime

from pydantic import BaseModel, Field, field_validator


class EventBase(BaseModel):
    """Base schema for Event."""

    title: str = Field(..., min_length=1, max_length=255, description="Event title")
    description: str | None = Field(None, description="Event description")
    event_date: datetime = Field(..., description="Event date and time")
    reminder_time: datetime | None = Field(None, description="Reminder date and time")
    group_id: int | None = Field(None, description="Group ID")


class EventCreate(EventBase):
    """Schema for creating a new event."""

    pass


class EventUpdate(BaseModel):
    """Schema for updating an event."""

    title: str | None = Field(None, min_length=1, max_length=255)
    description: str | None = None
    event_date: datetime | None = None
    reminder_time: datetime | None = None
    is_completed: bool | None = None
    group_id: int | None = None

    @field_validator("title")
    @classmethod
    def validate_title(cls, v: str | None) -> str | None:
        """Validate title is not empty if provided."""
        if v is not None and len(v.strip()) == 0:
            raise ValueError("Title cannot be empty")
        return v


class EventResponse(EventBase):
    """Schema for event response."""

    id: int
    is_completed: bool
    group_id: int | None
    created_at: datetime
    updated_at: datetime

    class Config:
        """Pydantic config."""

        from_attributes = True

