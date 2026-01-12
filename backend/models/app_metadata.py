"""Model for application metadata."""

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import Column, DateTime, String

from backend.database import Base

if TYPE_CHECKING:
    pass


class AppMetadata(Base):
    """Model for application-level metadata."""

    __tablename__ = "app_metadata"

    key = Column(String(255), primary_key=True, index=True)
    value = Column(DateTime, nullable=False)
    updated_at = Column(DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)

    def __repr__(self) -> str:
        """String representation of AppMetadata."""
        return f"<AppMetadata(key='{self.key}', value={self.value})>"

