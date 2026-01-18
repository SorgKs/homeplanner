#!/usr/bin/env python3

import sys
from pathlib import Path
from datetime import datetime, timedelta
import random

# Add backend to path
backend_path = Path(__file__).parent / "backend"
sys.path.insert(0, str(backend_path))

from backend.database import SessionLocal
from backend.services.task_service import TaskService
from backend.models.task import TaskType, RecurrenceType
from backend.schemas.task import TaskCreate
from backend.services.time_manager import get_current_time

def main():
    db = SessionLocal()
    try:
        now = datetime.now()
        today = now.replace(hour=9, minute=0, second=0, microsecond=0)

        # Days from -3 to +3
        days = [-3, -2, -1, 0, 1, 2, 3]

        task_types = [
            TaskType.ONE_TIME,
            TaskType.RECURRING,
            TaskType.INTERVAL
        ]

        recurrence_types = [
            RecurrenceType.DAILY,
            RecurrenceType.WEEKLY,
            RecurrenceType.WEEKENDS,
            RecurrenceType.WEEKDAYS,
            RecurrenceType.INTERVAL
        ]

        user_ids = [1, 2]  # Existing users

        task_counter = 1

        for day_offset in days:
            reminder_time = today + timedelta(days=day_offset)

            for i in range(5):  # 5 tasks per day
                task_type = random.choice(task_types)

                if task_type == TaskType.ONE_TIME:
                    recurrence_type = None
                    recurrence_interval = None
                    interval_days = None
                elif task_type == TaskType.RECURRING:
                    recurrence_type = random.choice(recurrence_types[:-1])  # Exclude INTERVAL for recurring
                    recurrence_interval = random.randint(1, 10)
                    interval_days = None
                elif task_type == TaskType.INTERVAL:
                    recurrence_type = RecurrenceType.INTERVAL
                    recurrence_interval = None
                    interval_days = random.randint(1, 10)

                assigned_user_ids = [random.choice(user_ids)]

                title = f"Test Task {task_counter} (Day {day_offset}, Type {task_type.value})"

                task_data = TaskCreate(
                    title=title,
                    description=f"Test task for day {day_offset}, type {task_type.value}",
                    task_type=task_type,
                    recurrence_type=recurrence_type,
                    recurrence_interval=recurrence_interval,
                    interval_days=interval_days,
                    reminder_time=reminder_time,
                    assigned_user_ids=assigned_user_ids,
                    alarm=random.choice([True, False])
                )

                task = TaskService.create_task(db, task_data, timestamp=get_current_time())
                print(f"Created task: {task.title} (id: {task.id}, reminder: {reminder_time})")

                task_counter += 1

        # Check total tasks
        all_tasks = TaskService.get_all_tasks(db)
        print(f"\nTotal tasks in database: {len(all_tasks)}")

    finally:
        db.close()

if __name__ == "__main__":
    main()