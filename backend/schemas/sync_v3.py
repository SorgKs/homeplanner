"""Pydantic schemas for sync API v0.3."""

from datetime import datetime
from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field


class HashCheckRequest(BaseModel):
    """Request for hash-check endpoint."""

    entity_type: str = Field(..., description="Entity type: tasks, users, groups")
    hashes: List[Dict[str, Any]] = Field(..., description="List of {id: int, hash: str} pairs from client")


class HashCheckResponse(BaseModel):
    """Response for hash-check endpoint."""

    status: str = Field(..., description="Status: checked")
    conflicts: List[Dict[str, Any]] = Field(default_factory=list, description="Conflicting entities: {id, client_hash, server_hash}")
    missing_on_client: List[Dict[str, Any]] = Field(default_factory=list, description="Entities missing on client: {id, hash}")
    missing_on_server: List[Dict[str, Any]] = Field(default_factory=list, description="Entities missing on server: {id, hash}")


class FullStateResponse(BaseModel):
    """Response for full-state endpoint."""

    status: str = Field(..., description="Status: full_state")
    entity_type: str = Field(..., description="Entity type: tasks, users, groups")
    entities: List[Dict[str, Any]] = Field(default_factory=list, description="Full list of entities with their data")
    server_timestamp: datetime = Field(..., description="Server timestamp when state was generated")


class ConflictResolutionRequest(BaseModel):
    """Request for resolve-conflicts endpoint."""

    entity_type: str = Field(..., description="Entity type: tasks, users, groups")
    resolutions: List[Dict[str, Any]] = Field(..., description="List of conflict resolutions with client data")


class ConflictResolutionResponse(BaseModel):
    """Response for resolve-conflicts endpoint."""

    status: str = Field(..., description="Status: resolved")
    resolved_count: int = Field(..., description="Number of conflicts resolved")
    failed_count: int = Field(..., description="Number of conflicts that failed to resolve")
    details: List[Dict[str, Any]] = Field(default_factory=list, description="Resolution details")