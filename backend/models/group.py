"""Group model for organizing tasks."""

from typing import TYPE_CHECKING

from sqlalchemy import Column, DateTime, Integer, String
from sqlalchemy.orm import relationship

from backend.database import Base
from backend.services.time_manager import get_current_time

if TYPE_CHECKING:
    from backend.models.event import Event
    from backend.models.task import Task


class Group(Base):
    """Model for task groups."""

    __tablename__ = "groups"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(255), nullable=False, unique=True, index=True)
    description = Column(String(500), nullable=True)
    created_at = Column(DateTime, default=get_current_time, nullable=False)
    updated_at = Column(DateTime, default=get_current_time, onupdate=get_current_time, nullable=False)

    # Relationships
    events = relationship("Event", back_populates="group", cascade="all, delete-orphan")
    tasks = relationship("Task", back_populates="group", cascade="all, delete-orphan")

    def __repr__(self) -> str:
        """String representation of Group."""
        return f"<Group(id={self.id}, name='{self.name}')>"

