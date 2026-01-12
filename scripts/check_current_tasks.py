"""Check current tasks in database."""

import sys
from pathlib import Path
from datetime import datetime

# Add backend to path
backend_path = Path(__file__).parent / "backend"
sys.path.insert(0, str(backend_path))

from backend.database import SessionLocal
from backend.services.task_service import TaskService
from backend.services.time_manager import get_current_time

def main():
    db = SessionLocal()
    try:
        print("=== CURRENT TIME STATE ===")
        from backend.services.time_manager import TimeManager
        state = TimeManager.get_state()
        print(f"Real now: {state['real_now']}")
        print(f"Virtual now: {state['virtual_now']}")
        print(f"Override enabled: {state['override_enabled']}")
        print()

        print("=== LAST UPDATE TIMESTAMP ===")
        from backend.utils.date_utils import get_last_update
        last_update = get_last_update(db)
        if last_update:
            print(f"Last update: {last_update}")
        else:
            print("No last update found")
        print()

        print("=== ALL TASKS ===")
        all_tasks = TaskService.get_all_tasks(db)
        for task in all_tasks:
            status = "✓ COMPLETED" if task.completed else "○ ACTIVE"
            if task.task_type == "one_time":
                task_type_str = "Разовая"
            elif task.task_type == "recurring":
                task_type_str = f"Повторяющаяся ({task.recurrence_type})"
            elif task.task_type == "interval":
                task_type_str = f"Интервальная ({task.interval_days} дней)"
            else:
                task_type_str = task.task_type

            print(f"ID {task.id}: {task.title}")
            print(f"  Тип: {task_type_str}")
            print(f"  Статус: {status}")
            print(f"  Время: {task.reminder_time}")
            print(f"  Включена: {'✓' if task.enabled else '✗'}")
            print()

        print(f"Total tasks: {len(all_tasks)}")

        print("\n=== TODAY TASKS FOR USER 1 ===")
        today_tasks = TaskService.get_today_tasks(db, user_id=1)
        for task in today_tasks:
            status = "✓ COMPLETED" if task.completed else "○ ACTIVE"
            print(f"ID {task.id}: {task.title} - {status}")

        print(f"Today tasks for user 1: {len(today_tasks)}")

    finally:
        db.close()

if __name__ == "__main__":
    main()