"""Tests for task visibility in 'today' view."""

from datetime import datetime
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from _pytest.capture import CaptureFixture
    from _pytest.fixtures import FixtureRequest
    from _pytest.logging import LogCaptureFixture
    from _pytest.monkeypatch import MonkeyPatch
    from pytest_mock.plugin import MockerFixture


def should_be_visible_in_today_view(task: dict, current_date: datetime) -> bool:
    """Return True if task should appear in the today view."""

    reminder_value = task.get("reminder_time")
    if reminder_value is None:
        return False

    reminder_dt = (
        datetime.fromisoformat(reminder_value)
        if isinstance(reminder_value, str)
        else reminder_value
    )
    today = current_date.replace(hour=0, minute=0, second=0, microsecond=0)
    reminder_day = reminder_dt.replace(hour=0, minute=0, second=0, microsecond=0)

    is_due_today = reminder_day == today
    is_due_today_or_overdue = reminder_day <= today

    task_type = task.get("task_type", "one_time")
    is_active = task.get("active", True)
    is_completed = task.get("completed", False)

    if task_type == "one_time":
        if not is_active:
            return is_due_today
        return is_due_today_or_overdue

    if is_completed:
        return True

    return is_active and is_due_today_or_overdue


def create_task(
    task_type: str,
    reminder_time: datetime,
    *,
    completed: bool = False,
    active: bool = True,
) -> dict:
    """Create task payload matching frontend filter expectations."""

    return {
        "id": 1,
        "title": f"Test {task_type} task",
        "task_type": task_type,
        "reminder_time": reminder_time,
        "completed": completed,
        "active": active,
    }


class TestTodayViewVisibility:
    """Тесты витрины «Сегодня»."""

    def test_one_time_tasks_visibility(self) -> None:
        """Разовые задачи: сегодня/вчера видны, завтра скрыты."""

        current_date = datetime(2025, 1, 2, 12, 0, 0)
        overdue = datetime(2025, 1, 1, 9, 0, 0)
        today = datetime(2025, 1, 2, 9, 0, 0)
        tomorrow = datetime(2025, 1, 3, 9, 0, 0)

        overdue_task = create_task("one_time", overdue)
        today_task = create_task("one_time", today)
        tomorrow_task = create_task("one_time", tomorrow)
        completed_today = create_task("one_time", today, completed=True, active=False)

        assert should_be_visible_in_today_view(overdue_task, current_date)
        assert should_be_visible_in_today_view(today_task, current_date)
        assert not should_be_visible_in_today_view(tomorrow_task, current_date)
        assert should_be_visible_in_today_view(completed_today, current_date)

    def test_recurring_tasks_visibility(self) -> None:
        """Повторяющиеся задачи: активные и просроченные/сегодня видны."""

        current_date = datetime(2025, 1, 2, 12, 0, 0)
        overdue = datetime(2025, 1, 1, 9, 0, 0)
        today = datetime(2025, 1, 2, 9, 0, 0)
        tomorrow = datetime(2025, 1, 3, 9, 0, 0)

        overdue_task = create_task("recurring", overdue)
        today_task = create_task("recurring", today)
        tomorrow_task = create_task("recurring", tomorrow)
        inactive_task = create_task("recurring", today, active=False)

        assert should_be_visible_in_today_view(overdue_task, current_date)
        assert should_be_visible_in_today_view(today_task, current_date)
        assert not should_be_visible_in_today_view(tomorrow_task, current_date)
        assert not should_be_visible_in_today_view(inactive_task, current_date)

    def test_completed_recurring_task_visible(self) -> None:
        """Выполненная повторяющаяся задача остаётся видимой для подтверждения."""

        current_date = datetime(2025, 1, 2, 12, 0, 0)
        next_iteration = datetime(2025, 1, 3, 9, 0, 0)

        completed_task = create_task(
            "interval",
            next_iteration,
            completed=True,
            active=True,
        )

        assert should_be_visible_in_today_view(completed_task, current_date)

