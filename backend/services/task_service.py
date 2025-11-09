"""Service for task business logic."""

from datetime import datetime, timedelta
from typing import TYPE_CHECKING

from sqlalchemy.orm import Session

from backend.models.task import RecurrenceType, Task, TaskNotification, TaskRecurrence

if TYPE_CHECKING:
    from backend.schemas.task import TaskCreate, TaskUpdate


class TaskRevisionConflictError(Exception):
    """Raised when optimistic lock revision mismatch occurs."""

    def __init__(self, expected_revision: int, actual_revision: int, task: Task) -> None:
        """Store revision details and server payload."""

        self.expected_revision = expected_revision
        self.actual_revision = actual_revision
        self.task = task
        message = (
            f"Revision conflict: expected {expected_revision}, got {actual_revision}"
        )
        super().__init__(message)


class TaskService:
    """Service for managing recurring tasks."""

    @staticmethod
    def create_task(db: Session, task_data: "TaskCreate") -> Task:
        """Create a new recurring task."""
        from backend.models.task import TaskType

        payload = task_data.model_dump()
        recurrence_payload = payload.pop("recurrence", None)
        notifications_payload = payload.pop("notifications", [])

        task = Task(**payload)

        if recurrence_payload:
            task.recurrence = TaskRecurrence(
                rrule=recurrence_payload["rrule"],
                end_at=recurrence_payload.get("end_at"),
            )

        if notifications_payload:
            task.notifications = [
                TaskNotification(
                    notification_type=notification["notification_type"],
                    channel=notification["channel"],
                    offset_minutes=notification["offset_minutes"],
                )
                for notification in notifications_payload
            ]

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
        """Получить все задачи с опциональной фильтрацией по активности."""
        query = db.query(Task)
        if active_only:
            query = query.filter(Task.active.is_(True))
        return query.order_by(Task.reminder_time).all()

    @staticmethod
    def get_tasks_for_today(db: Session) -> list[Task]:
        """Совместимость: обёртка над get_today_tasks()."""

        return TaskService.get_today_tasks(db)

    @staticmethod
    def update_task(db: Session, task_id: int, task_data: "TaskUpdate") -> Task | None:
        """Update a task."""
        task = db.query(Task).filter(Task.id == task_id).first()
        if not task:
            return None

        if task.revision != task_data.revision:
            raise TaskRevisionConflictError(
                expected_revision=task.revision,
                actual_revision=task_data.revision,
                task=task,
            )

        update_data = task_data.model_dump(exclude_unset=True)
        update_data.pop("revision", None)
        recurrence_payload = update_data.pop("recurrence", None)
        notifications_payload = update_data.pop("notifications", None)

        # Save old task state for history comments (before applying changes)
        old_task_state = {
            "task_type": task.task_type,
            "reminder_time": task.reminder_time,
            "recurrence_type": task.recurrence_type,
            "recurrence_interval": task.recurrence_interval,
            "interval_days": task.interval_days,
            "completed": task.completed,
            "active": task.active,
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

        if recurrence_payload is not None:
            if recurrence_payload:
                if task.recurrence is None:
                    task.recurrence = TaskRecurrence(
                        rrule=recurrence_payload["rrule"],
                        end_at=recurrence_payload.get("end_at"),
                    )
                else:
                    task.recurrence.rrule = recurrence_payload["rrule"]
                    task.recurrence.end_at = recurrence_payload.get("end_at")
            else:
                task.recurrence = None

        if notifications_payload is not None:
            task.notifications = [
                TaskNotification(
                    notification_type=notification["notification_type"],
                    channel=notification["channel"],
                    offset_minutes=notification["offset_minutes"],
                )
                for notification in notifications_payload
            ]

        task.revision += 1

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
                    "reminder_time": "напоминание",
                    "group_id": "группа",
                    "completed": "выполнено сегодня",
                    "active": "активность",
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
                relevant_fields = [
                    "task_type",
                    "recurrence_type",
                    "recurrence_interval",
                    "reminder_time",
                    "interval_days",
                    "completed",
                    "active",
                ]
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
                    "interval_days": task.interval_days,
                    "completed": task.completed,
                    "active": task.active,
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
        # Save task_id and task data before deletion (for cascade protection)
        saved_task_id = task.id
        saved_task_title = task.title
        
        from backend.models.task_history import TaskHistoryAction
        import json
        
        # Generate comment with task settings
        task_settings = TaskService._format_task_settings(task.task_type, task)
        comment = task_settings
        
        # Create metadata with task info (including title) to preserve it after deletion
        metadata = {
            "task_id": saved_task_id,
            "task_title": saved_task_title,
            "task_type": task.task_type.value if hasattr(task.task_type, 'value') else str(task.task_type),
        }
        metadata_json = json.dumps(metadata)
        
        # Create history entry manually to ensure it's saved with task_id before deletion
        from backend.models.task_history import TaskHistory
        history_entry = TaskHistory(
            task_id=saved_task_id,
            action=TaskHistoryAction.DELETED,
            action_timestamp=datetime.now(),
            comment=comment,
            meta_data=metadata_json
        )
        db.add(history_entry)
        # Commit history entry first to ensure it's saved in database before deletion
        db.commit()
        
        # Now delete the task
        # After deletion, task_id will become NULL due to cascade, but the history entry
        # is already saved in the database, so it won't be affected
        db.delete(task)
        db.commit()  # Commit deletion
        return True

    @staticmethod
    def complete_task(db: Session, task_id: int) -> Task | None:
        """Отметить задачу выполненной и при необходимости пересчитать напоминание."""
        from backend.models.task import TaskType

        task = db.query(Task).filter(Task.id == task_id).first()
        if not task:
            return None

        task.completed = True
        now = datetime.now()
        iteration_time = task.reminder_time

        today_start = now.replace(hour=0, minute=0, second=0, microsecond=0)
        iteration_day = iteration_time.replace(hour=0, minute=0, second=0, microsecond=0)
        is_due_today_or_overdue = iteration_day <= today_start

        if task.task_type == TaskType.ONE_TIME:
            task.active = False
        elif task.task_type == TaskType.INTERVAL:
            if not is_due_today_or_overdue:
                interval = task.interval_days or 1
                base = iteration_time if iteration_time > now else now
                task.reminder_time = base + timedelta(days=interval)
        else:
            if not is_due_today_or_overdue:
                interval = task.recurrence_interval or 1
                task.reminder_time = TaskService._calculate_next_reminder_time(
                    iteration_time,
                    task.recurrence_type,
                    interval,
                    task.reminder_time,
                )

        task.revision += 1

        db.commit()
        db.refresh(task)
        
        from backend.services.task_history_service import TaskHistoryService
        TaskHistoryService.log_task_confirmed(db, task.id, iteration_time)
        
        return task

    @staticmethod
    def uncomplete_task(db: Session, task_id: int) -> Task | None:
        """Отменить выполнение задачи и восстановить предыдущее время напоминания."""
        from backend.models.task import TaskType
        from backend.models.task_history import TaskHistory, TaskHistoryAction

        task = db.query(Task).filter(Task.id == task_id).first()
        if not task:
            return None

        last_confirmed = (
            db.query(TaskHistory)
            .filter(TaskHistory.task_id == task_id)
            .filter(TaskHistory.action == TaskHistoryAction.CONFIRMED)
            .order_by(TaskHistory.action_timestamp.desc())
            .first()
        )
        
        task.completed = False
        
        if last_confirmed and last_confirmed.iteration_date:
            task.reminder_time = last_confirmed.iteration_date

        if task.task_type == TaskType.ONE_TIME:
            task.active = True

        task.revision += 1

        db.commit()
        db.refresh(task)

        from backend.services.task_history_service import TaskHistoryService
        TaskHistoryService.log_task_unconfirmed(db, task.id, task.reminder_time)

        return task

    @staticmethod
    def _find_nth_weekday_in_month(year: int, month: int, weekday: int, n: int) -> datetime:
        """Find the N-th occurrence of a weekday in a given month.
        
        Args:
            year: Year
            month: Month (1-12)
            weekday: Day of week (0=Monday, 6=Sunday)
            n: Which occurrence (1=first, 2=second, 3=third, 4=fourth, -1=last)
        
        Returns:
            datetime object for the N-th weekday in the month
        """
        from calendar import monthrange
        
        # Get first day of month
        first_day = datetime(year, month, 1)
        # Find first occurrence of weekday in month
        first_weekday = first_day.weekday()
        days_to_first = (weekday - first_weekday) % 7
        if days_to_first < 0:
            days_to_first += 7
        
        if n == -1:
            # Find last occurrence: go to last day and work backwards
            last_day = monthrange(year, month)[1]
            last_date = datetime(year, month, last_day)
            last_weekday = last_date.weekday()
            days_from_last = (last_weekday - weekday) % 7
            if days_from_last < 0:
                days_from_last += 7
            result = last_date - timedelta(days=days_from_last)
            # Ensure result is still in the same month
            if result.month != month:
                result = result - timedelta(days=7)
        else:
            # Find N-th occurrence
            result = first_day + timedelta(days=days_to_first + (n - 1) * 7)
            # Check if date is still in the same month
            if result.month != month:
                # This means we're trying to get a 5th occurrence, which doesn't exist
                # Fall back to last occurrence
                last_day = monthrange(year, month)[1]
                last_date = datetime(year, month, last_day)
                last_weekday = last_date.weekday()
                days_from_last = (last_weekday - weekday) % 7
                if days_from_last < 0:
                    days_from_last += 7
                result = last_date - timedelta(days=days_from_last)
                if result.month != month:
                    result = result - timedelta(days=7)
        
        return result
    
    @staticmethod
    def _determine_weekday_occurrence(day_of_month: int, weekday: int, year: int, month: int) -> int:
        """Determine which occurrence (1-4 or -1 for last) a day represents.
        
        Args:
            day_of_month: Day of month (1-31)
            weekday: Day of week (0=Monday, 6=Sunday)
            year: Year
            month: Month (1-12)
        
        Returns:
            Occurrence number (1, 2, 3, 4, or -1 for last)
        """
        from calendar import monthrange
        
        # Check if this is the last occurrence of this weekday in the month
        last_day = monthrange(year, month)[1]
        last_date = datetime(year, month, last_day)
        last_weekday = last_date.weekday()
        days_from_last = (last_weekday - weekday) % 7
        if days_from_last < 0:
            days_from_last += 7
        last_occurrence_date = last_date - timedelta(days=days_from_last)
        if last_occurrence_date.month != month:
            last_occurrence_date = last_occurrence_date - timedelta(days=7)
        
        # If this is the last occurrence
        target_date = datetime(year, month, day_of_month)
        if target_date.day == last_occurrence_date.day:
            return -1
        
        # Otherwise, calculate which occurrence (1-4)
        first_day = datetime(year, month, 1)
        first_weekday = first_day.weekday()
        days_to_first = (weekday - first_weekday) % 7
        if days_to_first < 0:
            days_to_first += 7
        
        first_occurrence = first_day + timedelta(days=days_to_first)
        if first_occurrence.month != month:
            # This shouldn't happen, but handle it
            return 1
        
        # Calculate which occurrence
        days_diff = (target_date - first_occurrence).days
        occurrence = (days_diff // 7) + 1
        
        # Cap at 4 (we already checked if it's last)
        return min(occurrence, 4)
    
    @staticmethod
    def _calculate_next_reminder_time(
        current_date: datetime,
        recurrence_type: RecurrenceType | None,
        interval: int,
        reminder_time: datetime | None = None,
    ) -> datetime:
        """Рассчитать следующее время напоминания исходя из правил повторяемости."""
        from backend.models.task import RecurrenceType
        
        # For WEEKLY recurring tasks, reminder_time is required
        if recurrence_type == RecurrenceType.WEEKLY and not reminder_time:
            # For WEEKLY tasks, reminder_time is required to know day of week and time
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
            # Weekdays: Monday-Friday only, with interval (treat like daily)
            # Find next N weekdays (where N = interval)
            next_date = current_date.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            weekday_count = 0
            days_to_add = 0
            
            while weekday_count < interval:
                candidate = current_date + timedelta(days=days_to_add)
                weekday = candidate.weekday()  # Monday=0, Sunday=6
                # Check if it's a weekday (0-4 = Monday-Friday)
                if 0 <= weekday <= 4:
                    next_date = candidate.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
                    if next_date > current_date:
                        weekday_count += 1
                        if weekday_count >= interval:
                            break
                days_to_add += 1
                if days_to_add > 50:  # Safety check (enough for several weeks)
                    break
            return next_date
        
        elif recurrence_type == RecurrenceType.WEEKENDS:
            # Weekends: Saturday-Sunday only, with interval (treat like daily)
            # Find next N weekend days (where N = interval)
            next_date = current_date.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            weekend_count = 0
            days_to_add = 0
            
            while weekend_count < interval:
                candidate = current_date + timedelta(days=days_to_add)
                weekday = candidate.weekday()  # Monday=0, Sunday=6
                # Check if it's a weekend (5=Saturday, 6=Sunday)
                if weekday in [5, 6]:
                    next_date = candidate.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
                    if next_date > current_date:
                        weekend_count += 1
                        if weekend_count >= interval:
                            break
                days_to_add += 1
                if days_to_add > 50:  # Safety check (enough for several weeks)
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
            if reminder_time:
                return TaskService._format_datetime_short(reminder_time)
            else:
                return "разовая задача"
        
        elif task_type == "recurring":
            recurrence_type = get_val("recurrence_type")
            recurrence_interval = get_val("recurrence_interval") or 1
            reminder_time = get_val("reminder_time")
            
            if recurrence_type == RecurrenceType.DAILY:
                if reminder_time:
                    time_str = reminder_time.strftime("%H:%M")
                    if recurrence_interval == 1:
                        return f"в {time_str} каждый день"
                    else:
                        return f"в {time_str} каждые {recurrence_interval} дней"
                else:
                    if recurrence_interval == 1:
                        return "ежедневно"
                    else:
                        return f"каждые {recurrence_interval} дней"
            
            elif recurrence_type == RecurrenceType.WEEKDAYS:
                if reminder_time:
                    time_str = reminder_time.strftime("%H:%M")
                    if recurrence_interval == 1:
                        return f"в {time_str} по будням"
                    else:
                        return f"в {time_str} каждые {recurrence_interval} будних дня"
                else:
                    if recurrence_interval == 1:
                        return "по будням"
                    else:
                        return f"каждые {recurrence_interval} будних дня"
            
            elif recurrence_type == RecurrenceType.WEEKENDS:
                if reminder_time:
                    time_str = reminder_time.strftime("%H:%M")
                    if recurrence_interval == 1:
                        return f"в {time_str} по выходным"
                    else:
                        return f"в {time_str} каждые {recurrence_interval} выходных дня"
                else:
                    if recurrence_interval == 1:
                        return "по выходным"
                    else:
                        return f"каждые {recurrence_interval} выходных дня"
            
            elif recurrence_type == RecurrenceType.WEEKLY:
                # For weekly tasks, always show day of week and time
                if reminder_time:
                    time_str = reminder_time.strftime("%H:%M")
                    # Get day of week from reminder_time (Monday=0, Sunday=6)
                    weekday_num = reminder_time.weekday()
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
            reminder_time = get_val("reminder_time")
            time_str = ""
            if reminder_time:
                time_str = f" в {reminder_time.strftime('%H:%M')}"
            
            if interval_days:
                if interval_days == 7:
                    return f"раз в неделю{time_str}"
                elif interval_days == 1:
                    return f"раз в день{time_str}"
                elif interval_days < 7:
                    return f"раз в {interval_days} дня{time_str}"
                elif interval_days % 7 == 0:
                    weeks = interval_days // 7
                    return f"раз в {weeks} {'неделю' if weeks == 1 else 'недели'}{time_str}"
                elif interval_days % 30 == 0:
                    months = interval_days // 30
                    return f"раз в {months} {'месяц' if months == 1 else 'месяца'}{time_str}"
                else:
                    return f"раз в {interval_days} дней{time_str}"
            else:
                return f"интервальная задача{time_str}"
        
        return "задача"

    @staticmethod
    def get_upcoming_tasks(db: Session, days_ahead: int = 7) -> list[Task]:
        """Get tasks due in the next N days."""
        cutoff_date = datetime.now() + timedelta(days=days_ahead)
        return (
            db.query(Task)
            .filter(Task.active.is_(True))
            .filter(Task.reminder_time <= cutoff_date)
            .order_by(Task.reminder_time)
            .all()
        )

    @staticmethod
    def get_today_tasks(db: Session) -> list[Task]:
        """Получить задачи для представления «Сегодня».

        Критерии:
        - Разовые задачи (`one_time`):
          - если активна — показываем, когда `reminder_time` сегодня либо в прошлом;
          - если выполнена — показываем только в тот день, когда она была запланирована.
        - Повторяющиеся и интервальные задачи:
          - если `completed = True`, показываем всегда до ночного сброса;
          - если не выполнена, показываем при наступлении или просрочке (`reminder_time` сегодня/раньше).
        """
        from backend.models.task import TaskType
        
        now = datetime.now()
        today = now.replace(hour=0, minute=0, second=0, microsecond=0)
        tomorrow = today + timedelta(days=1)
        
        tasks = db.query(Task).order_by(Task.reminder_time).all()
        
        result: list[Task] = []
        for task in tasks:
            task_type = task.task_type
            reminder = task.reminder_time
            reminder_midnight = reminder.replace(hour=0, minute=0, second=0, microsecond=0)
            is_due_today = today <= reminder < tomorrow
            is_overdue = reminder < today

            if task_type == TaskType.ONE_TIME:
                if task.completed:
                    if reminder_midnight == today:
                        result.append(task)
                    continue

            # For active one-time tasks and other task types
            if task_type == TaskType.ONE_TIME:
                if task.active and (is_due_today or is_overdue):
                    result.append(task)
                continue

            if task.completed:
                result.append(task)
            elif task.active and (is_due_today or is_overdue):
                result.append(task)

        return result

    @staticmethod
    def mark_task_shown(db: Session, task_id: int) -> Task | None:
        """Mark task as shown for current iteration."""
        task = db.query(Task).filter(Task.id == task_id).first()
        if not task:
            return None
        
        now = datetime.now()
        
        # Проверяем, впервые ли показываем текущую итерацию (по времени напоминания)
        is_first_show = task.last_shown_at is None or task.last_shown_at < task.reminder_time
        
        # Update last_shown_at
        task.last_shown_at = now
        task.revision += 1

        db.commit()
        db.refresh(task)
        
        # Log first shown to history only if this is the first time showing this iteration
        if is_first_show:
            from backend.services.task_history_service import TaskHistoryService
            TaskHistoryService.log_task_first_shown(db, task.id, task.reminder_time)
        
        return task

