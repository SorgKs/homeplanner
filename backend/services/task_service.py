"""Service for task business logic."""

from datetime import datetime, timedelta
from typing import TYPE_CHECKING

from sqlalchemy.orm import Session

from backend.models.task import RecurrenceType, Task

if TYPE_CHECKING:
    from backend.schemas.task import TaskCreate, TaskUpdate


class TaskService:
    """Service for managing recurring tasks."""

    @staticmethod
    def create_task(db: Session, task_data: "TaskCreate") -> Task:
        """Create a new recurring task."""
        task = Task(**task_data.model_dump())
        db.add(task)
        db.commit()
        db.refresh(task)
        return task

    @staticmethod
    def get_task(db: Session, task_id: int) -> Task | None:
        """Get a task by ID."""
        return db.query(Task).filter(Task.id == task_id).first()

    @staticmethod
    def get_all_tasks(db: Session, active_only: bool = False) -> list[Task]:
        """Get all tasks, optionally filtering by active status.
        
        Also updates dates for completed tasks if they were completed before today
        and their date is in the past (i.e., after midnight).
        """
        from backend.models.task import TaskType
        
        # Update dates for tasks that were completed before today and have date in the past
        now = datetime.utcnow()
        today = now.replace(hour=0, minute=0, second=0, microsecond=0)
        
        # Find tasks that need date update:
        # - Have last_completed_at set (were completed)
        # - last_completed_at is before today (completed yesterday or earlier)
        # - next_due_date is before today (date is in the past)
        tasks_to_update = (
            db.query(Task)
            .filter(Task.last_completed_at.isnot(None))
            .filter(Task.last_completed_at < today)
            .filter(Task.next_due_date < today)
            .all()
        )
        
        for task in tasks_to_update:
            # Task date is in the past and task was completed before today
            # Update the date based on task type
            if task.task_type == TaskType.ONE_TIME:
                # One-time tasks stay as they are (already inactive)
                pass
            elif task.task_type == TaskType.INTERVAL:
                # Calculate next date from last completion + interval
                if task.interval_days:
                    # Calculate how many full cycles have passed since completion
                    last_completion_date = task.last_completed_at.replace(hour=0, minute=0, second=0, microsecond=0)
                    days_since_completion = (today - last_completion_date).days
                    # Calculate next cycle start date
                    cycles_to_add = (days_since_completion // task.interval_days) + 1
                    next_date = last_completion_date + timedelta(days=cycles_to_add * task.interval_days)
                    # Ensure it's at least today
                    if next_date < today:
                        next_date = today + timedelta(days=task.interval_days)
                    task.next_due_date = next_date
                else:
                    task.next_due_date = today + timedelta(days=1)
            else:
                # Recurring tasks: calculate next date from today
                task.next_due_date = TaskService._calculate_next_due_date(
                    today,
                    task.recurrence_type,
                    task.recurrence_interval,
                )
        
        if tasks_to_update:
            db.commit()
        
        # Now get all tasks (with updated dates)
        query = db.query(Task)
        if active_only:
            query = query.filter(Task.is_active == True)
        return query.order_by(Task.next_due_date).all()

    @staticmethod
    def update_task(db: Session, task_id: int, task_data: "TaskUpdate") -> Task | None:
        """Update a task."""
        task = db.query(Task).filter(Task.id == task_id).first()
        if not task:
            return None

        update_data = task_data.model_dump(exclude_unset=True)
        for key, value in update_data.items():
            setattr(task, key, value)

        db.commit()
        db.refresh(task)
        return task

    @staticmethod
    def delete_task(db: Session, task_id: int) -> bool:
        """Delete a task."""
        task = db.query(Task).filter(Task.id == task_id).first()
        if not task:
            return False

        db.delete(task)
        db.commit()
        return True

    @staticmethod
    def complete_task(db: Session, task_id: int) -> Task | None:
        """Mark a task as completed and update next due date.
        
        If task is due today, the date is not updated immediately.
        Date will be updated only after midnight (next day).
        """
        from backend.models.task import TaskType

        task = db.query(Task).filter(Task.id == task_id).first()
        if not task:
            return None

        task.last_completed_at = datetime.utcnow()
        now = datetime.utcnow()
        
        # Check if task is due today (within current day)
        task_date = task.next_due_date.replace(hour=0, minute=0, second=0, microsecond=0)
        today = now.replace(hour=0, minute=0, second=0, microsecond=0)
        is_due_today = task_date == today

        # Calculate next due date based on task type
        if task.task_type == TaskType.ONE_TIME:
            # For one-time tasks: just mark as inactive (completed)
            # Don't change the date - keep it for today so it stays visible
            task.is_active = False
        elif task.task_type == TaskType.INTERVAL:
            # For interval tasks: calculate next date but don't update if due today
            # Date will be updated only after midnight (next day)
            if not is_due_today:
                # Task is not due today, update the date immediately
                if task.interval_days:
                    next_date = now + timedelta(days=task.interval_days)
                else:
                    next_date = now + timedelta(days=1)
                task.next_due_date = next_date
            # If due today, keep the date as is - it will be updated after midnight
            # The frontend will handle updating tasks at the start of a new day
        else:
            # For recurring tasks: calculate next date but don't update if due today
            # Date will be updated only after midnight (next day)
            if not is_due_today:
                # Task is not due today, update the date immediately
                next_date = TaskService._calculate_next_due_date(
                    task.next_due_date,
                    task.recurrence_type,
                    task.recurrence_interval,
                )
                task.next_due_date = next_date
            # If due today, keep the date as is - it will be updated after midnight
            # The frontend will handle updating tasks at the start of a new day

        db.commit()
        db.refresh(task)
        return task

    @staticmethod
    def _calculate_next_due_date(
        current_date: datetime,
        recurrence_type: RecurrenceType | None,
        interval: int,
    ) -> datetime:
        """Calculate next due date based on recurrence type and interval."""
        if recurrence_type == RecurrenceType.DAILY:
            return current_date + timedelta(days=interval)
        elif recurrence_type == RecurrenceType.WEEKLY:
            return current_date + timedelta(weeks=interval)
        elif recurrence_type == RecurrenceType.MONTHLY:
            # Simple month addition (30 days approximation)
            return current_date + timedelta(days=30 * interval)
        elif recurrence_type == RecurrenceType.YEARLY:
            return current_date + timedelta(days=365 * interval)
        else:
            # Default to daily
            return current_date + timedelta(days=interval)

    @staticmethod
    def get_upcoming_tasks(db: Session, days_ahead: int = 7) -> list[Task]:
        """Get tasks due in the next N days."""
        cutoff_date = datetime.utcnow() + timedelta(days=days_ahead)
        return (
            db.query(Task)
            .filter(Task.is_active == True)
            .filter(Task.next_due_date <= cutoff_date)
            .order_by(Task.next_due_date)
            .all()
        )

