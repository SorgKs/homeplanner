"""Pydantic schemas for synchronization events and operations."""

from datetime import datetime
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class SyncEvent(BaseModel):
    """Base schema for synchronization events."""

    event_type: str = Field(..., description="Type of event (create, update, delete, complete, uncomplete)")
    entity_type: str = Field(..., description="Entity type (task, user, group)")
    entity_id: Optional[int] = Field(None, description="Entity ID (None for create)")
    timestamp: datetime = Field(..., description="Client timestamp of the event")
    changes: Optional[Dict[str, Any]] = Field(None, description="Changed data for create/update events")
    client_hash: Optional[str] = Field(None, description="Client's hash for the entity")


class SyncEventResponse(BaseModel):
    """Response for sync event processing."""

    status: str = Field(..., description="Status: confirmed, conflict, error")
    entity_type: str = Field(..., description="Entity type that was processed")
    entity_id: Optional[int] = Field(None, description="Entity ID that was processed")
    server_hash: Optional[str] = Field(None, description="Server's hash for the entity")
    message: Optional[str] = Field(None, description="Error message if status is error")


class HashVerificationRequest(BaseModel):
    """Request for hash verification."""

    entity_type: str = Field(..., description="Entity type (tasks, users, groups)")
    hashes: List[Dict[str, Any]] = Field(..., description="List of {id, hash} pairs from client")


class HashVerificationResponse(BaseModel):
    """Response for hash verification."""

    status: str = Field(..., description="Status: verified")
    conflicts: List[Dict[str, Any]] = Field(default_factory=list, description="Conflicting entities")
    missing_on_client: List[Dict[str, Any]] = Field(default_factory=list, description="Entities missing on client")
    missing_on_server: List[Dict[str, Any]] = Field(default_factory=list, description="Entities missing on server")


class ConflictResolutionRequest(BaseModel):
    """Request to resolve conflicts."""

    entity_type: str = Field(..., description="Entity type (tasks, users, groups)")
    resolutions: List[Dict[str, Any]] = Field(..., description="List of conflict resolutions")


class ConflictResolutionResponse(BaseModel):
    """Response for conflict resolution."""

    status: str = Field(..., description="Status: resolved")
    applied: List[Dict[str, Any]] = Field(default_factory=list, description="Successfully applied resolutions")
    failed: List[Dict[str, Any]] = Field(default_factory=list, description="Failed resolutions")


class ApplyResolvedDataRequest(BaseModel):
    """Request to apply resolved data after conflict."""

    entity_type: str = Field(..., description="Entity type (tasks, users, groups)")
    resolved_data: List[Dict[str, Any]] = Field(..., description="List of resolved entity data to apply")


class ApplyResolvedDataResponse(BaseModel):
    """Response for applying resolved data."""

    status: str = Field(..., description="Status: applied")
    applied: List[int] = Field(default_factory=list, description="IDs of successfully applied entities")
    failed: List[Dict[str, Any]] = Field(default_factory=list, description="Failed applications with errors")