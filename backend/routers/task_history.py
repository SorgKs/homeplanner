"""API router for task history."""

from datetime import datetime
from typing import TYPE_CHECKING

from fastapi import APIRouter, Body, Depends, HTTPException, status
from sqlalchemy.orm import Session

from backend.database import get_db
from backend.schemas.task_history import TaskHistoryResponse
from backend.services.task_history_service import TaskHistoryService

if TYPE_CHECKING:
    pass

router = APIRouter()


@router.get("/tasks/{task_id}/history", response_model=list[TaskHistoryResponse])
def get_task_history(
    task_id: int,
    db: Session = Depends(get_db),
) -> list[TaskHistoryResponse]:
    """Get all history entries for a specific task."""
    history = TaskHistoryService.get_task_history(db, task_id)
    return [TaskHistoryResponse.model_validate(entry) for entry in history]


@router.post("/tasks/{task_id}/first-shown", response_model=TaskHistoryResponse)
def log_task_first_shown(
    task_id: int,
    iteration_date: datetime = Body(...),
    db: Session = Depends(get_db),
) -> TaskHistoryResponse:
    """Log first time task iteration is shown."""
    entry = TaskHistoryService.log_task_first_shown(db, task_id, iteration_date)
    return TaskHistoryResponse.model_validate(entry)


@router.delete("/history/{history_id}")
def delete_history_entry(
    history_id: int,
    db: Session = Depends(get_db),
):
    """Delete a history entry."""
    TaskHistoryService.delete_history_entry(db, history_id)
    return {"status": "ok"}
