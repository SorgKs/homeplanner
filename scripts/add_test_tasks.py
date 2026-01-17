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

        tasks_data = []

        # Create 25 test tasks
        for i in range(1, 26):
            if i % 4 == 1:
                # One-time task
                task = TaskCreate(
                    title=f"One-time Task {i}",
                    description=f"A one-time test task number {i}",
                    task_type=TaskType.ONE_TIME,
                    reminder_time=today_9am + timedelta(hours=i),
                )
            elif i % 4 == 2:
                # Daily recurring
                task = TaskCreate(
                    title=f"Daily Recurring Task {i}",
                    description=f"A daily recurring test task number {i}",
                    task_type=TaskType.RECURRING,
                    recurrence_type=RecurrenceType.DAILY,
                    recurrence_interval=1,
                    reminder_time=today_9am + timedelta(hours=i),
                )
            elif i % 4 == 3:
                # Weekly recurring
                task = TaskCreate(
                    title=f"Weekly Recurring Task {i}",
                    description=f"A weekly recurring test task number {i}",
                    task_type=TaskType.RECURRING,
                    recurrence_type=RecurrenceType.WEEKLY,
                    recurrence_interval=1,
                    reminder_time=tomorrow_9am + timedelta(hours=i),
                )
            else:
                # Future one-time
                task = TaskCreate(
                    title=f"Future One-time Task {i}",
                    description=f"A future one-time test task number {i}",
                    task_type=TaskType.ONE_TIME,
                    reminder_time=tomorrow_9am + timedelta(days=i//4, hours=i),
                )
            tasks_data.append(task)

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