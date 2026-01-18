"""Test conflict resolution algorithms."""

import pytest
from datetime import datetime, timezone
from backend.services.conflict_resolver import ConflictResolver


class TestConflictResolver:
    """Test ConflictResolver methods."""

    def test_resolve_completion_conflict_client_newer(self):
        """Test completion conflict where client change is newer."""
        client_task = {
            'id': 1,
            'completed': True,
            'updated_at': datetime(2024, 1, 1, 12, 0, tzinfo=timezone.utc)
        }
        server_task = {
            'id': 1,
            'completed': False,
            'updated_at': datetime(2024, 1, 1, 11, 0, tzinfo=timezone.utc)
        }

        winner, resolved = ConflictResolver._resolve_completion_conflict(client_task, server_task)

        assert winner == 'client'
        assert resolved['completed'] == True

    def test_resolve_completion_conflict_server_newer(self):
        """Test completion conflict where server change is newer."""
        client_task = {
            'id': 1,
            'completed': True,
            'updated_at': datetime(2024, 1, 1, 10, 0, tzinfo=timezone.utc)
        }
        server_task = {
            'id': 1,
            'completed': False,
            'updated_at': datetime(2024, 1, 1, 11, 0, tzinfo=timezone.utc)
        }

        winner, resolved = ConflictResolver._resolve_completion_conflict(client_task, server_task)

        assert winner == 'server'
        assert resolved['completed'] == False

    def test_resolve_recurrence_conflict_client_newer(self):
        """Test recurrence conflict where client change is newer."""
        client_task = {
            'id': 1,
            'recurrence_type': 'daily',
            'recurrence_interval': 2,
            'updated_at': datetime(2024, 1, 1, 12, 0, tzinfo=timezone.utc)
        }
        server_task = {
            'id': 1,
            'recurrence_type': 'weekly',
            'recurrence_interval': 1,
            'updated_at': datetime(2024, 1, 1, 11, 0, tzinfo=timezone.utc)
        }

        winner, resolved = ConflictResolver._resolve_recurrence_conflict(client_task, server_task)

        assert winner == 'client'
        assert resolved['recurrence_type'] == 'daily'
        assert resolved['recurrence_interval'] == 2

    def test_resolve_description_conflict_client_newer(self):
        """Test description conflict where client change is newer."""
        client_task = {
            'id': 1,
            'title': 'Client Title',
            'updated_at': datetime(2024, 1, 1, 12, 0, tzinfo=timezone.utc)
        }
        server_task = {
            'id': 1,
            'title': 'Server Title',
            'updated_at': datetime(2024, 1, 1, 11, 0, tzinfo=timezone.utc)
        }

        winner, resolved = ConflictResolver._resolve_description_conflict(client_task, server_task)

        assert winner == 'client'
        assert resolved['title'] == 'Client Title'

    def test_resolve_task_conflict_completion(self):
        """Test main resolve_task_conflict for completion field."""
        client_task = {
            'id': 1,
            'completed': True,
            'updated_at': datetime(2024, 1, 1, 12, 0, tzinfo=timezone.utc)
        }
        server_task = {
            'id': 1,
            'completed': False,
            'updated_at': datetime(2024, 1, 1, 11, 0, tzinfo=timezone.utc)
        }
        conflict_fields = ['completed']

        winner, resolved = ConflictResolver.resolve_task_conflict(client_task, server_task, conflict_fields)

        assert winner == 'client'
        assert resolved['completed'] == True

    def test_resolve_task_conflict_reminder_time(self):
        """Test main resolve_task_conflict for reminder_time field."""
        client_task = {
            'id': 1,
            'reminder_time': datetime(2024, 1, 1, 10, 0, tzinfo=timezone.utc),
            'updated_at': datetime(2024, 1, 1, 12, 0, tzinfo=timezone.utc)
        }
        server_task = {
            'id': 1,
            'reminder_time': datetime(2024, 1, 1, 11, 0, tzinfo=timezone.utc),
            'updated_at': datetime(2024, 1, 1, 11, 0, tzinfo=timezone.utc)
        }
        conflict_fields = ['reminder_time']

        winner, resolved = ConflictResolver.resolve_task_conflict(client_task, server_task, conflict_fields)

        # Reminder time conflict always lets server win (recalculate)
        assert winner == 'server'

    def test_resolve_task_conflict_recurrence(self):
        """Test main resolve_task_conflict for recurrence fields."""
        client_task = {
            'id': 1,
            'recurrence_type': 'daily',
            'updated_at': datetime(2024, 1, 1, 12, 0, tzinfo=timezone.utc)
        }
        server_task = {
            'id': 1,
            'recurrence_type': 'weekly',
            'updated_at': datetime(2024, 1, 1, 11, 0, tzinfo=timezone.utc)
        }
        conflict_fields = ['recurrence_type']

        winner, resolved = ConflictResolver.resolve_task_conflict(client_task, server_task, conflict_fields)

        assert winner == 'client'
        assert resolved['recurrence_type'] == 'daily'

    def test_resolve_task_conflict_description(self):
        """Test main resolve_task_conflict for description fields."""
        client_task = {
            'id': 1,
            'title': 'Client Title',
            'updated_at': datetime(2024, 1, 1, 12, 0, tzinfo=timezone.utc)
        }
        server_task = {
            'id': 1,
            'title': 'Server Title',
            'updated_at': datetime(2024, 1, 1, 11, 0, tzinfo=timezone.utc)
        }
        conflict_fields = ['title']

        winner, resolved = ConflictResolver.resolve_task_conflict(client_task, server_task, conflict_fields)

        assert winner == 'client'
        assert resolved['title'] == 'Client Title'

    def test_resolve_task_conflict_default_last_change(self):
        """Test main resolve_task_conflict default to last change wins."""
        client_task = {
            'id': 1,
            'some_field': 'client_value',
            'updated_at': datetime(2024, 1, 1, 12, 0, tzinfo=timezone.utc)
        }
        server_task = {
            'id': 1,
            'some_field': 'server_value',
            'updated_at': datetime(2024, 1, 1, 11, 0, tzinfo=timezone.utc)
        }
        conflict_fields = ['some_field']

        winner, resolved = ConflictResolver.resolve_task_conflict(client_task, server_task, conflict_fields)

        assert winner == 'client'
        assert resolved['some_field'] == 'client_value'

    def test_resolve_user_conflict(self):
        """Test user conflict resolution."""
        client_user = {
            'id': 1,
            'name': 'Client Name',
            'updated_at': datetime(2024, 1, 1, 12, 0, tzinfo=timezone.utc)
        }
        server_user = {
            'id': 1,
            'name': 'Server Name',
            'updated_at': datetime(2024, 1, 1, 11, 0, tzinfo=timezone.utc)
        }
        conflict_fields = ['name']

        winner, resolved = ConflictResolver.resolve_user_conflict(client_user, server_user, conflict_fields)

        assert winner == 'client'
        assert resolved['name'] == 'Client Name'

    def test_resolve_group_conflict(self):
        """Test group conflict resolution."""
        client_group = {
            'id': 1,
            'name': 'Client Group',
            'updated_at': datetime(2024, 1, 1, 12, 0, tzinfo=timezone.utc)
        }
        server_group = {
            'id': 1,
            'name': 'Server Group',
            'updated_at': datetime(2024, 1, 1, 11, 0, tzinfo=timezone.utc)
        }
        conflict_fields = ['name']

        winner, resolved = ConflictResolver.resolve_group_conflict(client_group, server_group, conflict_fields)

        assert winner == 'client'
        assert resolved['name'] == 'Client Group'

    def test_resolve_creation_deletion_conflict_client_creation(self):
        """Test creation vs deletion conflict where client created."""
        client_task = {'id': 1, 'title': 'Created', 'updated_at': datetime(2024, 1, 1, 12, 0, tzinfo=timezone.utc)}
        server_task = None  # Server deleted

        winner, resolved = ConflictResolver._resolve_creation_deletion_conflict(client_task, server_task)

        assert winner == 'client'
        assert resolved == client_task

    def test_resolve_creation_deletion_conflict_server_creation(self):
        """Test creation vs deletion conflict where server created."""
        client_task = None  # Client deleted
        server_task = {'id': 1, 'title': 'Created', 'updated_at': datetime(2024, 1, 1, 12, 0, tzinfo=timezone.utc)}

        winner, resolved = ConflictResolver._resolve_creation_deletion_conflict(client_task, server_task)

        assert winner == 'server'
        assert resolved == server_task