"""API router for time control operations."""

from datetime import datetime
from typing import Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session
from fastapi import Depends

from backend.database import get_db
from backend.services.time_manager import TimeManager
from backend.services.task_service import TaskService

router = APIRouter()


class TimeShiftRequest(BaseModel):
    """Request model for time shift operation."""
    days: int = 0
    hours: int = 0
    minutes: int = 0


class TimeSetRequest(BaseModel):
    """Request model for time set operation."""
    target_datetime: str  # ISO format datetime string


@router.get("/")
def get_time_state() -> dict:
    """Get the current time control state."""
    return TimeManager.get_state()


@router.post("/shift")
def shift_time(request: TimeShiftRequest, db: Session = Depends(get_db)) -> dict:
    """Shift virtual time by the specified amount."""
    TimeManager.shift_time(days=request.days, hours=request.hours, minutes=request.minutes)
    # Force recalculation of completed tasks to handle virtual time changes
    TaskService.get_all_tasks(db, enabled_only=False, force_recalc=True)
    return TimeManager.get_state()


@router.post("/set")
def set_time(request: TimeSetRequest, db: Session = Depends(get_db)) -> dict:
    """Set virtual time to a specific datetime."""
    try:
        target_dt = datetime.fromisoformat(request.target_datetime.replace('Z', '+00:00'))
        TimeManager.set_time(target_dt)
        # Force recalculation of completed tasks to handle virtual time changes
        TaskService.get_all_tasks(db, enabled_only=False, force_recalc=True)
        return TimeManager.get_state()
    except ValueError as e:
        raise HTTPException(status_code=400, detail=f"Invalid datetime format: {e}")


@router.post("/reset")
def reset_time() -> dict:
    """Reset to real time."""
    TimeManager.reset_time()
    return TimeManager.get_state()