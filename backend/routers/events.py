"""API router for events and synchronization."""

from typing import TYPE_CHECKING, List

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from backend.database import get_db
from backend.schemas.event import EventCreate, EventResponse, EventUpdate
from backend.schemas.sync import (
    SyncEvent,
    SyncEventResponse,
    HashVerificationRequest,
    HashVerificationResponse,
    ConflictResolutionRequest,
    ConflictResolutionResponse,
    ApplyResolvedDataRequest,
    ApplyResolvedDataResponse,
)
from backend.services.event_service import EventService
from backend.services.sync_event_service import SyncEventService

if TYPE_CHECKING:
    pass

router = APIRouter()


# Legacy one-time events endpoints
@router.post("/", response_model=EventResponse, status_code=status.HTTP_201_CREATED)
def create_event(
    event: EventCreate,
    db: Session = Depends(get_db),
) -> EventResponse:
    """Create a new one-time event."""
    created_event = EventService.create_event(db, event)
    return EventResponse.model_validate(created_event)


@router.get("/", response_model=list[EventResponse])
def get_events(
    completed: bool | None = None,
    db: Session = Depends(get_db),
) -> list[EventResponse]:
    """Get all events, optionally filtered by completion status."""
    events = EventService.get_all_events(db, completed=completed)
    return [EventResponse.model_validate(event) for event in events]


@router.get("/{event_id}", response_model=EventResponse)
def get_event(
    event_id: int,
    db: Session = Depends(get_db),
) -> EventResponse:
    """Get a specific event by ID."""
    event = EventService.get_event(db, event_id)
    if not event:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Event with id {event_id} not found",
        )
    return EventResponse.model_validate(event)


@router.put("/{event_id}", response_model=EventResponse)
def update_event(
    event_id: int,
    event_update: EventUpdate,
    db: Session = Depends(get_db),
) -> EventResponse:
    """Update an event."""
    updated_event = EventService.update_event(db, event_id, event_update)
    if not updated_event:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Event with id {event_id} not found",
        )
    return EventResponse.model_validate(updated_event)


@router.delete("/{event_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_event(
    event_id: int,
    db: Session = Depends(get_db),
) -> None:
    """Delete an event."""
    success = EventService.delete_event(db, event_id)
    if not success:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Event with id {event_id} not found",
        )


@router.post("/{event_id}/complete", response_model=EventResponse)
def complete_event(
    event_id: int,
    db: Session = Depends(get_db),
) -> EventResponse:
    """Mark an event as completed."""
    completed_event = EventService.complete_event(db, event_id)
    if not completed_event:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Event with id {event_id} not found",
        )
    return EventResponse.model_validate(completed_event)


# New synchronization endpoints
@router.post("/sync", response_model=SyncEventResponse)
def process_sync_event(
    event: SyncEvent,
    db: Session = Depends(get_db),
) -> SyncEventResponse:
    """Process a synchronization event from client."""
    try:
        if event.entity_type == 'task':
            result = SyncEventService.process_task_change_event(
                db, event.event_type, event.entity_id, event.changes or {}, event.client_hash, event.timestamp
            )
            return SyncEventResponse(
                status=result['status'],
                entity_type='task',
                entity_id=result.get('task_id'),
                server_hash=result.get('server_hash'),
                message=result.get('message')
            )

        elif event.entity_type == 'user':
            result = SyncEventService.process_user_change_event(
                db, event.event_type, event.entity_id, event.changes or {}, event.client_hash
            )
            return SyncEventResponse(
                status=result['status'],
                entity_type='user',
                entity_id=result.get('user_id'),
                server_hash=result.get('server_hash'),
                message=result.get('message')
            )

        elif event.entity_type == 'group':
            result = SyncEventService.process_group_change_event(
                db, event.event_type, event.entity_id, event.changes or {}, event.client_hash
            )
            return SyncEventResponse(
                status=result['status'],
                entity_type='group',
                entity_id=result.get('group_id'),
                server_hash=result.get('server_hash'),
                message=result.get('message')
            )

        else:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Unknown entity type: {event.entity_type}"
            )

    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Error processing sync event: {str(e)}"
        )


@router.post("/verify-hashes", response_model=HashVerificationResponse)
def verify_hashes(
    request: HashVerificationRequest,
    db: Session = Depends(get_db),
) -> HashVerificationResponse:
    """Verify client hashes against server hashes for periodic sync."""
    try:
        result = SyncEventService.verify_hashes(db, request.entity_type, request.hashes)
        return HashVerificationResponse.model_validate(result)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e)
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Error verifying hashes: {str(e)}"
        )


@router.post("/resolve-conflicts", response_model=ConflictResolutionResponse)
def resolve_conflicts(
    request: ConflictResolutionRequest,
    db: Session = Depends(get_db),
) -> ConflictResolutionResponse:
    """Resolve conflicts detected during hash verification."""
    try:
        result = SyncEventService.resolve_conflicts(db, request.entity_type, request.resolutions)
        return ConflictResolutionResponse.model_validate(result)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e)
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Error resolving conflicts: {str(e)}"
        )


@router.post("/apply-resolved", response_model=ApplyResolvedDataResponse)
def apply_resolved_data(
    request: ApplyResolvedDataRequest,
    db: Session = Depends(get_db),
) -> ApplyResolvedDataResponse:
    """Apply resolved data after conflict resolution."""
    try:
        # For now, this is a placeholder - actual implementation would depend on
        # how resolved data is structured
        applied = []
        failed = []

        for entity_data in request.resolved_data:
            try:
                if request.entity_type == 'task':
                    # Apply task data
                    task_id = entity_data.get('id')
                    if task_id:
                        # Update task with resolved data
                        # This would need proper implementation based on the data structure
                        applied.append(task_id)
                    else:
                        failed.append({'data': entity_data, 'error': 'Missing task ID'})
                elif request.entity_type == 'user':
                    # Apply user data
                    user_id = entity_data.get('id')
                    if user_id:
                        applied.append(user_id)
                    else:
                        failed.append({'data': entity_data, 'error': 'Missing user ID'})
                elif request.entity_type == 'group':
                    # Apply group data
                    group_id = entity_data.get('id')
                    if group_id:
                        applied.append(group_id)
                    else:
                        failed.append({'data': entity_data, 'error': 'Missing group ID'})
                else:
                    failed.append({'data': entity_data, 'error': f'Unknown entity type: {request.entity_type}'})
            except Exception as e:
                failed.append({'data': entity_data, 'error': str(e)})

        return ApplyResolvedDataResponse(
            status='applied',
            applied=applied,
            failed=failed
        )

    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Error applying resolved data: {str(e)}"
        )

