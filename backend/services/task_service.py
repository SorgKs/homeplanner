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
        from backend.models.task import RecurrenceType, TaskType
        
        task = Task(**task_data.model_dump())
        
        # Validate that all recurring tasks have reminder_time
        if (task.task_type == TaskType.RECURRING and 
            task.reminder_time is None):
            raise ValueError("reminder_time is required for recurring tasks (время обязательно для задач типа расписание)")
        
        db.add(task)
        db.commit()
        db.refresh(task)
        
        # Log creation to history
        from backend.services.task_history_service import TaskHistoryService
        import json
        # Custom JSON encoder for datetime
        def json_default(obj):
            if isinstance(obj, datetime):
                return obj.isoformat()
            return str(obj)
        metadata = json.dumps(task_data.model_dump(), default=json_default)
        
        # Generate comment with task settings
        task_settings = TaskService._format_task_settings(task.task_type, task)
        comment = task_settings
        
        from backend.models.task_history import TaskHistoryAction
        TaskHistoryService.log_action(db, task.id, TaskHistoryAction.CREATED, metadata=metadata, comment=comment)
        
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
        now = datetime.now()
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
                    task.reminder_time,
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
        
        # Save old task state for history comments (before applying changes)
        old_task_state = {
            "task_type": task.task_type,
            "reminder_time": task.reminder_time,
            "next_due_date": task.next_due_date,
            "recurrence_type": task.recurrence_type,
            "recurrence_interval": task.recurrence_interval,
            "interval_days": task.interval_days,
        }
        
        # Calculate changes and save old values
        changes = {}
        old_values = {}
        
        def compare_values(old_val, new_val) -> bool:
            """Compare two values, handling datetime properly."""
            # Handle None cases
            if old_val is None and new_val is None:
                return True
            if old_val is None or new_val is None:
                return False
            
            # Handle datetime comparison - compare as-is (local time)
            if isinstance(old_val, datetime) and isinstance(new_val, datetime):
                # Compare only date and time, ignore microseconds and timezone
                # Remove timezone info if present for comparison
                old_naive = old_val.replace(tzinfo=None) if old_val.tzinfo else old_val
                new_naive = new_val.replace(tzinfo=None) if new_val.tzinfo else new_val
                
                return (old_naive.replace(microsecond=0) == new_naive.replace(microsecond=0))
            
            # For other types, use standard comparison
            return old_val == new_val
        
        for key, new_value in update_data.items():
            old_value = getattr(task, key, None)
            # Compare values properly (handles datetime normalization)
            if not compare_values(old_value, new_value):
                changes[key] = new_value
                old_values[key] = old_value
        
        # Apply changes
        for key, value in update_data.items():
            setattr(task, key, value)
        
        # Validate that all recurring tasks have reminder_time
        from backend.models.task import TaskType
        if (task.task_type == TaskType.RECURRING and 
            task.reminder_time is None):
            raise ValueError("reminder_time is required for recurring tasks (время обязательно для задач типа расписание)")

        db.commit()
        db.refresh(task)
        
        # Log edit to history
        if changes:
            from backend.models.task_history import TaskHistoryAction
            from backend.services.task_history_service import TaskHistoryService
            import json
            # Custom JSON encoder for datetime
            def json_default(obj):
                if isinstance(obj, datetime):
                    return obj.isoformat()
                return str(obj)
            metadata = json.dumps({"old": old_values, "new": changes}, default=json_default)
            
            # Generate comment with human-readable changes
            def get_field_display_name(key: str) -> str:
                """Get human-readable field name."""
                field_names = {
                    "title": "название",
                    "description": "описание",
                    "task_type": "тип задачи",
                    "recurrence_type": "периодичность",
                    "recurrence_interval": "интервал повторения",
                    "interval_days": "интервал в днях",
                    "next_due_date": "дата выполнения",
                    "reminder_time": "напоминание",
                    "group_id": "группа",
                }
                return field_names.get(key, key)
            
            def format_value(val, key: str) -> str:
                """Format value for display."""
                if val is None:
                    return "не установлено"
                
                # Handle enums
                if key == "task_type":
                    from backend.models.task import TaskType
                    task_types = {
                        TaskType.ONE_TIME: "разовая задача",
                        TaskType.RECURRING: "расписание",
                        TaskType.INTERVAL: "интервальная задача",
                    }
                    if isinstance(val, str):
                        val = TaskType(val)
                    return task_types.get(val, str(val))
                
                if key == "recurrence_type":
                    from backend.models.task import RecurrenceType
                    recurrence_types = {
                        RecurrenceType.DAILY: "ежедневно",
                        RecurrenceType.WEEKDAYS: "будни",
                        RecurrenceType.WEEKENDS: "выходные",
                        RecurrenceType.WEEKLY: "еженедельно",
                        RecurrenceType.MONTHLY: "ежемесячно",
                        RecurrenceType.YEARLY: "ежегодно",
                        RecurrenceType.CUSTOM: "произвольно",
                        RecurrenceType.INTERVAL: "интервал",
                    }
                    if isinstance(val, str):
                        val = RecurrenceType(val)
                    return recurrence_types.get(val, str(val))
                
                # Handle datetime - format in human-readable way (local time)
                if isinstance(val, datetime):
                    return TaskService._format_datetime_for_history(val)
                
                return str(val)
            
            # Determine which fields are relevant based on final task type
            def is_field_relevant(key: str, final_task_type: str) -> bool:
                """Check if field is relevant for the final task type."""
                # Check current task type (after update)
                relevant_fields = {
                    "one_time": {"title", "description", "next_due_date", "reminder_time", "group_id"},
                    "recurring": {"title", "description", "task_type", "recurrence_type", "recurrence_interval", "next_due_date", "reminder_time", "group_id"},
                    "interval": {"title", "description", "task_type", "interval_days", "next_due_date", "reminder_time", "group_id"},
                }
                return key in relevant_fields.get(final_task_type, set())
            
            # Generate comment based on whether task_type changed
            comment = None
            
            if "task_type" in changes:
                # When task type changes, generate a complete description of new settings
                # Get old and new task types
                old_task_type = old_task_state.get("task_type", task.task_type)
                new_task_type = changes.get("task_type", task.task_type)
                
                # Format old and new settings
                old_settings = TaskService._format_task_settings(old_task_type, old_task_state)
                new_settings = TaskService._format_task_settings(new_task_type, task)
                
                comment = f"вместо '{old_settings}' теперь будет '{new_settings}'"
            
            else:
                # Regular field changes - always show full configuration description
                from backend.models.task import TaskType
                
                # Build old state dict with old values for formatting
                # Always use old_task_state for task_type to get correct old type
                old_state_for_formatting = {}
                # Get all relevant fields for the old task type
                relevant_fields = ["task_type", "recurrence_type", "recurrence_interval", "reminder_time", "next_due_date", "interval_days"]
                for key in relevant_fields:
                    if key == "task_type":
                        # Always use old task type from old_task_state
                        old_state_for_formatting[key] = old_task_state.get("task_type", task.task_type)
                    elif key in old_values:
                        # Field was changed, use old value
                        old_state_for_formatting[key] = old_values[key]
                    elif key in old_task_state:
                        # Field wasn't changed, use value from old_task_state
                        old_state_for_formatting[key] = old_task_state[key]
                    else:
                        # Fallback to current task value (shouldn't happen, but safety check)
                        old_state_for_formatting[key] = getattr(task, key, None)
                
                # Build new state from current task
                new_state_for_formatting = {
                    "task_type": task.task_type,
                    "recurrence_type": task.recurrence_type,
                    "recurrence_interval": task.recurrence_interval,
                    "reminder_time": task.reminder_time,
                    "next_due_date": task.next_due_date,
                    "interval_days": task.interval_days,
                }
                
                # Format old and new settings using full configuration description
                # Get task types from state dicts and convert TaskType enum to string value
                old_task_type_from_state = old_state_for_formatting.get("task_type", old_task_state.get("task_type", task.task_type))
                old_task_type_str = old_task_type_from_state.value if isinstance(old_task_type_from_state, TaskType) else str(old_task_type_from_state)
                
                new_task_type_from_state = new_state_for_formatting.get("task_type", task.task_type)
                new_task_type_str = new_task_type_from_state.value if isinstance(new_task_type_from_state, TaskType) else str(new_task_type_from_state)
                
                old_settings = TaskService._format_task_settings(old_task_type_str, old_state_for_formatting)
                new_settings = TaskService._format_task_settings(new_task_type_str, new_state_for_formatting)
                
                comment = f"вместо '{old_settings}' теперь будет '{new_settings}'"
            
            TaskHistoryService.log_action(
                db, task.id, TaskHistoryAction.EDITED, metadata=metadata, comment=comment
            )
        
        return task

    @staticmethod
    def delete_task(db: Session, task_id: int) -> bool:
        """Delete a task."""
        task = db.query(Task).filter(Task.id == task_id).first()
        if not task:
            return False

        # Log deletion to history before deleting
        from backend.models.task_history import TaskHistoryAction
        from backend.services.task_history_service import TaskHistoryService
        
        # Generate comment with task settings
        task_settings = TaskService._format_task_settings(task.task_type, task)
        comment = task_settings
        
        TaskHistoryService.log_action(
            db, task.id, TaskHistoryAction.DELETED, comment=comment
        )

        db.delete(task)
        db.commit()
        return True

    @staticmethod
    def complete_task(db: Session, task_id: int) -> Task | None:
        """Mark a task as completed and update next due date.
        
        If task is due today or overdue, the date is not updated immediately.
        Date will be updated only after midnight (next day) via get_all_tasks.
        """
        from backend.models.task import TaskType

        task = db.query(Task).filter(Task.id == task_id).first()
        if not task:
            return None

        task.last_completed_at = datetime.now()
        now = datetime.now()
        
        # Store iteration date for history logging
        iteration_date = task.next_due_date
        
        # Check if task is due today or overdue (within current day or before)
        task_date = task.next_due_date.replace(hour=0, minute=0, second=0, microsecond=0)
        today = now.replace(hour=0, minute=0, second=0, microsecond=0)
        is_due_today_or_overdue = task_date <= today

        # Calculate next due date based on task type
        if task.task_type == TaskType.ONE_TIME:
            # For one-time tasks: just mark as inactive (completed)
            # Don't change the date - keep it so it stays visible
            task.is_active = False
        elif task.task_type == TaskType.INTERVAL:
            # For interval tasks: don't update date immediately if task is due today or overdue
            # Date will be updated only after midnight (next day) via get_all_tasks
            # This ensures that completed tasks remain visible until next day
            if not is_due_today_or_overdue:
                # Task is due in the future (not today or overdue), update the date immediately
                if task.interval_days:
                    next_date = now + timedelta(days=task.interval_days)
                else:
                    next_date = now + timedelta(days=1)
                task.next_due_date = next_date
            # If due today or overdue, keep the date as is - it will be updated after midnight
            # The frontend will handle updating tasks at the start of a new day
        else:
            # For recurring tasks: don't update date immediately if task is due today or overdue
            # Date will be updated only after midnight (next day) via get_all_tasks
            # This ensures that completed tasks remain visible until next day
            if not is_due_today_or_overdue:
                # Task is due in the future (not today or overdue), update the date immediately
                next_date = TaskService._calculate_next_due_date(
                    task.next_due_date,
                    task.recurrence_type,
                    task.recurrence_interval,
                    task.reminder_time,
                )
                task.next_due_date = next_date
            # If due today or overdue, keep the date as is - it will be updated after midnight
            # The frontend will handle updating tasks at the start of a new day

        db.commit()
        db.refresh(task)
        
        # Log confirmation to history
        from backend.services.task_history_service import TaskHistoryService
        TaskHistoryService.log_task_confirmed(db, task.id, iteration_date)
        
        return task

    @staticmethod
    def _calculate_next_due_date(
        current_date: datetime,
        recurrence_type: RecurrenceType | None,
        interval: int,
        reminder_time: datetime | None = None,
    ) -> datetime:
        """Calculate next due date based on recurrence type and interval.
        
        For recurring tasks with reminder_time, respects the time of day and day of week.
        """
        from backend.models.task import RecurrenceType
        
        # For WEEKLY recurring tasks, reminder_time is required
        if recurrence_type == RecurrenceType.WEEKLY and not reminder_time:
            # For WEEKLY tasks, reminder_time is required to know day of week and time
            # If not provided, use next_due_date as fallback (should not happen in practice)
            raise ValueError("reminder_time is required for weekly recurring tasks (день недели и время обязательны для еженедельных задач)")
        
        if not reminder_time:
            # Old behavior without reminder_time (for other recurrence types)
            if recurrence_type == RecurrenceType.DAILY:
                return current_date + timedelta(days=interval)
            elif recurrence_type == RecurrenceType.MONTHLY:
                return current_date + timedelta(days=30 * interval)
            elif recurrence_type == RecurrenceType.YEARLY:
                return current_date + timedelta(days=365 * interval)
            else:
                return current_date + timedelta(days=interval)
        
        # New behavior with reminder_time
        reminder_hour = reminder_time.hour
        reminder_minute = reminder_time.minute
        
        if recurrence_type == RecurrenceType.DAILY:
            # Daily: same time every day
            next_date = current_date.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            # If time has passed today, schedule for tomorrow
            if next_date <= current_date:
                next_date += timedelta(days=interval)
            return next_date
        
        elif recurrence_type == RecurrenceType.WEEKDAYS:
            # Weekdays: Monday-Friday only
            next_date = current_date.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            # Move to next weekday
            days_to_add = 0
            while True:
                candidate = current_date + timedelta(days=days_to_add)
                weekday = candidate.weekday()  # Monday=0, Sunday=6
                # Check if it's a weekday (0-4 = Monday-Friday)
                if 0 <= weekday <= 4:
                    next_date = candidate.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
                    if next_date > current_date:
                        break
                days_to_add += 1
                if days_to_add > 10:  # Safety check
                    break
            return next_date
        
        elif recurrence_type == RecurrenceType.WEEKENDS:
            # Weekends: Saturday-Sunday only
            next_date = current_date.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            # Move to next weekend day
            days_to_add = 0
            while True:
                candidate = current_date + timedelta(days=days_to_add)
                weekday = candidate.weekday()  # Monday=0, Sunday=6
                # Check if it's a weekend (5=Saturday, 6=Sunday)
                if weekday in [5, 6]:
                    next_date = candidate.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
                    if next_date > current_date:
                        break
                days_to_add += 1
                if days_to_add > 10:  # Safety check
                    break
            return next_date
        
        elif recurrence_type == RecurrenceType.WEEKLY:
            # Weekly: same day of week and time
            reminder_weekday = reminder_time.weekday()  # Monday=0, Sunday=6
            # Find next occurrence of this weekday
            days_to_next = (reminder_weekday - current_date.weekday()) % 7
            # Calculate days to add based on interval
            if days_to_next == 0:
                # Same weekday - check if time has passed
                candidate = current_date.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
                if candidate <= current_date:
                    # Time has passed, schedule for next week
                    days_ahead = 7 * interval
                else:
                    # Time hasn't passed yet today
                    days_ahead = 0
            else:
                # Different weekday
                if interval == 1:
                    days_ahead = days_to_next
                else:
                    # interval > 1 means every N weeks
                    days_ahead = days_to_next + (interval - 1) * 7
            
            next_date = current_date + timedelta(days=days_ahead)
            next_date = next_date.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            return next_date
        
        elif recurrence_type == RecurrenceType.MONTHLY:
            # Monthly: same day of month and time
            reminder_day = reminder_time.day
            next_date = current_date.replace(day=reminder_day, hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            # If this month's occurrence has passed, move to next month(s)
            if next_date <= current_date:
                # Calculate how many months to add
                months_to_add = interval
                # Move forward by that many months
                from calendar import monthrange
                target_month = current_date.month + months_to_add
                target_year = current_date.year
                while target_month > 12:
                    target_month -= 12
                    target_year += 1
                
                # Create date in target month
                try:
                    next_date = current_date.replace(year=target_year, month=target_month, day=reminder_day)
                except ValueError:
                    # Month doesn't have that day, use last day
                    last_day = monthrange(target_year, target_month)[1]
                    next_date = current_date.replace(year=target_year, month=target_month, day=last_day)
                # Set the time
                next_date = next_date.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            return next_date
        
        elif recurrence_type == RecurrenceType.YEARLY:
            # Yearly: same month, day, and time
            reminder_month = reminder_time.month
            reminder_day = reminder_time.day
            next_date = current_date.replace(month=reminder_month, day=reminder_day, hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            # If this year's occurrence has passed, move forward by interval years
            if next_date <= current_date:
                next_date = next_date.replace(year=current_date.year + interval)
            return next_date
        
        else:
            # Default to daily
            return current_date + timedelta(days=interval)

    @staticmethod
    def _format_datetime_short(dt: datetime) -> str:
        """Format datetime as short readable string."""
        months = {
            1: "января", 2: "февраля", 3: "марта", 4: "апреля",
            5: "мая", 6: "июня", 7: "июля", 8: "августа",
            9: "сентября", 10: "октября", 11: "ноября", 12: "декабря"
        }
        return f"{dt.day} {months[dt.month]} в {dt.strftime('%H:%M')}"
    
    @staticmethod
    def _format_datetime_for_history(dt: datetime) -> str:
        """Format datetime for history comments.
        
        Formats datetime in human-readable way (no timezone conversion).
        """
        if dt is None:
            return "не установлено"
        
        # Format in human-readable way (same as _format_datetime_short)
        months = {
            1: "января", 2: "февраля", 3: "марта", 4: "апреля",
            5: "мая", 6: "июня", 7: "июля", 8: "августа",
            9: "сентября", 10: "октября", 11: "ноября", 12: "декабря"
        }
        return f"{dt.day} {months[dt.month]} {dt.year} в {dt.strftime('%H:%M')}"

    @staticmethod
    def _format_task_settings(task_type: str, task_obj: Task | dict) -> str:
        """Format complete task settings as human-readable string.
        Can work with Task object or dict of values.
        """
        from backend.models.task import RecurrenceType
        
        # Helper to get value from either Task or dict
        def get_val(key: str, default=None):
            if isinstance(task_obj, dict):
                return task_obj.get(key, default)
            else:
                return getattr(task_obj, key, default)
        
        if task_type == "one_time":
            reminder_time = get_val("reminder_time")
            next_due_date = get_val("next_due_date")
            if reminder_time:
                return TaskService._format_datetime_short(reminder_time)
            elif next_due_date:
                return TaskService._format_datetime_short(next_due_date)
            else:
                return "разовая задача"
        
        elif task_type == "recurring":
            recurrence_type = get_val("recurrence_type")
            recurrence_interval = get_val("recurrence_interval") or 1
            reminder_time = get_val("reminder_time")
            
            if recurrence_type == RecurrenceType.DAILY:
                # Use reminder_time if available, otherwise use next_due_date
                time_source = reminder_time if reminder_time else get_val("next_due_date")
                
                if time_source:
                    time_str = time_source.strftime("%H:%M")
                    if recurrence_interval == 1:
                        return f"в {time_str} каждый день"
                    else:
                        return f"в {time_str} каждые {recurrence_interval} дней"
                else:
                    # Fallback if neither reminder_time nor next_due_date is available
                    if recurrence_interval == 1:
                        return "ежедневно"
                    else:
                        return f"каждые {recurrence_interval} дней"
            
            elif recurrence_type == RecurrenceType.WEEKDAYS:
                if reminder_time:
                    time_str = reminder_time.strftime("%H:%M")
                    return f"в {time_str} по будням"
                else:
                    return "по будням"
            
            elif recurrence_type == RecurrenceType.WEEKENDS:
                if reminder_time:
                    time_str = reminder_time.strftime("%H:%M")
                    return f"в {time_str} по выходным"
                else:
                    return "по выходным"
            
            elif recurrence_type == RecurrenceType.WEEKLY:
                # For weekly tasks, always show day of week and time
                # Use reminder_time if available, otherwise use next_due_date
                time_source = reminder_time if reminder_time else get_val("next_due_date")
                
                if time_source:
                    time_str = time_source.strftime("%H:%M")
                    # Get day of week from time_source (Monday=0, Sunday=6)
                    weekday_num = time_source.weekday()
                    weekdays_ru = [
                        "понедельник",
                        "вторник",
                        "среда",
                        "четверг",
                        "пятница",
                        "суббота",
                        "воскресенье"
                    ]
                    weekday_ru = weekdays_ru[weekday_num]
                    if recurrence_interval == 1:
                        return f"каждый {weekday_ru} в {time_str}"
                    else:
                        # Format: "раз в 2 недели в пятница в 08:11"
                        return f"раз в {recurrence_interval} недели в {weekday_ru} в {time_str}"
                else:
                    # Fallback if neither reminder_time nor next_due_date is available
                    if recurrence_interval == 1:
                        return "еженедельно"
                    else:
                        return f"каждые {recurrence_interval} недели"
            
            elif recurrence_type == RecurrenceType.MONTHLY:
                if reminder_time:
                    time_str = reminder_time.strftime("%H:%M")
                    day_of_month = reminder_time.day
                    if recurrence_interval == 1:
                        return f"{day_of_month} числа в {time_str} ежемесячно"
                    else:
                        return f"{day_of_month} числа в {time_str} каждые {recurrence_interval} месяца"
                else:
                    if recurrence_interval == 1:
                        return "ежемесячно"
                    else:
                        return f"каждые {recurrence_interval} месяца"
            
            elif recurrence_type == RecurrenceType.YEARLY:
                if reminder_time:
                    time_str = reminder_time.strftime("%H:%M")
                    day_of_month = reminder_time.day
                    month_num = reminder_time.month
                    months_ru = {
                        1: "января", 2: "февраля", 3: "марта", 4: "апреля",
                        5: "мая", 6: "июня", 7: "июля", 8: "августа",
                        9: "сентября", 10: "октября", 11: "ноября", 12: "декабря"
                    }
                    month_ru = months_ru.get(month_num, f"{month_num} месяца")
                    if recurrence_interval == 1:
                        return f"{day_of_month} {month_ru} в {time_str} ежегодно"
                    else:
                        return f"{day_of_month} {month_ru} в {time_str} каждые {recurrence_interval} года"
                else:
                    if recurrence_interval == 1:
                        return "ежегодно"
                    else:
                        return f"каждые {recurrence_interval} года"
            
            else:
                return "расписание"
        
        elif task_type == "interval":
            interval_days = get_val("interval_days")
            if interval_days:
                if interval_days == 7:
                    return "раз в неделю"
                elif interval_days == 1:
                    return "раз в день"
                elif interval_days < 7:
                    return f"раз в {interval_days} дня"
                elif interval_days % 7 == 0:
                    weeks = interval_days // 7
                    return f"раз в {weeks} {'неделю' if weeks == 1 else 'недели'}"
                elif interval_days % 30 == 0:
                    months = interval_days // 30
                    return f"раз в {months} {'месяц' if months == 1 else 'месяца'}"
                else:
                    return f"раз в {interval_days} дней"
            else:
                return "интервальная задача"
        
        return "задача"

    @staticmethod
    def get_upcoming_tasks(db: Session, days_ahead: int = 7) -> list[Task]:
        """Get tasks due in the next N days."""
        cutoff_date = datetime.now() + timedelta(days=days_ahead)
        return (
            db.query(Task)
            .filter(Task.is_active == True)
            .filter(Task.next_due_date <= cutoff_date)
            .order_by(Task.next_due_date)
            .all()
        )

    @staticmethod
    def mark_task_shown(db: Session, task_id: int) -> Task | None:
        """Mark task as shown for current iteration."""
        task = db.query(Task).filter(Task.id == task_id).first()
        if not task:
            return None
        
        now = datetime.now()
        
        # Check if this is a new iteration (last_shown_at is before current next_due_date)
        is_first_show = task.last_shown_at is None or task.last_shown_at < task.next_due_date
        
        # Update last_shown_at
        task.last_shown_at = now
        db.commit()
        db.refresh(task)
        
        # Log first shown to history only if this is the first time showing this iteration
        if is_first_show:
            from backend.services.task_history_service import TaskHistoryService
            TaskHistoryService.log_task_first_shown(db, task.id, task.next_due_date)
        
        return task

