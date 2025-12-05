"""API router for recurring tasks."""

from typing import TYPE_CHECKING, Any

from fastapi import APIRouter, Depends, HTTPException, Query, Request, status
import logging
from sqlalchemy.orm import Session

from backend.database import get_db
from backend.schemas.task import (
    TaskCreate,
    TaskResponse,
    TaskUpdate,
    TaskSyncRequest,
    TaskSyncOperation,
    TaskOperationType,
)
from backend.services.task_service import TaskRevisionConflictError, TaskService
from backend.routers.realtime import manager as ws_manager

if TYPE_CHECKING:
    pass

router = APIRouter()
logger = logging.getLogger("homeplanner.tasks")


def _resolve_selected_user_id(request: Request) -> int | None:
    """Extract the selected user identifier from cookie."""
    cookie_value = request.cookies.get("hp.selectedUserId")
    if not cookie_value:
        return None
    try:
        return int(cookie_value)
    except (TypeError, ValueError):
        return None


def broadcast_task_update(message: dict[str, Any]) -> None:
    """Broadcast a WebSocket message about a task update.
    
    This function safely handles async broadcasting from sync context.
    """
    # Try AnyIO safe path first (works from worker threads and sync contexts)
    try:
        import anyio
        logger.info("Broadcast via anyio.from_thread: %s", message)
        anyio.from_thread.run(ws_manager.broadcast, message)  # type: ignore[arg-type]
        return
    except Exception as e_anyio:
        logger.warning("Broadcast via anyio failed: %s, trying asyncio", e_anyio)
    
    # Fallbacks using asyncio
    try:
        import asyncio
        try:
            loop = asyncio.get_running_loop()
            # If we're in an async context, schedule the coroutine
            logger.info("Broadcast enqueue (running loop): %s", message)
            asyncio.create_task(ws_manager.broadcast(message))
        except RuntimeError:
            # No running loop, try to get or create one
            try:
                loop = asyncio.get_event_loop()
                if loop.is_running():
                    logger.info("Broadcast enqueue (running loop): %s", message)
                    asyncio.ensure_future(ws_manager.broadcast(message))
                else:
                    logger.info("Broadcast immediate (no loop): %s", message)
                    loop.run_until_complete(ws_manager.broadcast(message))
            except RuntimeError:
                # No event loop at all, create a new one
                logger.info("Broadcast creating new event loop: %s", message)
                loop = asyncio.new_event_loop()
                asyncio.set_event_loop(loop)
                try:
                    loop.run_until_complete(ws_manager.broadcast(message))
                finally:
                    loop.close()
    except Exception as e:
        logger.error(f"Failed to broadcast WS message: {e}", exc_info=True)


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
    db: Session = Depends(get_db),
) -> list[TaskResponse]:
    """Get tasks with optional filters.

    Supported filters:
    - days_ahead: предстоящие задачи за N дней
    - active_only: только активные
    
    Note: Для получения задач на сегодня используйте GET /tasks/today/ids
    """
    if days_ahead:
        tasks = TaskService.get_upcoming_tasks(db, days_ahead=days_ahead)
        return [TaskResponse.model_validate(task) for task in tasks]
    else:
        tasks = TaskService.get_all_tasks(db, active_only=active_only)
        return [TaskResponse.model_validate(task) for task in tasks]

@router.get("/today", response_model=list[TaskResponse])
def get_today_tasks_view(
    request: Request,
    db: Session = Depends(get_db),
) -> list[TaskResponse]:
    """Return tasks for the 'today' list filtered by selected user from cookie."""
    selected_user_id = _resolve_selected_user_id(request)
    if selected_user_id is None:
        return []
    
    tasks = TaskService.get_today_tasks(db, user_id=selected_user_id)
    return [TaskResponse.model_validate(task) for task in tasks]


@router.get("/today/ids", response_model=list[int])
def get_today_task_ids(
    request: Request,
    db: Session = Depends(get_db),
) -> list[int]:
    """Get identifiers of tasks visible in 'today' view.
    
    Returns only array of task IDs (not full objects).
    Cookie 'hp.selectedUserId' with current user ID is required.
    Returns only tasks assigned to the specified user.
    If cookie is missing or invalid, returns empty array [].
    """
    selected_user_id = _resolve_selected_user_id(request)
    if selected_user_id is None:
        return []
    
    return TaskService.get_today_task_ids(db, user_id=selected_user_id)


@router.post("/sync-queue", response_model=list[TaskResponse])
def sync_task_queue(
    payload: TaskSyncRequest,
    db: Session = Depends(get_db),
) -> list[TaskResponse]:
    """Apply a batch of task operations in chronological order and return current state.

    Ожидает массив операций (create/update/delete/complete/uncomplete) с timestamp.
    Операции сортируются по времени и применяются последовательно.
    Перед началом обработки выполняется возможный пересчёт задач по новому дню.
    """
    logger.info("HTTP sync-queue: %s operations", len(payload.operations))

    # Возможный пересчёт по новому дню единым местом
    if TaskService._is_new_day(db):
        TaskService.get_all_tasks(db, active_only=False)

    # Сортировка операций по timestamp (на случай, если клиент прислал неотсортированный список)
    operations: list[TaskSyncOperation] = sorted(
        payload.operations, key=lambda op: op.timestamp
    )

    for op in operations:
        try:
            if op.operation == TaskOperationType.CREATE:
                if op.payload is None:
                    continue
                create_data = TaskCreate.model_validate(op.payload)
                TaskService.create_task(db, create_data)
            elif op.operation == TaskOperationType.UPDATE:
                if op.task_id is None or op.payload is None:
                    continue
                update_data = TaskUpdate.model_validate(op.payload)
                TaskService.update_task(db, op.task_id, update_data)
            elif op.operation == TaskOperationType.DELETE:
                if op.task_id is None:
                    continue
                TaskService.delete_task(db, op.task_id)
            elif op.operation == TaskOperationType.COMPLETE:
                if op.task_id is None:
                    continue
                TaskService.complete_task(db, op.task_id)
            elif op.operation == TaskOperationType.UNCOMPLETE:
                if op.task_id is None:
                    continue
                TaskService.uncomplete_task(db, op.task_id)
        except Exception as exc:  # Логируем, но продолжаем остальные операции
            logger.error(
                "sync-queue: failed to apply op %s for task %s: %s",
                op.operation,
                op.task_id,
                exc,
            )

    # Возвращаем актуальное состояние задач после применения всех операций
    tasks = TaskService.get_all_tasks(db, active_only=False)
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

