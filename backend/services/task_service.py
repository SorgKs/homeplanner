"""Service for task business logic."""

from datetime import datetime, timedelta
from typing import TYPE_CHECKING

from sqlalchemy.orm import Session, selectinload

from backend.config import get_settings
from backend.models.task import RecurrenceType, Task
from backend.models.user import User
from backend.services.time_manager import get_current_time

if TYPE_CHECKING:
    from backend.schemas.task import TaskCreate, TaskUpdate

from backend.utils.date_utils import get_day_start, get_last_update, set_last_update, is_new_day, format_datetime_short, format_datetime_for_history, LAST_UPDATE_KEY
from backend.utils.recurrence_utils import find_nth_weekday_in_month, determine_weekday_occurrence, calculate_next_due_date
from backend.utils.format_utils import format_task_settings


class TaskService:
    """Service for managing recurring tasks."""



    @staticmethod
    def _get_users_by_ids(db: Session, user_ids: list[int]) -> list[User]:
        """Load users by IDs ensuring all exist."""
        if not user_ids:
            return []
        unique_ids = sorted(set(user_ids))
        users = db.query(User).filter(User.id.in_(unique_ids)).all()
        found_ids = {user.id for user in users}
        missing = sorted(set(unique_ids) - found_ids)
        if missing:
            raise ValueError(f"Users not found: {missing}")
        user_map = {user.id: user for user in users}
        # Preserve requested order (including duplicates if any)
        return [user_map[user_id] for user_id in user_ids]

    @staticmethod
    def create_task(db: Session, task_data: "TaskCreate", timestamp: datetime | None = None) -> Task:
        """Create a new recurring task.
        
        Args:
            db: Database session.
            task_data: Task creation data.
            timestamp: Optional client timestamp for sync operations.
                      If provided, used for both created_at and updated_at.
                      If None, uses server current time (default behavior).
        """
        import logging
        logger = logging.getLogger("homeplanner.tasks")
        
        from backend.models.task import RecurrenceType, TaskType
        
        payload = task_data.model_dump(exclude={"assigned_user_ids"})
        task = Task(**payload)
        
        # Если передан timestamp от клиента (для sync операций), используем его
        # Иначе будут использованы дефолтные значения из модели (get_current_time)
        if timestamp is not None:
            task.created_at = timestamp
            task.updated_at = timestamp
        else:
            # Логируем предупреждение, если timestamp не передан (для синхронизации он обязателен)
            logger.warning("create_task: timestamp not provided, using server current time")
        
        if task_data.assigned_user_ids:
            task.assignees = TaskService._get_users_by_ids(db, task_data.assigned_user_ids)
        
        # reminder_time is now required (set in schema)
        
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
        task_settings = format_task_settings(task.task_type, task)
        comment = task_settings

        from backend.models.task_history import TaskHistoryAction
        TaskHistoryService.log_action(db, task.id, TaskHistoryAction.CREATED, metadata=metadata, comment=comment, timestamp=timestamp)

        return task

    @staticmethod
    def get_task(db: Session, task_id: int) -> Task | None:
        """Get a task by ID."""
        return (
            db.query(Task)
            .options(selectinload(Task.assignees))
            .filter(Task.id == task_id)
            .first()
        )

    @staticmethod
    def check_new_day(db: Session, ws_manager=None) -> bool:
        """Check if a new day has started and recalculate completed tasks if needed.

        Uses last_update metadata to determine if it's a new day.
        If new day detected, performs recalculation of completed tasks, updates last_update,
        and sends WebSocket notification if ws_manager is provided.

        Args:
            db: Database session.
            ws_manager: Optional WebSocket manager for sending notifications.

        Returns:
            True if new day was detected and tasks were recalculated, False otherwise.
        """
        if is_new_day(db):
            TaskService.recalculate_tasks(db)

            # Send WebSocket notification if manager is provided
            if ws_manager is not None:
                from backend.services.time_manager import TimeManager
                import asyncio
                today = TimeManager.get_real_time().date().isoformat()

                # Send notification asynchronously
                async def send_notification():
                    await ws_manager.broadcast({
                        "type": "day_changed",
                        "new_day": today,
                        "timestamp": TimeManager.get_real_time().isoformat()
                    })

                # Create task for async notification (fire and forget)
                asyncio.create_task(send_notification())

            return True
        return False

    @staticmethod
    def recalculate_tasks(db: Session) -> None:
        """Recalculate dates for all completed tasks and update last_update.

        Finds all completed tasks and updates their dates based on task type,
        resets completed status for recurring/interval tasks, and updates last_update.

        Args:
            db: Database session.
        """
        from backend.models.task import TaskType

        # All operations happen in a single transaction to ensure atomicity
        now = get_current_time()
        today_start = get_day_start(now)

        # Find tasks that need date update:
        # - Are completed (completed = True)
        # All completed tasks are recalculated regardless of reminder_time
        all_completed_tasks = (
            db.query(Task)
            .options(selectinload(Task.assignees))
            .filter(Task.completed == True)
            .all()
        )
        tasks_to_update = all_completed_tasks

        # Update all tasks in memory (no commit yet)
        for task in tasks_to_update:
            # Task date is in the past and task was completed
            # Update the date based on task type and reset completed flag for recurring/interval tasks
            if task.task_type == TaskType.ONE_TIME:
                # One-time tasks: date never changes, но завершённые задачи
                # должны стать неактивными при наступлении нового дня
                task.enabled = False
            elif task.task_type == TaskType.INTERVAL:
                # Interval tasks: always shift from task's reminder_time
                interval_days = task.interval_days or 1
                base_time = max(task.reminder_time, now) if task.reminder_time else now
                next_date = base_time + timedelta(days=interval_days)
                # Preserve original time component from reminder_time
                if task.reminder_time is not None:
                    next_date = next_date.replace(
                        hour=task.reminder_time.hour,
                        minute=task.reminder_time.minute,
                        second=0,
                        microsecond=0,
                    )
                task.reminder_time = next_date
                # Reset completed flag - task should be active again
                task.completed = False
            else:
                # Recurring tasks: calculate next date based on task's reminder_time,
                # ensuring the result is strictly in the future relative to current time
                base_time = max(task.reminder_time, now)  # Use reminder_time if in future, otherwise now
                interval = task.recurrence_interval or 1
                candidate = calculate_next_due_date(
                    base_time,
                    task.recurrence_type,
                    interval,
                    task.reminder_time,
                )
                # Ensure candidate is strictly in the future relative to current time
                # In rare edge cases (large overdue or same-moment), advance again
                safety_counter = 0
                while candidate <= now and safety_counter < 12:
                    # Move the base forward slightly and recalculate
                    base_time = candidate + timedelta(seconds=1)
                    candidate = calculate_next_due_date(
                        base_time,
                        task.recurrence_type,
                        interval,
                        task.reminder_time,
                    )
                    safety_counter += 1
                task.reminder_time = candidate
                # Reset completed flag - task should be active again
                task.completed = False

        # Update last_update timestamp to current real time (in same transaction)
        # This happens even if no tasks were updated, to mark that we checked
        set_last_update(db, None, commit=False)  # Uses real time

        # Commit all changes atomically: tasks updates + last_update
        db.commit()

    @staticmethod
    def _recalculate_completed_tasks_on_new_day(db: Session, force: bool = False) -> None:
        """Legacy method for backward compatibility.

        Args:
            db: Database session.
            force: If True, force recalculation regardless of day check.
        """
        # For backward compatibility, maintain old behavior
        if force or is_new_day(db):
            TaskService.recalculate_tasks(db)

    @staticmethod
    def get_all_tasks(db: Session, enabled_only: bool = False, force_recalc: bool = False) -> list[Task]:
        """Get all tasks, optionally filtering by enabled status.

        Also updates dates for completed tasks if a new day has started
        since last update. Uses last_update metadata to determine if it's a new day.

        Args:
            db: Database session.
            enabled_only: If True, return only enabled tasks.
            force_recalc: If True, force recalculation of completed tasks.
        """
        # Check for new day and recalculate tasks if needed
        TaskService.check_new_day(db)

        # Now get all tasks (with updated dates)
        query = db.query(Task).options(selectinload(Task.assignees))
        if enabled_only:
            query = query.filter(Task.enabled == True)
        return query.order_by(Task.reminder_time).all()

    @staticmethod
    def get_tasks_for_today(db: Session) -> list[Task]:
        """Return tasks visible in 'today' view (deprecated wrapper).

        Использует единые канонические правила фильтрации из get_today_tasks().
        Оставлен для обратной совместимости.
        """
        return TaskService.get_today_tasks(db, user_id=None)

    @staticmethod
    def update_task(db: Session, task_id: int, task_data: "TaskUpdate", timestamp: datetime | None = None) -> Task | None:
        """Update a task.
        
        Args:
            db: Database session.
            task_id: Task identifier.
            task_data: Task update data.
            timestamp: Optional client timestamp for sync operations.
                     If provided, used for updated_at.
                     If None, uses server current time (default behavior).
        """
        task = (
            db.query(Task)
            .options(selectinload(Task.assignees))
            .filter(Task.id == task_id)
            .first()
        )
        if not task:
            return None

        update_data = task_data.model_dump(exclude_unset=True, exclude={"assigned_user_ids"})

        # Conflict resolution for sync operations
        if resolve_conflicts and timestamp is not None:
            from backend.services.conflict_resolver import ConflictResolver
            from backend.utils.hash_calculator import HashCalculator

            # Create client task data for conflict resolution
            client_task_data = task.__dict__.copy()
            client_task_data.update(update_data)
            client_task_data['updated_at'] = timestamp

            # Determine changed fields
            changed_fields = list(update_data.keys())
            if task_data.assigned_user_ids is not None:
                changed_fields.append('assigned_user_ids')

            # Apply conflict resolution
            winner, resolved_data = ConflictResolver.resolve_task_conflict(
                client_task_data, task.__dict__, changed_fields
            )

            if winner == 'server':
                # Server wins - no changes needed
                return task
            # Client wins - proceed with update
            update_data = {k: v for k, v in resolved_data.items() if k in update_data}
        
        # Log reminder_time update for debugging
        if "reminder_time" in update_data:
            import logging
            logger = logging.getLogger("homeplanner.tasks")
            logger.info(
                "Task update: id=%s, old_reminder_time=%s, new_reminder_time=%s",
                task.id,
                task.reminder_time,
                update_data["reminder_time"]
            )
        
        # Save old task state for history comments (before applying changes)
        old_task_state = {
            "task_type": task.task_type,
            "reminder_time": task.reminder_time,
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

        # Update assignees if provided
        if task_data.assigned_user_ids is not None:
            new_users = TaskService._get_users_by_ids(db, task_data.assigned_user_ids)
            new_ids = [user.id for user in new_users]
            old_ids = [user.id for user in task.assignees]
            if set(new_ids) != set(old_ids):
                task.assignees = new_users
                changes["assigned_user_ids"] = new_ids
                old_values["assigned_user_ids"] = old_ids
        
        # reminder_time is now required (set in schema)
        
        # Если передан timestamp от клиента (для sync операций), используем его для updated_at
        # Иначе будет использовано дефолтное значение из модели (get_current_time)
        if timestamp is not None:
            task.updated_at = timestamp
        else:
            # Логируем предупреждение, если timestamp не передан (для синхронизации он обязателен)
            import logging
            logger = logging.getLogger("homeplanner.tasks")
            logger.warning("update_task: timestamp not provided, using server current time")

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
                    "reminder_time": "время напоминания",
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
                        RecurrenceType.MONTHLY_WEEKDAY: "ежемесячно (по дню недели)",
                        RecurrenceType.YEARLY: "ежегодно",
                        RecurrenceType.YEARLY_WEEKDAY: "ежегодно (по дню недели)",
                        RecurrenceType.CUSTOM: "произвольно",
                        RecurrenceType.INTERVAL: "интервал",
                    }
                    if isinstance(val, str):
                        val = RecurrenceType(val)
                    return recurrence_types.get(val, str(val))
                
                # Handle datetime - format in human-readable way (local time)
                if isinstance(val, datetime):
                    return format_datetime_for_history(val)
                
                return str(val)
            
            # Determine which fields are relevant based on final task type
            def is_field_relevant(key: str, final_task_type: str) -> bool:
                """Check if field is relevant for the final task type."""
                # Check current task type (after update)
                relevant_fields = {
                    "one_time": {"title", "description", "reminder_time", "group_id"},
                    "recurring": {"title", "description", "task_type", "recurrence_type", "recurrence_interval", "reminder_time", "group_id"},
                    "interval": {"title", "description", "task_type", "interval_days", "reminder_time", "group_id"},
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
                old_settings = format_task_settings(old_task_type, old_task_state)
                new_settings = format_task_settings(new_task_type, task)

                comment = f"вместо '{old_settings}' теперь будет '{new_settings}'"
            
            else:
                # Regular field changes - always show full configuration description
                from backend.models.task import TaskType
                
                # Build old state dict with old values for formatting
                # Always use old_task_state for task_type to get correct old type
                old_state_for_formatting = {}
                # Get all relevant fields for the old task type
                relevant_fields = ["task_type", "recurrence_type", "recurrence_interval", "reminder_time", "interval_days"]
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
                        if key == "reminder_time":
                            old_state_for_formatting[key] = old_task_state.get("reminder_time", task.reminder_time)
                        else:
                            old_state_for_formatting[key] = getattr(task, key, None)
                
                # Build new state from current task
                new_state_for_formatting = {
                    "task_type": task.task_type,
                    "recurrence_type": task.recurrence_type,
                    "recurrence_interval": task.recurrence_interval,
                    "reminder_time": task.reminder_time,
                    "interval_days": task.interval_days,
                }
                
                # Format old and new settings using full configuration description
                # Get task types from state dicts and convert TaskType enum to string value
                old_task_type_from_state = old_state_for_formatting.get("task_type", old_task_state.get("task_type", task.task_type))
                old_task_type_str = old_task_type_from_state.value if isinstance(old_task_type_from_state, TaskType) else str(old_task_type_from_state)

                new_task_type_from_state = new_state_for_formatting.get("task_type", task.task_type)
                new_task_type_str = new_task_type_from_state.value if isinstance(new_task_type_from_state, TaskType) else str(new_task_type_from_state)

                old_settings = format_task_settings(old_task_type_str, old_state_for_formatting)
                new_settings = format_task_settings(new_task_type_str, new_state_for_formatting)
                
                comment = f"вместо '{old_settings}' теперь будет '{new_settings}'"
            
            TaskHistoryService.log_action(
                db, task.id, TaskHistoryAction.EDITED, metadata=metadata, comment=comment, timestamp=timestamp
            )
        
        return task

    @staticmethod
    def delete_task(db: Session, task_id: int, timestamp: datetime | None = None) -> bool:
        """Delete a task.
        
        Args:
            db: Database session.
            task_id: Task identifier.
            timestamp: Optional client timestamp for sync operations.
                     If provided, used for updated_at before deletion.
                     If None, uses server current time (default behavior).
        """
        task = (
            db.query(Task)
            .options(selectinload(Task.assignees))
            .filter(Task.id == task_id)
            .first()
        )
        if not task:
            return False

        # Log deletion to history before deleting
        # Save task_id and task data before deletion (for cascade protection)
        saved_task_id = task.id
        saved_task_title = task.title
        
        from backend.models.task_history import TaskHistoryAction
        import json
        
        # Generate comment with task settings
        task_settings = format_task_settings(task.task_type, task)
        comment = task_settings

        # Create metadata with task info (including title) to preserve it after deletion
        metadata = {
            "task_id": saved_task_id,
            "task_title": saved_task_title,
            "task_type": task.task_type.value if hasattr(task.task_type, 'value') else str(task.task_type),
        }
        metadata_json = json.dumps(metadata)
        
        # Если передан timestamp от клиента (для sync операций), обновляем updated_at перед удалением
        if timestamp is not None:
            task.updated_at = timestamp
        else:
            # Логируем предупреждение, если timestamp не передан (для синхронизации он обязателен)
            import logging
            logger = logging.getLogger("homeplanner.tasks")
            logger.warning("delete_task: timestamp not provided, using server current time")
        
        # Create history entry manually to ensure it's saved with task_id before deletion
        from backend.models.task_history import TaskHistory
        history_entry = TaskHistory(
            task_id=saved_task_id,
            action=TaskHistoryAction.DELETED,
            action_timestamp=timestamp if timestamp is not None else get_current_time(),
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
    def complete_task(db: Session, task_id: int, timestamp: datetime | None = None) -> Task | None:
        """Mark a task as completed and update next due date.

        If task is due today or overdue, the date is not updated immediately.
        Date will be updated only after day_start_hour (next day) via get_all_tasks.

        Args:
            db: Database session.
            task_id: Task identifier.
            timestamp: Optional client timestamp for sync operations.
                     If provided, used for updated_at.
                     If None, uses server current time (default behavior).
        """
        import logging
        logger = logging.getLogger("homeplanner.tasks")

        try:
            from backend.models.task import TaskType

            task = (
                db.query(Task)
                .options(selectinload(Task.assignees))
                .filter(Task.id == task_id)
                .first()
            )
            if not task:
                return None

            now = get_current_time()

            # Store iteration date for history logging
            iteration_date = task.reminder_time

            # Mark task as completed
            task.completed = True
            # Per specification and tests: do NOT shift reminder_time on completion for any task type.
            # Centralized recalculation will occur at day boundaries inside get_all_tasks().
            # Note: enabled is set to False only during day recalculation for one-time tasks

            # Если передан timestamp от клиента (для sync операций), используем его для updated_at
            # Иначе будет использовано дефолтное значение из модели (get_current_time)
            if timestamp is not None:
                task.updated_at = timestamp
            else:
                # Логируем предупреждение, если timestamp не передан (для синхронизации он обязателен)
                logger.warning("complete_task: timestamp not provided, using server current time")

            db.commit()
            db.refresh(task)

            # Log confirmation to history
            from backend.services.task_history_service import TaskHistoryService
            TaskHistoryService.log_task_confirmed(db, task.id, iteration_date, timestamp=timestamp)

            return task
        except Exception as e:
            logger.error("Exception in complete_task for task_id=%s: %s", task_id, str(e), exc_info=True)
            raise

    @staticmethod
    def uncomplete_task(db: Session, task_id: int, timestamp: datetime | None = None) -> Task | None:
        """Revert task completion (cancel confirmation).

        For one-time tasks, mark task as active again.
        For recurring and interval tasks, clear completed flag.
        Restores reminder_time to the original date from history (iteration_date of last confirmed action).
        
        Args:
            db: Database session.
            task_id: Task identifier.
            timestamp: Optional client timestamp for sync operations.
                     If provided, used for updated_at.
                     If None, uses server current time (default behavior).
        """
        import logging
        logger = logging.getLogger("homeplanner.tasks")

        from backend.models.task import TaskType
        from backend.models.task_history import TaskHistory, TaskHistoryAction

        task = (
            db.query(Task)
            .options(selectinload(Task.assignees))
            .filter(Task.id == task_id)
            .first()
        )
        if not task:
            logger.error("uncomplete_task: task not found, task_id=%s", task_id)
            return None

        logger.info("uncomplete_task: task found, task_type=%s, completed=%s, enabled=%s, reminder_time=%s",
                   task.task_type, task.completed, task.enabled, task.reminder_time)

        # Clear completed flag
        task.completed = False

        if task.task_type == TaskType.ONE_TIME:
            # Make one-time task active again
            task.enabled = True
            logger.info("uncomplete_task: enabled one-time task")

        # Если передан timestamp от клиента (для sync операций), используем его для updated_at
        # Иначе будет использовано дефолтное значение из модели (get_current_time)
        if timestamp is not None:
            task.updated_at = timestamp
            logger.info("uncomplete_task: using client timestamp for updated_at: %s", timestamp)
        else:
            # Логируем предупреждение, если timestamp не передан (для синхронизации он обязателен)
            logger.warning("uncomplete_task: timestamp not provided, using server current time")

        db.commit()
        db.refresh(task)

        logger.info("uncomplete_task: after commit, completed=%s, enabled=%s, reminder_time=%s",
                   task.completed, task.enabled, task.reminder_time)

        # Log unconfirmation to history
        from backend.services.task_history_service import TaskHistoryService
        TaskHistoryService.log_task_unconfirmed(db, task.id, task.reminder_time, timestamp=timestamp)

        logger.info("uncomplete_task: completed successfully for task_id=%s", task_id)
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
            raise ValueError("reminder_time is required for weekly recurring tasks (день недели и время обязательны для еженедельных задач)")
        
        if not reminder_time:
            # Old behavior without reminder_time (for other recurrence types)
            # Note: MONTHLY_WEEKDAY and YEARLY_WEEKDAY require reminder_time
            if recurrence_type == RecurrenceType.DAILY:
                return current_date + timedelta(days=interval)
            elif recurrence_type == RecurrenceType.MONTHLY:
                return current_date + timedelta(days=30 * interval)
            elif recurrence_type == RecurrenceType.YEARLY:
                return current_date + timedelta(days=365 * interval)
            elif recurrence_type == RecurrenceType.MONTHLY_WEEKDAY:
                # Fallback: treat like monthly
                return current_date + timedelta(days=30 * interval)
            elif recurrence_type == RecurrenceType.YEARLY_WEEKDAY:
                # Fallback: treat like yearly
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
            # Try to create date in current month
            try:
                next_date = current_date.replace(day=reminder_day, hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            except ValueError:
                # Month doesn't have that day, use last day of current month
                from calendar import monthrange
                last_day = monthrange(current_date.year, current_date.month)[1]
                next_date = current_date.replace(day=last_day, hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)

            # If this month's occurrence has passed, move to next month(s)
            if next_date <= current_date:
                # Calculate how many months to add
                months_to_add = interval
                # Move forward by that many months
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
        
        elif recurrence_type == RecurrenceType.MONTHLY_WEEKDAY:
            # Monthly by weekday: e.g., 2nd Tuesday of each month
            # reminder_time should contain a date with the target weekday
            # We need to determine which occurrence (1st, 2nd, 3rd, 4th, or last)
            reminder_weekday = reminder_time.weekday()
            reminder_day = reminder_time.day
            reminder_month = reminder_time.month
            reminder_year = reminder_time.year
            # Determine which occurrence this is (1-4 or -1 for last)
            n = TaskService._determine_weekday_occurrence(reminder_day, reminder_weekday, reminder_year, reminder_month)
            
            # Start from current month
            target_month = current_date.month
            target_year = current_date.year
            
            # Find the N-th weekday in current month
            candidate = TaskService._find_nth_weekday_in_month(target_year, target_month, reminder_weekday, n)
            candidate = candidate.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            
            if candidate <= current_date:
                # Current month's occurrence has passed, move to next month(s)
                months_to_add = interval
                target_month += months_to_add
                while target_month > 12:
                    target_month -= 12
                    target_year += 1
                candidate = TaskService._find_nth_weekday_in_month(target_year, target_month, reminder_weekday, n)
                candidate = candidate.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            
            return candidate
        
        elif recurrence_type == RecurrenceType.YEARLY:
            # Yearly: same month, day, and time
            reminder_month = reminder_time.month
            reminder_day = reminder_time.day
            # Try to create date in current year
            try:
                next_date = current_date.replace(month=reminder_month, day=reminder_day, hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            except ValueError:
                # Year doesn't have that month/day combination, use last day of month
                from calendar import monthrange
                last_day = monthrange(current_date.year, reminder_month)[1]
                next_date = current_date.replace(month=reminder_month, day=last_day, hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)

            # If this year's occurrence has passed, move forward by interval years
            if next_date <= current_date:
                try:
                    next_date = next_date.replace(year=current_date.year + interval)
                except ValueError:
                    # Target year doesn't have that date, use last day of month
                    target_year = current_date.year + interval
                    last_day = monthrange(target_year, reminder_month)[1]
                    next_date = next_date.replace(year=target_year, day=last_day)
            return next_date
        
        elif recurrence_type == RecurrenceType.YEARLY_WEEKDAY:
            # Yearly by weekday: e.g., 1st Monday of January each year
            reminder_month = reminder_time.month
            reminder_weekday = reminder_time.weekday()
            reminder_day = reminder_time.day
            reminder_year = reminder_time.year
            # Determine which occurrence this is (1-4 or -1 for last)
            n = TaskService._determine_weekday_occurrence(reminder_day, reminder_weekday, reminder_year, reminder_month)
            
            # Start from current year
            target_year = current_date.year
            
            # Find the N-th weekday in target month of current year
            candidate = TaskService._find_nth_weekday_in_month(target_year, reminder_month, reminder_weekday, n)
            candidate = candidate.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            
            if candidate <= current_date:
                # Current year's occurrence has passed, move forward by interval years
                target_year += interval
                candidate = TaskService._find_nth_weekday_in_month(target_year, reminder_month, reminder_weekday, n)
                candidate = candidate.replace(hour=reminder_hour, minute=reminder_minute, second=0, microsecond=0)
            
            return candidate
        
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
            
            elif recurrence_type == RecurrenceType.MONTHLY_WEEKDAY:
                if reminder_time:
                    time_str = reminder_time.strftime("%H:%M")
                    weekday_num = reminder_time.weekday()
                    day_of_month = reminder_time.day
                    weekdays_ru = [
                        "понедельник", "вторник", "среду", "четверг", "пятницу", "субботу", "воскресенье"
                    ]
                    weekday_ru = weekdays_ru[weekday_num]
                    # Determine which occurrence (1st, 2nd, 3rd, 4th, or last)
                    # Use a more accurate method by checking the actual date
                    if hasattr(reminder_time, "year"):
                        year = reminder_time.year
                    else:
                        year = get_current_time().year
                    month = reminder_time.month
                    n = TaskService._determine_weekday_occurrence(day_of_month, weekday_num, year, month)
                    if n == -1:
                        nth_str = "последний"
                    else:
                        nth_words = {1: "первый", 2: "второй", 3: "третий", 4: "четвертый"}
                        nth_str = nth_words.get(n, f"{n}-й")
                    
                    if recurrence_interval == 1:
                        return f"{nth_str} {weekday_ru} месяца в {time_str}"
                    else:
                        return f"{nth_str} {weekday_ru} месяца в {time_str} каждые {recurrence_interval} месяца"
                else:
                    if recurrence_interval == 1:
                        return "ежемесячно (по дню недели)"
                    else:
                        return f"каждые {recurrence_interval} месяца (по дню недели)"
            
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
            
            elif recurrence_type == RecurrenceType.YEARLY_WEEKDAY:
                if reminder_time:
                    time_str = reminder_time.strftime("%H:%M")
                    weekday_num = reminder_time.weekday()
                    day_of_month = reminder_time.day
                    month_num = reminder_time.month
                    weekdays_ru = [
                        "понедельник", "вторник", "среду", "четверг", "пятницу", "субботу", "воскресенье"
                    ]
                    weekday_ru = weekdays_ru[weekday_num]
                    months_ru = {
                        1: "января", 2: "февраля", 3: "марта", 4: "апреля",
                        5: "мая", 6: "июня", 7: "июля", 8: "августа",
                        9: "сентября", 10: "октября", 11: "ноября", 12: "декабря"
                    }
                    month_ru = months_ru.get(month_num, f"{month_num} месяца")
                    # Determine which occurrence (1st, 2nd, 3rd, 4th, or last)
                    week_of_month = (day_of_month - 1) // 7 + 1
                    if week_of_month > 4:
                        nth_str = "последний"
                    else:
                        nth_words = {1: "первый", 2: "второй", 3: "третий", 4: "четвертый"}
                        nth_str = nth_words.get(week_of_month, f"{week_of_month}-й")
                    
                    if recurrence_interval == 1:
                        return f"{nth_str} {weekday_ru} {month_ru} в {time_str} ежегодно"
                    else:
                        return f"{nth_str} {weekday_ru} {month_ru} в {time_str} каждые {recurrence_interval} года"
                else:
                    if recurrence_interval == 1:
                        return "ежегодно (по дню недели)"
                    else:
                        return f"каждые {recurrence_interval} года (по дню недели)"
            
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
                    if weeks == 1:
                        return f"раз в неделю{time_str}"
                    else:
                        return f"раз в {weeks} недели{time_str}"
                elif interval_days % 30 == 0:
                    months = interval_days // 30
                    if months == 1:
                        return f"раз в месяц{time_str}"
                    else:
                        return f"раз в {months} месяца{time_str}"
                else:
                    return f"раз в {interval_days} дней{time_str}"
            else:
                return f"интервальная задача{time_str}"
        
        return "задача"

    @staticmethod
    def get_upcoming_tasks(db: Session, days_ahead: int = 7) -> list[Task]:
        """Get tasks due in the next N days."""
        cutoff_date = get_current_time() + timedelta(days=days_ahead)
        return (
            db.query(Task)
            .options(selectinload(Task.assignees))
            .filter(Task.enabled == True)
            .filter(Task.reminder_time <= cutoff_date)
            .order_by(Task.reminder_time)
            .all()
        )

    @staticmethod
    def get_today_tasks(db: Session, user_id: int | None = None) -> list[Task]:
        """Return tasks that must be displayed in the 'today' view.

        Args:
            db: Database session.
            user_id: Optional user identifier used to filter assignments.

        Returns:
            Ordered list of tasks that should be shown to the current user.
        """
        from backend.models.task import TaskType

        # Check for new day and recalculate tasks if needed
        TaskService.check_new_day(db)

        now = get_current_time()
        today_start = get_day_start(now)

        tasks = (
            db.query(Task)
            .options(selectinload(Task.assignees))
            .order_by(Task.reminder_time)
            .all()
        )

        today_tasks: list[Task] = []
        for task in tasks:
            if task.reminder_time is None:
                continue

            task_day_start = get_day_start(task.reminder_time)

            # Положение reminder_time относительно текущего логического дня
            if task_day_start < today_start:
                pos = "PAST"
            elif task_day_start == today_start:
                pos = "TODAY"
            else:
                pos = "FUTURE"

            # Determine completed status: use completed flag for all task types
            is_completed = task.completed

            # Каноническая логика фильтрации из OFFLINE_REQUIREMENTS:
            # Задача видна, если enabled = true AND (completed = true OR reminder_time ∈ {PAST, TODAY})
            include = task.enabled and (is_completed or pos in ("PAST", "TODAY"))

            if not include:
                continue

            # Фильтрация по пользователю:
            # - Если пользователь не выбран (user_id is None) - показываем все задачи
            # - Если задача не назначена никому (пустой список assignees) - показываем всем
            # - Если задача назначена пользователям - показываем только если user_id в списке
            if user_id is not None:
                assignee_ids = {user.id for user in task.assignees}
                if assignee_ids and user_id not in assignee_ids:
                    continue

            today_tasks.append(task)

        return today_tasks

    @staticmethod
    def get_today_task_ids(db: Session, user_id: int | None = None) -> list[int]:
        """Return identifiers of tasks visible in 'today' view."""
        tasks = TaskService.get_today_tasks(db, user_id=user_id)
        return [task.id for task in tasks]

    @staticmethod
    def mark_task_shown(db: Session, task_id: int) -> Task | None:
        """Mark task as shown for current iteration."""
        task = (
            db.query(Task)
            .options(selectinload(Task.assignees))
            .filter(Task.id == task_id)
            .first()
        )
        if not task:
            return None
        
        now = get_current_time()
        
        # Check if this is a new iteration (last_shown_at is before current reminder_time)
        is_first_show = task.last_shown_at is None or task.last_shown_at < task.reminder_time
        
        # Update last_shown_at
        task.last_shown_at = now
        db.commit()
        db.refresh(task)
        
        # Log first shown to history only if this is the first time showing this iteration
        if is_first_show:
            from backend.services.task_history_service import TaskHistoryService
            TaskHistoryService.log_task_first_shown(db, task.id, task.reminder_time)
        
        return task
