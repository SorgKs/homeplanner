"""Test sync event service functionality."""

import pytest
from unittest.mock import Mock, patch
from datetime import datetime, timezone

from backend.services.sync_event_service import SyncEventService


class TestSyncEventService:
    """Test SyncEventService methods."""

    @patch('backend.services.sync_event_service.SyncEventService._broadcast_task_event')
    @patch('backend.services.task_service.TaskService.create_task')
    @patch('backend.utils.hash_calculator.HashCalculator.calculate_task_hash')
    def test_process_task_create_event(self, mock_hash, mock_create, mock_broadcast):
        """Test processing task create event."""
        # Mock task
        mock_task = Mock()
        mock_task.id = 1
        mock_create.return_value = mock_task
        mock_hash.return_value = "testhash"

        db = Mock()
        changes = {'title': 'Test Task'}
        client_hash = None

        result = SyncEventService.process_task_change_event(db, 'create', None, changes, client_hash)

        assert result['status'] == 'confirmed'
        assert result['task_id'] == 1
        assert result['server_hash'] == 'testhash'
        mock_create.assert_called_once_with(db, changes)
        mock_broadcast.assert_called_once()

    @patch('backend.services.sync_event_service.SyncEventService._broadcast_task_event')
    @patch('backend.services.task_service.TaskService.update_task')
    @patch('backend.utils.hash_calculator.HashCalculator.calculate_task_hash')
    def test_process_task_update_event(self, mock_hash, mock_update, mock_broadcast):
        """Test processing task update event."""
        mock_task = Mock()
        mock_task.id = 1
        mock_update.return_value = mock_task
        mock_hash.return_value = "serverhash"

        db = Mock()
        changes = {'title': 'Updated Task'}
        client_hash = "clienthash"

        result = SyncEventService.process_task_change_event(db, 'update', 1, changes, client_hash)

        assert result['status'] == 'confirmed'
        assert result['task_id'] == 1
        assert result['server_hash'] == 'serverhash'
        mock_update.assert_called_once_with(db, 1, changes, resolve_conflicts=True, timestamp=None)
        mock_broadcast.assert_called_once()

    @patch('backend.services.sync_event_service.SyncEventService._broadcast_task_event')
    @patch('backend.services.task_service.TaskService.delete_task')
    def test_process_task_delete_event(self, mock_delete, mock_broadcast):
        """Test processing task delete event."""
        mock_delete.return_value = True

        db = Mock()

        result = SyncEventService.process_task_change_event(db, 'delete', 1, {}, None)

        assert result['status'] == 'confirmed'
        assert result['task_id'] == 1
        mock_delete.assert_called_once_with(db, 1)
        mock_broadcast.assert_called_once()

    @patch('backend.services.sync_event_service.SyncEventService._broadcast_task_event')
    @patch('backend.services.task_service.TaskService.complete_task')
    @patch('backend.utils.hash_calculator.HashCalculator.calculate_task_hash')
    def test_process_task_complete_event(self, mock_hash, mock_complete, mock_broadcast):
        """Test processing task complete event."""
        mock_task = Mock()
        mock_task.id = 1
        mock_complete.return_value = mock_task
        mock_hash.return_value = "completeserverhash"

        db = Mock()

        result = SyncEventService.process_task_change_event(db, 'complete', 1, {}, None)

        assert result['status'] == 'confirmed'
        assert result['task_id'] == 1
        assert result['server_hash'] == 'completeserverhash'
        mock_broadcast.assert_called_once()

    @patch('backend.services.sync_event_service.SyncEventService._broadcast_user_event')
    @patch('backend.services.user_service.UserService.update_user')
    @patch('backend.utils.hash_calculator.HashCalculator.calculate_user_hash')
    def test_process_user_update_event(self, mock_hash, mock_update, mock_broadcast):
        """Test processing user update event."""
        mock_user = Mock()
        mock_user.id = 1
        mock_update.return_value = mock_user
        mock_hash.return_value = "userhash"

        db = Mock()
        changes = {'name': 'Updated Name'}

        result = SyncEventService.process_user_change_event(db, 'update', 1, changes, None)

        assert result['status'] == 'confirmed'
        assert result['user_id'] == 1
        assert result['server_hash'] == 'userhash'
        mock_update.assert_called_once_with(db, 1, changes)
        mock_broadcast.assert_called_once()

    @patch('backend.services.sync_event_service.SyncEventService._broadcast_group_event')
    @patch('backend.services.group_service.GroupService.create_group')
    @patch('backend.utils.hash_calculator.HashCalculator.calculate_group_hash')
    def test_process_group_create_event(self, mock_hash, mock_create, mock_broadcast):
        """Test processing group create event."""
        mock_group = Mock()
        mock_group.id = 1
        mock_create.return_value = mock_group
        mock_hash.return_value = "grouphash"

        db = Mock()
        changes = {'name': 'Test Group'}

        result = SyncEventService.process_group_change_event(db, 'create', None, changes, None)

        assert result['status'] == 'confirmed'
        assert result['group_id'] == 1
        assert result['server_hash'] == 'grouphash'
        mock_create.assert_called_once_with(db, changes)
        mock_broadcast.assert_called_once()

    @patch('backend.services.task_service.TaskService.get_all_tasks')
    @patch('backend.utils.hash_calculator.HashCalculator.calculate_task_hash')
    def test_get_entity_hashes_tasks(self, mock_hash, mock_get_tasks):
        """Test getting entity hashes for tasks."""
        mock_task = Mock()
        mock_task.id = 1
        mock_get_tasks.return_value = [mock_task]
        mock_hash.return_value = "taskhash"

        db = Mock()

        result = SyncEventService.get_entity_hashes(db, 'tasks')

        assert len(result) == 1
        assert result[0]['id'] == 1
        assert result[0]['hash'] == 'taskhash'
        mock_get_tasks.assert_called_once_with(db, enabled_only=False)

    @patch('backend.services.user_service.UserService.get_all_users')
    @patch('backend.utils.hash_calculator.HashCalculator.calculate_user_hash')
    def test_get_entity_hashes_users(self, mock_hash, mock_get_users):
        """Test getting entity hashes for users."""
        mock_user = Mock()
        mock_user.id = 1
        mock_get_users.return_value = [mock_user]
        mock_hash.return_value = "userhash"

        db = Mock()

        result = SyncEventService.get_entity_hashes(db, 'users')

        assert len(result) == 1
        assert result[0]['id'] == 1
        assert result[0]['hash'] == 'userhash'

    @patch('backend.services.group_service.GroupService.get_all_groups')
    @patch('backend.utils.hash_calculator.HashCalculator.calculate_group_hash')
    def test_get_entity_hashes_groups(self, mock_hash, mock_get_groups):
        """Test getting entity hashes for groups."""
        mock_group = Mock()
        mock_group.id = 1
        mock_get_groups.return_value = [mock_group]
        mock_hash.return_value = "grouphash"

        db = Mock()

        result = SyncEventService.get_entity_hashes(db, 'groups')

        assert len(result) == 1
        assert result[0]['id'] == 1
        assert result[0]['hash'] == 'grouphash'

    def test_verify_hashes_no_conflicts(self):
        """Test hash verification with no conflicts."""
        db = Mock()

        with patch.object(SyncEventService, 'get_entity_hashes', return_value=[
            {'id': 1, 'hash': 'hash1'},
            {'id': 2, 'hash': 'hash2'}
        ]):
            client_hashes = [
                {'id': 1, 'hash': 'hash1'},
                {'id': 2, 'hash': 'hash2'}
            ]

            result = SyncEventService.verify_hashes(db, 'tasks', client_hashes)

            assert result['status'] == 'verified'
            assert len(result['conflicts']) == 0
            assert len(result['missing_on_client']) == 0
            assert len(result['missing_on_server']) == 0

    def test_verify_hashes_with_conflicts(self):
        """Test hash verification with conflicts."""
        db = Mock()

        with patch.object(SyncEventService, 'get_entity_hashes', return_value=[
            {'id': 1, 'hash': 'serverhash1'},
            {'id': 2, 'hash': 'serverhash2'}
        ]):
            client_hashes = [
                {'id': 1, 'hash': 'clienthash1'},  # Conflict
                {'id': 2, 'hash': 'serverhash2'}   # Match
            ]

            result = SyncEventService.verify_hashes(db, 'tasks', client_hashes)

            assert result['status'] == 'verified'
            assert len(result['conflicts']) == 1
            assert result['conflicts'][0]['id'] == 1
            assert result['conflicts'][0]['server_hash'] == 'serverhash1'
            assert result['conflicts'][0]['client_hash'] == 'clienthash1'

    def test_verify_hashes_missing_on_client(self):
        """Test hash verification with items missing on client."""
        db = Mock()

        with patch.object(SyncEventService, 'get_entity_hashes', return_value=[
            {'id': 1, 'hash': 'hash1'},
            {'id': 2, 'hash': 'hash2'}
        ]):
            client_hashes = [
                {'id': 1, 'hash': 'hash1'}
                # Missing id 2
            ]

            result = SyncEventService.verify_hashes(db, 'tasks', client_hashes)

            assert result['status'] == 'verified'
            assert len(result['missing_on_client']) == 1
            assert result['missing_on_client'][0]['id'] == 2

    def test_verify_hashes_missing_on_server(self):
        """Test hash verification with items missing on server."""
        db = Mock()

        with patch.object(SyncEventService, 'get_entity_hashes', return_value=[
            {'id': 1, 'hash': 'hash1'}
        ]):
            client_hashes = [
                {'id': 1, 'hash': 'hash1'},
                {'id': 2, 'hash': 'clienthash2'}  # Missing on server
            ]

            result = SyncEventService.verify_hashes(db, 'tasks', client_hashes)

            assert result['status'] == 'verified'
            assert len(result['missing_on_server']) == 1
            assert result['missing_on_server'][0]['id'] == 2