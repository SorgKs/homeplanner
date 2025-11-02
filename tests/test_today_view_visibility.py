"""Tests for task visibility in 'today' view."""

from datetime import datetime, timedelta
from typing import TYPE_CHECKING

import pytest

if TYPE_CHECKING:
    from _pytest.capture import CaptureFixture
    from _pytest.fixtures import FixtureRequest
    from _pytest.logging import LogCaptureFixture
    from _pytest.monkeypatch import MonkeyPatch
    from pytest_mock.plugin import MockerFixture


def should_be_visible_in_today_view(task: dict, current_date: datetime) -> bool:
    """Check if task should be visible in 'today' view.
    
    This function replicates the logic from frontend/app.js filterAndRenderTasks
    for the 'today' view.
    
    Args:
        task: Task dictionary with keys:
            - due_date: str (ISO format) or datetime
            - is_completed: bool
            - last_completed_at: str (ISO format) or datetime or None
            - task_type: str ('one_time', 'recurring', 'interval')
        current_date: Current date as datetime
    
    Returns:
        True if task should be visible in 'today' view, False otherwise
    """
    # Parse task due date
    if isinstance(task['due_date'], str):
        task_date = datetime.fromisoformat(task['due_date'].replace('Z', '+00:00'))
    else:
        task_date = task['due_date']
    
    # Get today (start of day)
    today = current_date.replace(hour=0, minute=0, second=0, microsecond=0)
    tomorrow = today + timedelta(days=1)
    
    # Check if task is due today or overdue
    task_date_local = task_date.replace(hour=0, minute=0, second=0, microsecond=0)
    is_due_today_or_overdue = task_date_local < tomorrow
    
    # Check if task was completed today
    # For one_time tasks, completion is indicated by is_active=False
    # For recurring and interval tasks, completion is indicated by last_completed_at
    completed_today = False
    task_type = task.get('task_type', 'one_time')
    
    if task_type == 'one_time':
        # For one_time tasks, if they're inactive, they're completed
        # But we only show them if they were due today or overdue
        # (They don't have last_completed_at, so we can't check when they were completed)
        pass  # For one_time, visibility is based only on due date
    else:
        # For recurring and interval tasks
        if task.get('is_completed') and task.get('last_completed_at'):
            if isinstance(task['last_completed_at'], str):
                completed_date = datetime.fromisoformat(
                    task['last_completed_at'].replace('Z', '+00:00')
                )
            else:
                completed_date = task['last_completed_at']
            
            completed_date_local = completed_date.replace(
                hour=0, minute=0, second=0, microsecond=0
            )
            completed_today = completed_date_local.date() == today.date()
    
    # Task is visible if:
    # 1. It's due today or overdue AND not completed (for uncompleted tasks), OR
    # 2. It was completed TODAY (for recurring/interval tasks)
    # For one_time tasks: only visible if due today or overdue AND not completed
    if task_type == 'one_time':
        # For one_time tasks: if completed (is_active=False), only show if due today
        # If overdue and completed - don't show
        if task.get('is_active') is False:
            # Completed one_time task - only show if due today
            return task_date_local.date() == today.date()
        else:
            # Uncompleted one_time task - show if due today or overdue
            return is_due_today_or_overdue
    else:
        # For recurring/interval tasks:
        # - Visible if due today or overdue AND not completed, OR
        # - Visible if completed TODAY (regardless of due date)
        if task.get('is_completed'):
            # Completed task - only visible if completed today
            return completed_today
        else:
            # Uncompleted task - visible if due today or overdue
            return is_due_today_or_overdue


def create_task(
    task_type: str,
    due_date: datetime,
    is_completed: bool = False,
    last_completed_at: datetime | None = None,
    is_active: bool = True,
) -> dict:
    """Create a test task dictionary.
    
    Args:
        task_type: Type of task ('one_time', 'recurring', 'interval')
        due_date: Task due date
        is_completed: Whether task is completed
        last_completed_at: When task was completed (if completed)
        is_active: Whether task is active
    
    Returns:
        Task dictionary
    """
    task = {
        'id': 1,
        'title': f'Test {task_type} task',
        'task_type': task_type,
        'due_date': due_date.isoformat(),
        'is_completed': is_completed,
        'is_active': is_active,
        'last_completed_at': last_completed_at.isoformat() if last_completed_at else None,
    }
    return task


class TestTodayViewVisibility:
    """Test task visibility in 'today' view."""

    def test_uncompleted_tasks_visible_for_today_and_overdue(self):
        """Test 1: Uncompleted tasks for 1.01, 2.01, 3.01.
        
        On 2.01, tasks for 1.01 and 2.01 should be visible.
        """
        # Current date: 2.01.2025
        current_date = datetime(2025, 1, 2, 12, 0, 0)
        
        # Create tasks for each type with different dates
        dates = [
            datetime(2025, 1, 1, 9, 0, 0),  # 1.01.2025
            datetime(2025, 1, 2, 9, 0, 0),  # 2.01.2025
            datetime(2025, 1, 3, 9, 0, 0),  # 3.01.2025
        ]
        
        task_types = ['one_time', 'recurring', 'interval']
        
        for task_type in task_types:
            for i, date in enumerate(dates, 1):
                task = create_task(
                    task_type=task_type,
                    due_date=date,
                    is_completed=False,
                )
                
                is_visible = should_be_visible_in_today_view(task, current_date)
                
                if i <= 2:  # Tasks for 1.01 and 2.01
                    assert is_visible, (
                        f'{task_type} task for {date.date()} should be visible on 2.01.2025'
                    )
                else:  # Task for 3.01
                    assert not is_visible, (
                        f'{task_type} task for {date.date()} should NOT be visible on 2.01.2025'
                    )

    def test_completed_tasks_on_previous_day_not_visible(self):
        """Test 2: Tasks for 1.01 completed on 1.01.
        
        On 2.01, only task for 2.01 should be visible.
        """
        # Current date: 2.01.2025
        current_date = datetime(2025, 1, 2, 12, 0, 0)
        
        # Create tasks
        dates = [
            datetime(2025, 1, 1, 9, 0, 0),  # 1.01.2025
            datetime(2025, 1, 2, 9, 0, 0),  # 2.01.2025
            datetime(2025, 1, 3, 9, 0, 0),  # 3.01.2025
        ]
        
        task_types = ['one_time', 'recurring', 'interval']
        
        for task_type in task_types:
            # Task for 1.01, completed on 1.01
            # For one_time tasks: is_active=False means completed
            # For recurring/interval tasks: is_completed=True and last_completed_at
            if task_type == 'one_time':
                task_1_01 = create_task(
                    task_type=task_type,
                    due_date=dates[0],
                    is_completed=True,
                    is_active=False,  # one_time tasks are inactive when completed
                    last_completed_at=None,  # one_time tasks don't have last_completed_at
                )
            else:
                task_1_01 = create_task(
                    task_type=task_type,
                    due_date=dates[0],
                    is_completed=True,
                    last_completed_at=datetime(2025, 1, 1, 21, 0, 0),
                )
            
            # Task for 2.01, not completed
            task_2_01 = create_task(
                task_type=task_type,
                due_date=dates[1],
                is_completed=False,
            )
            
            # Task for 3.01, not completed
            task_3_01 = create_task(
                task_type=task_type,
                due_date=dates[2],
                is_completed=False,
            )
            
            # Task for 1.01 should NOT be visible (completed on previous day)
            assert not should_be_visible_in_today_view(
                task_1_01, current_date
            ), f'{task_type} task for 1.01 completed on 1.01 should NOT be visible on 2.01'
            
            # Task for 2.01 should be visible (due today)
            assert should_be_visible_in_today_view(
                task_2_01, current_date
            ), f'{task_type} task for 2.01 should be visible on 2.01'
            
            # Task for 3.01 should NOT be visible (due tomorrow)
            assert not should_be_visible_in_today_view(
                task_3_01, current_date
            ), f'{task_type} task for 3.01 should NOT be visible on 2.01'

    def test_completed_tasks_on_current_day_visible(self):
        """Test 3: Tasks for 1.01 and 2.01 completed on 2.01.
        
        On 2.01, tasks for 1.01 and 2.01 should be visible.
        Task for 3.01 should NOT be visible.
        """
        # Current date: 2.01.2025
        current_date = datetime(2025, 1, 2, 12, 0, 0)
        
        # Create tasks
        dates = [
            datetime(2025, 1, 1, 9, 0, 0),  # 1.01.2025
            datetime(2025, 1, 2, 9, 0, 0),  # 2.01.2025
            datetime(2025, 1, 3, 9, 0, 0),  # 3.01.2025
        ]
        
        task_types = ['one_time', 'recurring', 'interval']
        
        for task_type in task_types:
            # Task for 1.01, completed on 2.01
            task_1_01 = create_task(
                task_type=task_type,
                due_date=dates[0],
                is_completed=True,
                last_completed_at=datetime(2025, 1, 2, 10, 0, 0),  # Completed on 2.01
            )
            
            # Task for 2.01, completed on 2.01
            task_2_01 = create_task(
                task_type=task_type,
                due_date=dates[1],
                is_completed=True,
                last_completed_at=datetime(2025, 1, 2, 11, 0, 0),  # Completed on 2.01
            )
            
            # Task for 3.01, not completed
            task_3_01 = create_task(
                task_type=task_type,
                due_date=dates[2],
                is_completed=False,
            )
            
            # Task for 1.01 should be visible (completed today)
            assert should_be_visible_in_today_view(
                task_1_01, current_date
            ), f'{task_type} task for 1.01 completed on 2.01 should be visible on 2.01'
            
            # Task for 2.01 should be visible (completed today)
            assert should_be_visible_in_today_view(
                task_2_01, current_date
            ), f'{task_type} task for 2.01 completed on 2.01 should be visible on 2.01'
            
            # Task for 3.01 should NOT be visible (due tomorrow)
            assert not should_be_visible_in_today_view(
                task_3_01, current_date
            ), f'{task_type} task for 3.01 should NOT be visible on 2.01'

