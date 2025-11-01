"""API router for events."""

from typing import TYPE_CHECKING

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from backend.database import get_db
from backend.schemas.event import EventCreate, EventResponse, EventUpdate
from backend.services.event_service import EventService

if TYPE_CHECKING:
    pass

router = APIRouter()


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

