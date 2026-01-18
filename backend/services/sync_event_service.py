"""Service for handling synchronization events between client and server."""

from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional
import logging

from sqlalchemy.orm import Session

from backend.models.task import Task
from backend.models.user import User
from backend.models.group import Group
from backend.services.task_service import TaskService
from backend.services.user_service import UserService
from backend.services.group_service import GroupService
from backend.services.conflict_resolver import ConflictResolver
from backend.utils.hash_calculator import HashCalculator
from backend.routers.realtime import manager as ws_manager

# Import for GroupService and UserService
try:
    from backend.services.user_service import UserService
    from backend.services.group_service import GroupService
except ImportError:
    # If not implemented yet, create stubs
    class UserService:
        @staticmethod
        def get_all_users(db): return []
        @staticmethod
        def update_user(db, user_id, data, resolve_conflicts=False, timestamp=None): return None

    class GroupService:
        @staticmethod
        def get_all_groups(db): return []
        @staticmethod
        def create_group(db, data): return None
        @staticmethod
        def update_group(db, group_id, data, resolve_conflicts=False, timestamp=None): return None
        @staticmethod
        def delete_group(db, group_id): return False

logger = logging.getLogger("homeplanner.sync_events")


class SyncEventService:
    """Handles synchronization events and conflict resolution."""

    # Cache for last hash send times to avoid sending too frequently
    _last_hash_times: Dict[str, datetime] = {}

    @staticmethod
    def process_task_change_event(
        db: Session,
        event_type: str,
        task_id: int,
        changes: Dict[str, Any],
        client_hash: Optional[str] = None,
        timestamp: Optional[datetime] = None
    ) -> Dict[str, Any]:
        """Process a task change event from client.

        Args:
            db: Database session
            event_type: Type of event ('create', 'update', 'delete', 'complete', 'uncomplete')
            task_id: Task ID (None for create)
            changes: Changed data
            client_hash: Client's hash for the task

        Returns:
            Response with confirmation and server hash
        """
        logger.info("Processing task %s event for task_id=%s", event_type, task_id)

        try:
            if event_type == 'create':
                task = TaskService.create_task(db, changes)
                server_hash = HashCalculator.calculate_task_hash(task)
                SyncEventService._broadcast_task_event('created', task.id, task)
                return {'status': 'confirmed', 'task_id': task.id, 'server_hash': server_hash}

            elif event_type == 'update':
                task = TaskService.update_task(db, task_id, changes, resolve_conflicts=True, timestamp=timestamp)
                if task:
                    server_hash = HashCalculator.calculate_task_hash(task)
                    # Check for hash mismatch (potential conflict)
                    if client_hash and client_hash != server_hash:
                        logger.warning("Hash mismatch for task %s: client=%s, server=%s", task_id, client_hash, server_hash)
                        # Conflict was resolved by TaskService.update_task with resolve_conflicts=True
                    SyncEventService._broadcast_task_event('updated', task.id, task)
                    return {'status': 'confirmed', 'task_id': task.id, 'server_hash': server_hash}
                else:
                    return {'status': 'error', 'message': f'Task {task_id} not found'}

            elif event_type == 'delete':
                success = TaskService.delete_task(db, task_id)
                if success:
                    SyncEventService._broadcast_task_event('deleted', task_id)
                    return {'status': 'confirmed', 'task_id': task_id}
                else:
                    return {'status': 'error', 'message': f'Task {task_id} not found'}

            elif event_type == 'complete':
                task = TaskService.complete_task(db, task_id, timestamp=timestamp)
                if task:
                    server_hash = HashCalculator.calculate_task_hash(task)
                    SyncEventService._broadcast_task_event('completed', task.id, task)
                    return {'status': 'confirmed', 'task_id': task.id, 'server_hash': server_hash}
                else:
                    return {'status': 'error', 'message': f'Task {task_id} not found'}

            elif event_type == 'uncomplete':
                task = TaskService.uncomplete_task(db, task_id, timestamp=timestamp)
                if task:
                    server_hash = HashCalculator.calculate_task_hash(task)
                    SyncEventService._broadcast_task_event('uncompleted', task.id, task)
                    return {'status': 'confirmed', 'task_id': task.id, 'server_hash': server_hash}
                else:
                    return {'status': 'error', 'message': f'Task {task_id} not found'}

            else:
                return {'status': 'error', 'message': f'Unknown event type: {event_type}'}

        except Exception as e:
            logger.error("Error processing task event %s for task %s: %s", event_type, task_id, e, exc_info=True)
            return {'status': 'error', 'message': str(e)}

    @staticmethod
    def process_user_change_event(
        db: Session,
        event_type: str,
        user_id: int,
        changes: Dict[str, Any],
        client_hash: Optional[str] = None
    ) -> Dict[str, Any]:
        """Process a user change event from client."""
        logger.info("Processing user %s event for user_id=%s", event_type, user_id)

        try:
            if event_type == 'update':
                user = UserService.update_user(db, user_id, changes)
                if user:
                    server_hash = HashCalculator.calculate_user_hash(user)
                    SyncEventService._broadcast_user_event('updated', user.id, user)
                    return {'status': 'confirmed', 'user_id': user.id, 'server_hash': server_hash}
                else:
                    return {'status': 'error', 'message': f'User {user_id} not found'}
            else:
                return {'status': 'error', 'message': f'Unknown user event type: {event_type}'}

        except Exception as e:
            logger.error("Error processing user event %s for user %s: %s", event_type, user_id, e, exc_info=True)
            return {'status': 'error', 'message': str(e)}

    @staticmethod
    def process_group_change_event(
        db: Session,
        event_type: str,
        group_id: int,
        changes: Dict[str, Any],
        client_hash: Optional[str] = None
    ) -> Dict[str, Any]:
        """Process a group change event from client."""
        logger.info("Processing group %s event for group_id=%s", event_type, group_id)

        try:
            if event_type == 'create':
                group = GroupService.create_group(db, changes)
                server_hash = HashCalculator.calculate_group_hash(group)
                SyncEventService._broadcast_group_event('created', group.id, group)
                return {'status': 'confirmed', 'group_id': group.id, 'server_hash': server_hash}

            elif event_type == 'update':
                group = GroupService.update_group(db, group_id, changes)
                if group:
                    server_hash = HashCalculator.calculate_group_hash(group)
                    SyncEventService._broadcast_group_event('updated', group.id, group)
                    return {'status': 'confirmed', 'group_id': group.id, 'server_hash': server_hash}
                else:
                    return {'status': 'error', 'message': f'Group {group_id} not found'}

            elif event_type == 'delete':
                success = GroupService.delete_group(db, group_id)
                if success:
                    SyncEventService._broadcast_group_event('deleted', group_id)
                    return {'status': 'confirmed', 'group_id': group_id}
                else:
                    return {'status': 'error', 'message': f'Group {group_id} not found'}

            else:
                return {'status': 'error', 'message': f'Unknown group event type: {event_type}'}

        except Exception as e:
            logger.error("Error processing group event %s for group %s: %s", event_type, group_id, e, exc_info=True)
            return {'status': 'error', 'message': str(e)}

    @staticmethod
    def get_entity_hashes(db: Session, entity_type: str) -> List[Dict[str, Any]]:
        """Get hashes for all entities of a specific type.

        Used for periodic hash verification.
        """
        if entity_type == 'tasks':
            tasks = TaskService.get_all_tasks(db, enabled_only=False)
            return [{'id': task.id, 'hash': HashCalculator.calculate_task_hash(task)} for task in tasks]
        elif entity_type == 'users':
            users = UserService.get_all_users(db)
            return [{'id': user.id, 'hash': HashCalculator.calculate_user_hash(user)} for user in users]
        elif entity_type == 'groups':
            groups = GroupService.get_all_groups(db)
            return [{'id': group.id, 'hash': HashCalculator.calculate_group_hash(group)} for group in groups]
        else:
            raise ValueError(f"Unknown entity type: {entity_type}")

    @staticmethod
    def verify_hashes(db: Session, entity_type: str, client_hashes: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Verify client hashes against server hashes and detect conflicts."""
        server_hashes = SyncEventService.get_entity_hashes(db, entity_type)

        # Create lookup dicts
        server_hash_dict = {item['id']: item['hash'] for item in server_hashes}
        client_hash_dict = {item['id']: item['hash'] for item in client_hashes}

        conflicts = []
        missing_on_server = []
        missing_on_client = []

        # Check for conflicts and missing items
        all_ids = set(server_hash_dict.keys()) | set(client_hash_dict.keys())

        for entity_id in all_ids:
            server_hash = server_hash_dict.get(entity_id)
            client_hash = client_hash_dict.get(entity_id)

            if server_hash and client_hash:
                if server_hash != client_hash:
                    conflicts.append({
                        'id': entity_id,
                        'server_hash': server_hash,
                        'client_hash': client_hash
                    })
            elif server_hash and not client_hash:
                missing_on_client.append({'id': entity_id, 'server_hash': server_hash})
            elif client_hash and not server_hash:
                missing_on_server.append({'id': entity_id, 'client_hash': client_hash})

        return {
            'status': 'verified',
            'conflicts': conflicts,
            'missing_on_client': missing_on_client,
            'missing_on_server': missing_on_server
        }

    @staticmethod
    def resolve_conflicts(db: Session, entity_type: str, conflicts: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Resolve conflicts for entities."""
        resolved = []
        failed = []

        for conflict in conflicts:
            try:
                entity_id = conflict['id']

                if entity_type == 'tasks':
                    # Get current server and client data (client data would come from conflict details)
                    # For now, assume server data is authoritative after conflict detection
                    server_task = TaskService.get_task(db, entity_id)
                    if server_task:
                        # In a real implementation, we'd need client data to resolve
                        # For now, just mark as resolved with server data
                        resolved.append({
                            'id': entity_id,
                            'resolution': 'server_wins',
                            'data': server_task.__dict__
                        })
                    else:
                        failed.append({'id': entity_id, 'error': 'Task not found'})

                # Similar logic for users and groups...

            except Exception as e:
                failed.append({'id': entity_id, 'error': str(e)})

        return {
            'resolved': resolved,
            'failed': failed
        }

    @staticmethod
    def _broadcast_task_event(action: str, task_id: int, task: Optional[Task] = None) -> None:
        """Broadcast task event via WebSocket."""
        message = {
            "type": "task_update",
            "action": action,
            "task_id": task_id
        }
        if task:
            from backend.schemas.task import TaskResponse
            message["task"] = TaskResponse.model_validate(task).model_dump(mode="json")

        # Broadcast asynchronously
        import asyncio
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                asyncio.create_task(ws_manager.broadcast(message))
            else:
                loop.run_until_complete(ws_manager.broadcast(message))
        except RuntimeError:
            # No event loop, use anyio
            import anyio
            anyio.from_thread.run(ws_manager.broadcast, message)

    @staticmethod
    def _broadcast_user_event(action: str, user_id: int, user: Optional[User] = None) -> None:
        """Broadcast user event via WebSocket."""
        message = {
            "type": "user_update",
            "action": action,
            "user_id": user_id
        }
        if user:
            message["user"] = {"id": user.id, "name": user.name}

        import asyncio
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                asyncio.create_task(ws_manager.broadcast(message))
            else:
                loop.run_until_complete(ws_manager.broadcast(message))
        except RuntimeError:
            import anyio
            anyio.from_thread.run(ws_manager.broadcast, message)

    @staticmethod
    def _broadcast_group_event(action: str, group_id: int, group: Optional[Group] = None) -> None:
        """Broadcast group event via WebSocket."""
        message = {
            "type": "group_update",
            "action": action,
            "group_id": group_id
        }
        if group:
            message["group"] = {"id": group.id, "name": group.name}

        import asyncio
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                asyncio.create_task(ws_manager.broadcast(message))
            else:
                loop.run_until_complete(ws_manager.broadcast(message))
        except RuntimeError:
            import anyio
            anyio.from_thread.run(ws_manager.broadcast, message)