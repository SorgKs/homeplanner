"""Service for task history business logic."""

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy.orm import Session

from backend.models.task_history import TaskHistory, TaskHistoryAction
from backend.services.time_manager import get_current_time

if TYPE_CHECKING:
    from backend.schemas.task_history import TaskHistoryCreate


class TaskHistoryService:
    """Service for managing task history."""

    @staticmethod
    def create_history_entry(db: Session, history_data: "TaskHistoryCreate") -> TaskHistory:
        """Create a new task history entry."""
        history = TaskHistory(**history_data.model_dump())
        db.add(history)
        db.commit()
        db.refresh(history)
        return history

    @staticmethod
    def get_task_history(db: Session, task_id: int) -> list[TaskHistory]:
        """Get all history entries for a specific task."""
        return (
            db.query(TaskHistory)
            .filter(TaskHistory.task_id == task_id)
            .order_by(TaskHistory.action_timestamp.desc())
            .all()
        )
    
    @staticmethod
    def get_all_history(db: Session) -> list[TaskHistory]:
        """Get all history entries (including deleted tasks where task_id is NULL)."""
        return (
            db.query(TaskHistory)
            .order_by(TaskHistory.action_timestamp.desc())
            .all()
        )

    @staticmethod
    def log_action(
        db: Session,
        task_id: int,
        action: TaskHistoryAction,
        iteration_date: datetime | None = None,
        metadata: str | None = None,
        comment: str | None = None,
    ) -> TaskHistory:
        """Log an action to task history."""
        import logging
        logger = logging.getLogger("homeplanner.task_history")

        try:
            history_data = {
                "task_id": task_id,
                "action": action,
                "action_timestamp": get_current_time(),
                "iteration_date": iteration_date,
                "meta_data": metadata,
                "comment": comment,
            }
            history = TaskHistory(**history_data)
            db.add(history)
            db.commit()
            db.refresh(history)
            return history
        except Exception as e:
            logger.error("Exception in log_action for task_id=%s, action=%s: %s", task_id, action, str(e), exc_info=True)
            raise

    @staticmethod
    def log_task_created(db: Session, task_id: int, creation_data: dict | None = None) -> TaskHistory:
        """Log task creation."""
        metadata = None
        if creation_data:
            import json
            from datetime import datetime
            # Custom JSON encoder for datetime
            def json_default(obj):
                if isinstance(obj, datetime):
                    return obj.isoformat()
                return str(obj)
            metadata = json.dumps(creation_data, default=json_default)
        return TaskHistoryService.log_action(db, task_id, TaskHistoryAction.CREATED, metadata=metadata)

    @staticmethod
    def log_task_confirmed(
        db: Session,
        task_id: int,
        iteration_date: datetime,
        metadata: dict | None = None,
    ) -> TaskHistory:
        """Log task confirmation."""
        meta_str = None
        if metadata:
            import json
            # Custom JSON encoder for datetime
            def json_default(obj):
                if isinstance(obj, datetime):
                    return obj.isoformat()
                return str(obj)
            meta_str = json.dumps(metadata, default=json_default)
        return TaskHistoryService.log_action(db, task_id, TaskHistoryAction.CONFIRMED, iteration_date, meta_str)

    @staticmethod
    def log_task_unconfirmed(
        db: Session,
        task_id: int,
        iteration_date: datetime,
    ) -> TaskHistory:
        """Log task unconfirmation."""
        return TaskHistoryService.log_action(db, task_id, TaskHistoryAction.UNCONFIRMED, iteration_date)

    @staticmethod
    def log_task_first_shown(
        db: Session,
        task_id: int,
        iteration_date: datetime,
    ) -> TaskHistory:
        """Log first time task iteration is shown."""
        return TaskHistoryService.log_action(db, task_id, TaskHistoryAction.FIRST_SHOWN, iteration_date)

    @staticmethod
    def delete_history_entry(db: Session, history_id: int) -> bool:
        """Delete a history entry."""
        history = db.query(TaskHistory).filter(TaskHistory.id == history_id).first()
        if not history:
            return False
        db.delete(history)
        db.commit()
        return True
