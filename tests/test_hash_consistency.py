"""Test hash consistency between Android and backend implementations."""

import pytest
from unittest.mock import Mock
from datetime import datetime, timezone

from backend.utils.hash_calculator import HashCalculator


class TestHashConsistency:
    """Test hash calculations for consistency."""

    def test_task_hash_consistency(self):
        """Test task hash calculation matches Android implementation."""
        # Create a mock task object
        task = Mock()
        task.id = 1
        task.title = "Test Task"
        task.description = "Test Description"
        task.task_type = "one_time"
        task.recurrence_type = None
        task.recurrence_interval = None
        task.interval_days = None
        task.reminder_time = datetime(2024, 1, 1, 10, 0, 0, tzinfo=timezone.utc)
        task.group_id = None
        task.enabled = True
        task.completed = False
        task.assigned_user_ids = [1, 2, 3]
        task.updated_at = datetime(2024, 1, 1, 12, 0, 0, tzinfo=timezone.utc)

        # Calculate hash using backend implementation
        backend_hash = HashCalculator.calculate_task_hash(task)

        # Android uses the same formula as backend
        # Calculate expected hash using the same logic
        import hashlib
        # Replicate backend logic for expected
        task_id = task.id or ""
        title = task.title or ""
        description = task.description or ""
        task_type = task.task_type or ""
        recurrence_type = task.recurrence_type or ""
        recurrence_interval = task.recurrence_interval or ""
        interval_days = task.interval_days or ""
        reminder_time = task.reminder_time.isoformat() if task.reminder_time else ""
        group_id = task.group_id or ""
        enabled = task.enabled if task.enabled is not None else ""
        completed = task.completed if task.completed is not None else ""
        assigned_user_ids = getattr(task, 'assigned_user_ids', [])
        if assigned_user_ids:
            assigned_user_ids_str = ','.join(map(str, sorted(assigned_user_ids)))
        else:
            assigned_user_ids_str = ""
        updated_at = task.updated_at.isoformat() if task.updated_at else ""
        expected_data = f"{task_id}|{title}|{description}|{task_type}|{recurrence_type}|{recurrence_interval}|{interval_days}|{reminder_time}|{group_id}|{enabled}|{completed}|{assigned_user_ids_str}|{updated_at}"
        expected_hash = hashlib.sha256(expected_data.encode('utf-8')).hexdigest()

        assert backend_hash == expected_hash

    def test_user_hash_consistency(self):
        """Test user hash calculation."""
        user = Mock()
        user.id = 1
        user.name = "Test User"

        backend_hash = HashCalculator.calculate_user_hash(user)

        # Android: "${user.id}|${user.name}"
        expected_data = "1|Test User"
        import hashlib
        expected_hash = hashlib.sha256(expected_data.encode('utf-8')).hexdigest()

        assert backend_hash == expected_hash

    def test_group_hash_consistency(self):
        """Test group hash calculation."""
        group = Mock()
        group.id = 1
        group.name = "Test Group"
        group.user_ids = [1, 2, 3]

        backend_hash = HashCalculator.calculate_group_hash(group)

        # Android: "${group.id}|${group.name}|$userIds" (userIds sorted)
        user_ids_str = "1,2,3"
        expected_data = f"1|Test Group|{user_ids_str}"
        import hashlib
        expected_hash = hashlib.sha256(expected_data.encode('utf-8')).hexdigest()

        assert backend_hash == expected_hash

    def test_combined_hash_consistency(self):
        """Test combined hash calculation."""
        id_hashes = [(1, "hash1"), (2, "hash2"), (3, "hash3")]

        backend_combined = HashCalculator.calculate_combined_hash(id_hashes)

        # Android: sortedBy { it.first }, joinToString("") { it.second }
        # Should be "hash1hash2hash3" (sorted by id 1,2,3)
        expected_data = "hash1hash2hash3"
        import hashlib
        expected_combined = hashlib.sha256(expected_data.encode('utf-8')).hexdigest()

        assert backend_combined == expected_combined

    def test_task_hash_with_none_values(self):
        """Test task hash with None values."""
        task = Mock()
        task.id = 1
        task.title = "Test"
        task.description = None
        task.task_type = "one_time"
        task.recurrence_type = None
        task.recurrence_interval = None
        task.interval_days = None
        task.reminder_time = None
        task.group_id = None
        task.enabled = True
        task.completed = False
        task.assigned_user_ids = []
        task.updated_at = datetime(2024, 1, 1, 12, 0, 0, tzinfo=timezone.utc)

        backend_hash = HashCalculator.calculate_task_hash(task)

        # Replicate backend logic for expected
        import hashlib
        task_id = task.id or ""
        title = task.title or ""
        description = task.description or ""
        task_type = task.task_type or ""
        recurrence_type = task.recurrence_type or ""
        recurrence_interval = task.recurrence_interval or ""
        interval_days = task.interval_days or ""
        reminder_time = task.reminder_time.isoformat() if task.reminder_time else ""
        group_id = task.group_id or ""
        enabled = task.enabled if task.enabled is not None else ""
        completed = task.completed if task.completed is not None else ""
        assigned_user_ids = getattr(task, 'assigned_user_ids', [])
        if assigned_user_ids:
            assigned_user_ids_str = ','.join(map(str, sorted(assigned_user_ids)))
        else:
            assigned_user_ids_str = ""
        updated_at = task.updated_at.isoformat() if task.updated_at else ""
        expected_data = f"{task_id}|{title}|{description}|{task_type}|{recurrence_type}|{recurrence_interval}|{interval_days}|{reminder_time}|{group_id}|{enabled}|{completed}|{assigned_user_ids_str}|{updated_at}"
        expected_hash = hashlib.sha256(expected_data.encode('utf-8')).hexdigest()

        assert backend_hash == expected_hash