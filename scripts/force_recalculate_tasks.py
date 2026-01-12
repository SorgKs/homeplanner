"""Force recalculate all completed tasks."""

import sys
from pathlib import Path

# Add backend to path
backend_path = Path(__file__).parent / "backend"
sys.path.insert(0, str(backend_path))

from backend.database import SessionLocal
from backend.services.task_service import TaskService

def main():
    db = SessionLocal()
    try:
        print("=== FORCED TASK RECALCULATION ===")
        print("Recalculating all completed tasks...")

        # Force recalculation regardless of day check
        TaskService._recalculate_completed_tasks_on_new_day(db, force=True)

        print("Recalculation completed successfully!")
        print()

        # Show updated tasks
        all_tasks = TaskService.get_all_tasks(db)
        print("=== UPDATED TASKS ===")
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

    finally:
        db.close()

if __name__ == "__main__":
    main()