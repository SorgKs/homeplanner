"""API router for recurring tasks."""

from typing import TYPE_CHECKING, Any

from fastapi import APIRouter, Depends, HTTPException, Query, status
import logging
from sqlalchemy.orm import Session

from backend.database import get_db
from backend.schemas.task import TaskCreate, TaskResponse, TaskUpdate
from backend.services.task_service import TaskRevisionConflictError, TaskService
from backend.routers.realtime import manager as ws_manager

if TYPE_CHECKING:
    pass

router = APIRouter()
logger = logging.getLogger("homeplanner.tasks")


def broadcast_task_update(message: dict[str, Any]) -> None:
    """Broadcast a WebSocket message about a task update.
    
    This function safely handles async broadcasting from sync context.
    """
    # Try AnyIO safe path first (works from worker threads)
    try:
        import anyio
        logger.info("Broadcast via anyio.from_thread: %s", message)
        anyio.from_thread.run(ws_manager.broadcast, message)  # type: ignore[arg-type]
        return
    except Exception as e_anyio:
        logger.error("Broadcast via anyio failed: %s", e_anyio)
    # Fallbacks using asyncio (may fail in worker threads)
    try:
        import asyncio
        loop = asyncio.get_event_loop()
        if loop.is_running():
            logger.info("Broadcast enqueue (running loop): %s", message)
            asyncio.ensure_future(ws_manager.broadcast(message))
        else:
            logger.info("Broadcast immediate (no loop): %s", message)
            loop.run_until_complete(ws_manager.broadcast(message))
    except Exception as e:
        logging.error(f"Failed to broadcast WS message: {e}")


@router.post("/", response_model=TaskResponse, status_code=status.HTTP_201_CREATED)
def create_task(
    task: TaskCreate,
    db: Session = Depends(get_db),
) -> TaskResponse:
    """Create a new recurring task."""
    created_task = TaskService.create_task(db, task)
    # Broadcast creation event
    logger.info("HTTP create: task_id=%s", created_task.id)
    broadcast_task_update({
        "type": "task_update",
        "action": "created",
        "task_id": created_task.id,
        "task": TaskResponse.model_validate(created_task).model_dump(mode="json")
    })
    return TaskResponse.model_validate(created_task)


@router.get("/", response_model=list[TaskResponse])
def get_tasks(
    active_only: bool = Query(False, description="Filter only active tasks"),
    days_ahead: int | None = Query(None, ge=1, description="Get tasks due in next N days"),
    filter: str | None = Query(None, description="Predefined filter (e.g., 'today')"),
    db: Session = Depends(get_db),
) -> list[TaskResponse]:
    """Get tasks with optional predefined filters.

    Supported filters:
    - filter=today: серверная единая логика видимости «Сегодня»
    - days_ahead: предстоящие задачи за N дней
    - active_only: только активные
    """
    if filter == "today":
        tasks = TaskService.get_today_tasks(db)
    elif days_ahead:
        tasks = TaskService.get_upcoming_tasks(db, days_ahead=days_ahead)
    else:
        tasks = TaskService.get_all_tasks(db, active_only=active_only)
    return [TaskResponse.model_validate(task) for task in tasks]


@router.get("/today", response_model=list[TaskResponse])
def get_today_tasks(
    db: Session = Depends(get_db),
) -> list[TaskResponse]:
    """Get tasks visible in 'today' view.
    
    Returns tasks that are due today, overdue, or completed today.
    Logic matches frontend shouldBeVisibleInTodayView for consistency.
    This is the single source of truth for 'today' view filtering.
    """
    tasks = TaskService.get_today_tasks(db)
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
    try:
        updated_task = TaskService.update_task(db, task_id, task_update)
    except TaskRevisionConflictError as exc:
        server_payload = TaskResponse.model_validate(exc.task).model_dump(mode="json")
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail={
                "code": "conflict_revision",
                "message": "Конфликт ревизий. Обновите данные и повторите попытку.",
                "server_revision": exc.expected_revision,
                "server_payload": server_payload,
            },
        ) from exc
    except ValueError as exc:
        # Validation inside service may raise ValueError; convert to HTTP 422
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    except Exception as exc:  # fallback to avoid 500 for client CORS
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    if not updated_task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task with id {task_id} not found",
        )
    logger.info("HTTP update: task_id=%s", updated_task.id)
    broadcast_task_update({
        "type": "task_update",
        "action": "updated",
        "task_id": updated_task.id,
        "task": TaskResponse.model_validate(updated_task).model_dump(mode="json")
    })
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
    logger.info("HTTP delete: task_id=%s", task_id)
    broadcast_task_update({
        "type": "task_update",
        "action": "deleted",
        "task_id": task_id
    })


@router.post("/{task_id}/complete", response_model=TaskResponse)
def complete_task(
    task_id: int,
    db: Session = Depends(get_db),
) -> TaskResponse:
    """Отметить задачу выполненной и обновить состояние напоминания."""
    completed_task = TaskService.complete_task(db, task_id)
    if not completed_task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task with id {task_id} not found",
        )
    logger.info("HTTP complete: task_id=%s", completed_task.id)
    broadcast_task_update({
        "type": "task_update",
        "action": "completed",
        "task_id": completed_task.id,
        "task": TaskResponse.model_validate(completed_task).model_dump(mode="json")
    })
    return TaskResponse.model_validate(completed_task)


@router.post("/{task_id}/mark-shown", response_model=TaskResponse)
def mark_task_shown(
    task_id: int,
    db: Session = Depends(get_db),
) -> TaskResponse:
    """Mark task as shown for current iteration."""
    shown_task = TaskService.mark_task_shown(db, task_id)
    if not shown_task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task with id {task_id} not found",
        )
    logger.info("HTTP mark-shown: task_id=%s", shown_task.id)
    broadcast_task_update({
        "type": "task_update",
        "action": "shown",
        "task_id": shown_task.id,
        "task": TaskResponse.model_validate(shown_task).model_dump(mode="json")
    })
    return TaskResponse.model_validate(shown_task)


@router.post("/{task_id}/uncomplete", response_model=TaskResponse)
def uncomplete_task(
    task_id: int,
    db: Session = Depends(get_db),
) -> TaskResponse:
    """Cancel task confirmation (revert completion)."""
    uncompleted_task = TaskService.uncomplete_task(db, task_id)
    if not uncompleted_task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task with id {task_id} not found",
        )
    logger.info("HTTP uncomplete: task_id=%s", uncompleted_task.id)
    broadcast_task_update({
        "type": "task_update",
        "action": "uncompleted",
        "task_id": uncompleted_task.id,
        "task": TaskResponse.model_validate(uncompleted_task).model_dump(mode="json")
    })
    return TaskResponse.model_validate(uncompleted_task)

