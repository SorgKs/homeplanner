"""Add test tasks to database."""

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
        # Create some test tasks
        now = datetime.now()
        today_9am = now.replace(hour=9, minute=0, second=0, microsecond=0)
        tomorrow_9am = (now + timedelta(days=1)).replace(hour=9, minute=0, second=0, microsecond=0)

        tasks_data = [
            TaskCreate(
                title="Test Task Today",
                description="A test task for today",
                task_type=TaskType.ONE_TIME,
                reminder_time=today_9am,
            ),
            TaskCreate(
                title="Daily Recurring Task",
                description="A recurring task every day",
                task_type=TaskType.RECURRING,
                recurrence_type=RecurrenceType.DAILY,
                recurrence_interval=1,
                reminder_time=today_9am,
            ),
            TaskCreate(
                title="Weekly Task",
                description="A task every week",
                task_type=TaskType.RECURRING,
                recurrence_type=RecurrenceType.WEEKLY,
                recurrence_interval=1,
                reminder_time=tomorrow_9am,
            ),
            TaskCreate(
                title="Future One-time Task",
                description="A task in the future",
                task_type=TaskType.ONE_TIME,
                reminder_time=tomorrow_9am,
            ),
        ]

        for task_data in tasks_data:
            task = TaskService.create_task(db, task_data, timestamp=get_current_time())
            print(f"Created task: {task.title} (id: {task.id})")

        # Check total tasks
        all_tasks = TaskService.get_all_tasks(db)
        print(f"\nTotal tasks in database: {len(all_tasks)}")

    finally:
        db.close()

if __name__ == "__main__":
    main()