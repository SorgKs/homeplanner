"""Debug log model for storing client logs."""

from datetime import datetime
import json
from typing import Any
from sqlalchemy import Column, Integer, String, Text, DateTime, Index, TypeDecorator
from sqlalchemy.types import TypeEngine
from backend.database import Base


class JSONType(TypeDecorator):
    """JSON type for storing dict/list data in SQLite."""
    
    impl = Text
    cache_ok = True

    def process_bind_param(self, value: Any | None, dialect: Any) -> str | None:
        if value is None:
            return None
        if isinstance(value, dict):
            return json.dumps(value, ensure_ascii=False)
        return str(value)

    def process_result_value(self, value: str | None, dialect: Any) -> dict[str, Any] | None:
        if value is None:
            return {}
        try:
            parsed = json.loads(value)
            if isinstance(parsed, dict):
                return parsed
            return {}
        except (json.JSONDecodeError, TypeError):
            return {}


class DebugLog(Base):
    """Model for storing debug logs from Android clients.
    
    Supports two formats:
    - v1 (JSON): Uses message_code, tag, context (old format)
    - v2 (Binary chunks): Uses text field (minimalistic format)
    """

    __tablename__ = "debug_logs"

    id = Column(Integer, primary_key=True, index=True)
    timestamp = Column(DateTime, nullable=False, default=datetime.now, index=True)
    level = Column(String(10), nullable=False, index=True)  # DEBUG, INFO, WARN, ERROR
    
    # v1 fields (JSON format)
    tag = Column(String(255), nullable=True, index=True)
    message_code = Column(String(100), nullable=True, index=True)  # Message code from dictionary
    context = Column(JSONType, nullable=True, default=dict)  # JSON context data
    
    # v2 fields (Binary chunk format)
    text = Column(Text, nullable=True)  # Decoded text message (for v2)
    chunk_id = Column(String(50), nullable=True, index=True)  # Chunk ID (for v2)
    
    # Common fields
    device_id = Column(String(255), nullable=True, index=True)  # Unique device identifier
    device_info = Column(String(255), nullable=True)  # Device model, Android version, etc.
    app_version = Column(String(50), nullable=True)  # App version string
    dictionary_revision = Column(String(20), nullable=True)  # Dictionary revision (e.g., "1.0")

    __table_args__ = (
        Index("idx_debug_logs_timestamp", "timestamp"),
        Index("idx_debug_logs_level", "level"),
        Index("idx_debug_logs_tag", "tag"),
        Index("idx_debug_logs_message_code", "message_code"),
        Index("idx_debug_logs_device_id", "device_id"),
        Index("idx_debug_logs_chunk_id", "chunk_id"),
    )

    def __repr__(self) -> str:
        return f"<DebugLog(id={self.id}, level={self.level}, text={self.text or self.message_code}, timestamp={self.timestamp})>"
