"""Pydantic schemas for Group model."""

from datetime import datetime

from pydantic import BaseModel, Field, field_validator


class GroupBase(BaseModel):
    """Base schema for Group."""

    name: str = Field(..., min_length=1, max_length=255, description="Group name")
    description: str | None = Field(None, max_length=500, description="Group description")


class GroupCreate(GroupBase):
    """Schema for creating a new group."""

    pass


class GroupUpdate(BaseModel):
    """Schema for updating a group."""

    name: str | None = Field(None, min_length=1, max_length=255)
    description: str | None = Field(None, max_length=500)

    @field_validator("name")
    @classmethod
    def validate_name(cls, v: str | None) -> str | None:
        """Validate name is not empty if provided."""
        if v is not None and len(v.strip()) == 0:
            raise ValueError("Name cannot be empty")
        return v


class GroupResponse(GroupBase):
    """Schema for group response."""

    id: int
    created_at: datetime
    updated_at: datetime

    class Config:
        """Pydantic config."""

        from_attributes = True

