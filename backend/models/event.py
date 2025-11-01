"""Event model for one-time events."""

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import Boolean, Column, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import relationship

from backend.database import Base

if TYPE_CHECKING:
    from backend.models.group import Group
    from backend.models.task import Task


class Event(Base):
    """Model for one-time events."""

    __tablename__ = "events"

    id = Column(Integer, primary_key=True, index=True)
    title = Column(String(255), nullable=False, index=True)
    description = Column(Text, nullable=True)
    event_date = Column(DateTime, nullable=False, index=True)
    reminder_time = Column(DateTime, nullable=True, index=True)
    is_completed = Column(Boolean, default=False, nullable=False)
    group_id = Column(Integer, ForeignKey("groups.id"), nullable=True, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)

    # Relationships
    group = relationship("Group", back_populates="events")

    def __repr__(self) -> str:
        """String representation of Event."""
        return f"<Event(id={self.id}, title='{self.title}', event_date={self.event_date})>"

