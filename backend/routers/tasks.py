"""API router for recurring tasks."""

from typing import TYPE_CHECKING

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from backend.database import get_db
from backend.schemas.task import TaskCreate, TaskResponse, TaskUpdate
from backend.services.task_service import TaskService

if TYPE_CHECKING:
    pass

router = APIRouter()


@router.post("/", response_model=TaskResponse, status_code=status.HTTP_201_CREATED)
def create_task(
    task: TaskCreate,
    db: Session = Depends(get_db),
) -> TaskResponse:
    """Create a new recurring task."""
    created_task = TaskService.create_task(db, task)
    return TaskResponse.model_validate(created_task)


@router.get("/", response_model=list[TaskResponse])
def get_tasks(
    active_only: bool = Query(False, description="Filter only active tasks"),
    days_ahead: int | None = Query(None, ge=1, description="Get tasks due in next N days"),
    db: Session = Depends(get_db),
) -> list[TaskResponse]:
    """Get all tasks, optionally filtered by active status or upcoming dates."""
    if days_ahead:
        tasks = TaskService.get_upcoming_tasks(db, days_ahead=days_ahead)
    else:
        tasks = TaskService.get_all_tasks(db, active_only=active_only)
    return [TaskResponse.model_validate(task) for task in tasks]


@router.get("/{task_id}", response_model=TaskResponse)
def get_task(
    task_id: int,
    db: Session = Depends(get_db),
) -> TaskResponse:
    """Get a specific task by ID."""
    task = TaskService.get_task(db, task_id)
    if not task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task with id {task_id} not found",
        )
    return TaskResponse.model_validate(task)


@router.put("/{task_id}", response_model=TaskResponse)
def update_task(
    task_id: int,
    task_update: TaskUpdate,
    db: Session = Depends(get_db),
) -> TaskResponse:
    """Update a task."""
    updated_task = TaskService.update_task(db, task_id, task_update)
    if not updated_task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task with id {task_id} not found",
        )
    return TaskResponse.model_validate(updated_task)


@router.delete("/{task_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_task(
    task_id: int,
    db: Session = Depends(get_db),
) -> None:
    """Delete a task."""
    success = TaskService.delete_task(db, task_id)
    if not success:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task with id {task_id} not found",
        )


@router.post("/{task_id}/complete", response_model=TaskResponse)
def complete_task(
    task_id: int,
    db: Session = Depends(get_db),
) -> TaskResponse:
    """Mark a task as completed and update next due date."""
    completed_task = TaskService.complete_task(db, task_id)
    if not completed_task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task with id {task_id} not found",
        )
    return TaskResponse.model_validate(completed_task)

