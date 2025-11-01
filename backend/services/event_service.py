"""Service for event business logic."""

from typing import TYPE_CHECKING

from sqlalchemy.orm import Session

from backend.models.event import Event

if TYPE_CHECKING:
    from backend.schemas.event import EventCreate, EventUpdate


class EventService:
    """Service for managing one-time events."""

    @staticmethod
    def create_event(db: Session, event_data: "EventCreate") -> Event:
        """Create a new event."""
        event = Event(**event_data.model_dump())
        db.add(event)
        db.commit()
        db.refresh(event)
        return event

    @staticmethod
    def get_event(db: Session, event_id: int) -> Event | None:
        """Get an event by ID."""
        return db.query(Event).filter(Event.id == event_id).first()

    @staticmethod
    def get_all_events(db: Session, completed: bool | None = None) -> list[Event]:
        """Get all events, optionally filtering by completion status."""
        query = db.query(Event)
        if completed is not None:
            query = query.filter(Event.is_completed == completed)
        return query.order_by(Event.event_date).all()

    @staticmethod
    def update_event(db: Session, event_id: int, event_data: "EventUpdate") -> Event | None:
        """Update an event."""
        event = db.query(Event).filter(Event.id == event_id).first()
        if not event:
            return None

        update_data = event_data.model_dump(exclude_unset=True)
        for key, value in update_data.items():
            setattr(event, key, value)

        db.commit()
        db.refresh(event)
        return event

    @staticmethod
    def delete_event(db: Session, event_id: int) -> bool:
        """Delete an event."""
        event = db.query(Event).filter(Event.id == event_id).first()
        if not event:
            return False

        db.delete(event)
        db.commit()
        return True

    @staticmethod
    def complete_event(db: Session, event_id: int) -> Event | None:
        """Mark an event as completed."""
        event = db.query(Event).filter(Event.id == event_id).first()
        if not event:
            return None

        event.is_completed = True
        db.commit()
        db.refresh(event)
        return event

