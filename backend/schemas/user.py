"""Pydantic schemas for User model."""

from datetime import datetime
from pydantic import BaseModel, Field, ConfigDict

from backend.models.user import UserRole


class UserBase(BaseModel):
    """Base schema for user."""

    name: str = Field(..., min_length=1, max_length=255, description="Display name")
    email: str | None = Field(None, max_length=255, description="Optional email")
    role: UserRole = Field(UserRole.REGULAR, description="Privilege level")
    is_active: bool = Field(True, description="Whether user can be assigned or log in")


class UserCreate(UserBase):
    """Schema for creating a user."""

    pass


class UserUpdate(BaseModel):
    """Schema for updating a user."""

    name: str | None = Field(None, min_length=1, max_length=255)
    email: str | None = Field(None, max_length=255)
    role: UserRole | None = None
    is_active: bool | None = None


class UserResponse(UserBase):
    """Schema returned from API."""

    id: int
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)


class UserSummary(BaseModel):
    """Lightweight schema to embed with tasks."""

    id: int
    name: str
    email: str | None = None
    role: UserRole
    is_active: bool

    model_config = ConfigDict(from_attributes=True)


__all__ = [
    "UserBase",
    "UserCreate",
    "UserUpdate",
    "UserResponse",
    "UserSummary",
]


