"""Schemas for debug log API."""

from datetime import datetime
from pydantic import BaseModel, ConfigDict, Field
from typing import Optional, Any


class DebugLogCreate(BaseModel):
    """Schema for creating a debug log entry (binary format with message codes)."""

    timestamp: datetime
    level: str = Field(..., min_length=1, max_length=10)
    tag: Optional[str] = Field(None, max_length=255)
    message_code: str = Field(..., min_length=1, max_length=100)  # Code instead of message text
    context: dict[str, Any] = Field(default_factory=dict)  # Additional context
    device_id: Optional[str] = Field(None, max_length=255, description="Unique device identifier")
    device_info: Optional[str] = Field(None, max_length=255)
    app_version: Optional[str] = Field(None, max_length=50)
    dictionary_revision: Optional[str] = Field(None, max_length=20)  # Dictionary revision (e.g., "1.0")


class DebugLogResponse(BaseModel):
    """Schema for debug log response.
    
    Supports both v1 (JSON) and v2 (binary chunk) formats.
    """

    model_config = ConfigDict(from_attributes=True)

    id: int
    timestamp: datetime
    level: str
    
    # v1 fields (JSON format)
    tag: Optional[str] = None
    message_code: Optional[str] = None  # Code instead of message text
    context: dict[str, Any] = Field(default_factory=dict)  # Additional context
    
    # v2 fields (binary chunk format)
    text: Optional[str] = None  # Decoded text message
    chunk_id: Optional[str] = None  # Chunk ID
    
    # Common fields
    device_id: Optional[str] = None  # Unique device identifier
    device_info: Optional[str] = None
    app_version: Optional[str] = None
    dictionary_revision: Optional[str] = None  # Dictionary revision used


class DebugLogBatchCreate(BaseModel):
    """Schema for batch creating debug log entries."""

    logs: list[DebugLogCreate]
