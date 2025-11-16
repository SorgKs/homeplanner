"""Router that exposes endpoints for controlling application time."""

from datetime import datetime, timedelta

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field, ConfigDict, model_validator, field_serializer

from backend.services.time_manager import (
    get_time_state,
    reset_time_override,
    set_current_time,
    shift_time,
)


router = APIRouter()


class TimeStateResponse(BaseModel):
    """Information about current time override state."""

    real_now: datetime = Field(description="Actual system time")
    virtual_now: datetime = Field(description="Effective application time")
    override_enabled: bool = Field(description="Flag indicating if override is active")
    offset_seconds: float | None = Field(
        default=None, description="Offset between virtual and real times in seconds"
    )

    @field_serializer("real_now", "virtual_now", when_used="always")
    def _serialize_dt(self, value: datetime) -> str:
        """Serialize datetime to ISO 8601 without timezone."""
        if value.tzinfo is not None:
            value = value.replace(tzinfo=None)
        return value.isoformat()


class TimeShiftRequest(BaseModel):
    """Payload for shifting current time."""

    days: int = Field(0, description="Days delta (can be negative)")
    hours: int = Field(0, description="Hours delta (can be negative)")
    minutes: int = Field(0, description="Minutes delta (can be negative)")

    @model_validator(mode="after")
    def ensure_non_zero(self) -> "TimeShiftRequest":
        """Ensure that at least one component is non-zero."""
        if self.days == 0 and self.hours == 0 and self.minutes == 0:
            raise ValueError("At least one shift value must be non-zero")
        return self


class TimeSetRequest(BaseModel):
    """Payload for setting absolute application time."""

    target_datetime: datetime = Field(description="New current time (local)")


def _serialize_state() -> TimeStateResponse:
    return TimeStateResponse(**get_time_state())


@router.get("/", response_model=TimeStateResponse)
def read_time_state() -> TimeStateResponse:
    """Return current time state."""
    return _serialize_state()


@router.post("/shift", response_model=TimeStateResponse)
def shift_time_endpoint(payload: TimeShiftRequest) -> TimeStateResponse:
    """Shift application time by provided delta."""
    delta = timedelta(days=payload.days, hours=payload.hours, minutes=payload.minutes)
    if delta == timedelta():
        raise HTTPException(status_code=400, detail="Shift delta cannot be zero")
    shift_time(delta)
    return _serialize_state()


@router.post("/set", response_model=TimeStateResponse)
def set_time_endpoint(payload: TimeSetRequest) -> TimeStateResponse:
    """Set absolute application time."""
    # Normalize to minute precision (seconds and microseconds to zero)
    normalized = payload.target_datetime.replace(second=0, microsecond=0)
    set_current_time(normalized)
    # Compose response explicitly to avoid transient microsecond drift
    state = get_time_state()
    return TimeStateResponse(
        real_now=state["real_now"],
        virtual_now=normalized,
        override_enabled=state["override_enabled"],
        offset_seconds=state["offset_seconds"],
    )


@router.post("/reset", response_model=TimeStateResponse)
def reset_time_endpoint() -> TimeStateResponse:
    """Reset override and use real system time."""
    reset_time_override()
    return _serialize_state()


