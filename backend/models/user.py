"""User model representing an assignee of tasks."""

from datetime import datetime
from enum import Enum

from sqlalchemy import Boolean, Column, DateTime, Enum as SQLEnum, Integer, String
from sqlalchemy.orm import relationship

from backend.database import Base
from backend.models.task_assignment import task_user_association


class UserRole(str, Enum):
    """Privilege levels for users."""

    ADMIN = "admin"
    REGULAR = "regular"
    GUEST = "guest"


class User(Base):
    """Model for task users."""

    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(255), nullable=False, unique=True, index=True)
    email = Column(String(255), nullable=True, unique=True)
    role = Column(SQLEnum(UserRole), default=UserRole.REGULAR, nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime, default=datetime.now, nullable=False)
    updated_at = Column(DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)

    tasks = relationship(
        "Task",
        secondary=task_user_association,
        back_populates="assignees",
        lazy="selectin",
    )

    def __repr__(self) -> str:
        """String representation of User."""
        return f"<User(id={self.id}, name='{self.name}', role={self.role}, active={self.is_active})>"


__all__ = ["User", "UserRole"]


