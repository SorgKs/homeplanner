"""Test sync API v0.3 implementation."""

import unittest
from unittest.mock import Mock
from datetime import datetime, timezone

from backend.services.hash_service import HashService
from backend.utils.hash_calculator import HashCalculator


class TestHashService(unittest.TestCase):
    """Test HashService functionality."""

    def test_calculate_task_hash(self):
        """Test task hash calculation through HashService."""
        # Create a mock task with assignees
        task = Mock()
        task.id = 1
        task.title = "Test Task"
        task.description = "Test Description"
        task.task_type = Mock()
        task.task_type.value = "one_time"
        task.recurrence_type = None
        task.recurrence_interval = None
        task.interval_days = None
        task.reminder_time = datetime(2024, 1, 1, 10, 0, 0, tzinfo=timezone.utc)
        task.group_id = None
        task.enabled = True
        task.completed = False
        task.assignees = [Mock(id=1), Mock(id=3), Mock(id=2)]  # Unsorted assignees
        task.updated_at = datetime(2024, 1, 1, 12, 0, 0, tzinfo=timezone.utc)

        # Calculate hash
        hash_value = HashService.calculate_task_hash(task)

        # Verify it's a valid SHA-256 hash (64 characters, hex)
        assert len(hash_value) == 64
        assert hash_value.isalnum()
        assert all(c in '0123456789abcdef' for c in hash_value)

        # The hash should match what HashCalculator produces with correct serialization
        # assigned_user_ids should be sorted: [1, 2, 3] -> "1,2,3"
        expected_assigned_ids = "1,2,3"

        # Create expected data string
        expected_data = f"1|Test Task|Test Description|one_time||||{task.reminder_time.isoformat()}||True|False|{expected_assigned_ids}|{task.updated_at.isoformat()}"
        expected_hash = HashCalculator.calculate_combined_hash([(0, expected_data)])  # Not using combined, just for SHA256

        # Actually calculate expected hash properly
        import hashlib
        expected_hash = hashlib.sha256(expected_data.encode('utf-8')).hexdigest()

        assert hash_value == expected_hash

    def test_calculate_user_hash(self):
        """Test user hash calculation."""
        user = Mock()
        user.id = 1
        user.name = "Test User"

        hash_value = HashService.calculate_user_hash(user)

        # Verify hash
        assert len(hash_value) == 64
        expected_data = "1|Test User"
        import hashlib
        expected_hash = hashlib.sha256(expected_data.encode('utf-8')).hexdigest()
        assert hash_value == expected_hash

    def test_calculate_group_hash(self):
        """Test group hash calculation."""
        group = Mock()
        group.id = 1
        group.name = "Test Group"
        # Note: groups don't have users in current implementation

        hash_value = HashService.calculate_group_hash(group)

        # Verify hash
        assert len(hash_value) == 64
        expected_data = "1|Test Group|"
        import hashlib
        expected_hash = hashlib.sha256(expected_data.encode('utf-8')).hexdigest()
        assert hash_value == expected_hash

    def test_get_entity_hashes_empty(self):
        """Test getting entity hashes for empty database."""
        # This would require database setup, so we'll mock it
        pass  # Placeholder for now

    def test_task_hash_with_null_values(self):
        """Test task hash with null/None values."""
        task = Mock()
        task.id = 1
        task.title = "Test"
        task.description = None
        task.task_type = Mock()
        task.task_type.value = "one_time"
        task.recurrence_type = None
        task.recurrence_interval = None
        task.interval_days = None
        task.reminder_time = None
        task.group_id = None
        task.enabled = True
        task.completed = False
        task.assignees = []
        task.updated_at = datetime(2024, 1, 1, 12, 0, 0, tzinfo=timezone.utc)

        hash_value = HashService.calculate_task_hash(task)

        # Verify hash
        assert len(hash_value) == 64

        # Expected data with None converted to empty strings
        expected_data = f"1|Test|||||||{task.group_id or ''}|True|False||{task.updated_at.isoformat()}"
        import hashlib
        expected_hash = hashlib.sha256(expected_data.encode('utf-8')).hexdigest()
        assert hash_value == expected_hash