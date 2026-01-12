"""Add test tasks assigned to users."""

import sys
from pathlib import Path
from datetime import datetime, timedelta

# Add backend to path
backend_path = Path(__file__).parent.parent / "backend"
sys.path.insert(0, str(backend_path))

from backend.database import SessionLocal
from backend.services.task_service import TaskService
from backend.models.task import TaskType, RecurrenceType
from backend.schemas.task import TaskCreate
from backend.services.time_manager import get_current_time

def main():
    db = SessionLocal()
    try:
        # Create some test tasks assigned to user 1
        now = datetime.now()
        today_9am = now.replace(hour=9, minute=0, second=0, microsecond=0)
        tomorrow_9am = (now + timedelta(days=1)).replace(hour=9, minute=0, second=0, microsecond=0)

        tasks_data = [
            TaskCreate(
                title="Today's Task for User 1",
                description="A test task for today assigned to user 1",
                task_type=TaskType.ONE_TIME,
                reminder_time=today_9am,
                assigned_user_ids=[1],
            ),
            TaskCreate(
                title="Daily Task for User 1",
                description="A recurring task every day for user 1",
                task_type=TaskType.RECURRING,
                recurrence_type=RecurrenceType.DAILY,
                recurrence_interval=1,
                reminder_time=today_9am,
                assigned_user_ids=[1],
            ),
            TaskCreate(
                title="Weekly Task for User 1",
                description="A task every week for user 1",
                task_type=TaskType.RECURRING,
                recurrence_type=RecurrenceType.WEEKLY,
                recurrence_interval=1,
                reminder_time=tomorrow_9am,
                assigned_user_ids=[1],
            ),
            TaskCreate(
                title="Future Task for User 1",
                description="A task in the future for user 1",
                task_type=TaskType.ONE_TIME,
                reminder_time=tomorrow_9am,
                assigned_user_ids=[1],
            ),
        ]

        for task_data in tasks_data:
            task = TaskService.create_task(db, task_data, timestamp=get_current_time())
            print(f"Created task: {task.title} (id: {task.id})")

        # Check total tasks
        all_tasks = TaskService.get_all_tasks(db)
        print(f"\nTotal tasks in database: {len(all_tasks)}")

        # Check tasks for user 1
        from backend.routers.tasks import get_tasks
        # Simulate the request
        from fastapi import Request
        from unittest.mock import Mock
        request = Mock(spec=Request)
        request.state.db = db
        # This would need proper setup, but for now just query directly

    finally:
        db.close()

if __name__ == "__main__":
    main()