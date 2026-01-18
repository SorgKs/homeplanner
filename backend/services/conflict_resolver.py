"""Conflict resolution service for synchronizing tasks, users, and groups."""

from datetime import datetime
from typing import Any, Dict, Optional, Tuple
import logging

logger = logging.getLogger("homeplanner.conflict_resolver")


class ConflictResolver:
    """Resolves conflicts between client and server data according to CACHE_STRATEGY_UPDATE_V1."""

    @staticmethod
    def resolve_task_conflict(
        client_task: Dict[str, Any],
        server_task: Dict[str, Any],
        conflict_fields: list[str]
    ) -> Tuple[str, Dict[str, Any]]:
        """Resolve conflict for a task based on changed fields.

        Returns:
            Tuple of (winner, resolved_data) where winner is 'client' or 'server'
        """
        # Determine conflict type based on changed fields
        if 'completed' in conflict_fields or 'last_completed_at' in conflict_fields:
            return ConflictResolver._resolve_completion_conflict(client_task, server_task)

        if 'reminder_time' in conflict_fields:
            return ConflictResolver._resolve_reminder_time_conflict(client_task, server_task)

        if any(field in conflict_fields for field in ['task_type', 'recurrence_type', 'recurrence_interval', 'interval_days', 'reminder_time']):
            return ConflictResolver._resolve_recurrence_conflict(client_task, server_task)

        if any(field in conflict_fields for field in ['title', 'description', 'group_id', 'assigned_user_ids']):
            return ConflictResolver._resolve_description_conflict(client_task, server_task)

        # Default to "last change wins" based on updated_at
        return ConflictResolver._resolve_last_change_wins(client_task, server_task)

    @staticmethod
    def _resolve_completion_conflict(client_task: Dict[str, Any], server_task: Dict[str, Any]) -> Tuple[str, Dict[str, Any]]:
        """Resolve completion/uncompletion conflict.

        Algorithm: "Last change wins" - compare updatedAt timestamps.
        """
        client_updated = client_task.get('updated_at')
        server_updated = server_task.get('updated_at')

        # Handle string timestamps from API
        if isinstance(client_updated, str):
            from dateutil import parser
            client_updated = parser.parse(client_updated)
        if isinstance(server_updated, str):
            from dateutil import parser
            server_updated = parser.parse(server_updated)

        if client_updated and server_updated:
            if client_updated > server_updated:
                logger.info("Completion conflict resolved: client wins (newer)")
                return 'client', client_task
            else:
                logger.info("Completion conflict resolved: server wins (newer)")
                return 'server', server_task
        elif client_updated:
            return 'client', client_task
        else:
            return 'server', server_task

    @staticmethod
    def _resolve_reminder_time_conflict(client_task: Dict[str, Any], server_task: Dict[str, Any]) -> Tuple[str, Dict[str, Any]]:
        """Resolve reminder_time conflict.

        Algorithm: Recalculate reminder_time based on recurrence rules.
        Server always recalculates and applies the result.
        """
        # In practice, this would require access to TaskService to recalculate
        # For now, let server decide (assume server has the correct calculation)
        logger.info("Reminder time conflict: server recalculates and wins")
        return 'server', server_task

    @staticmethod
    def _resolve_recurrence_conflict(client_task: Dict[str, Any], server_task: Dict[str, Any]) -> Tuple[str, Dict[str, Any]]:
        """Resolve recurrence pattern conflict.

        Algorithm: "Last change wins" - compare updatedAt of recurrence fields.
        """
        client_updated = client_task.get('updated_at')
        server_updated = server_task.get('updated_at')

        # Handle string timestamps from API
        if isinstance(client_updated, str):
            from dateutil import parser
            client_updated = parser.parse(client_updated)
        if isinstance(server_updated, str):
            from dateutil import parser
            server_updated = parser.parse(server_updated)

        if client_updated and server_updated:
            if client_updated > server_updated:
                logger.info("Recurrence conflict resolved: client wins (newer)")
                return 'client', client_task
            else:
                logger.info("Recurrence conflict resolved: server wins (newer)")
                return 'server', server_task
        elif client_updated:
            return 'client', client_task
        else:
            return 'server', server_task

    @staticmethod
    def _resolve_description_conflict(client_task: Dict[str, Any], server_task: Dict[str, Any]) -> Tuple[str, Dict[str, Any]]:
        """Resolve description fields conflict (title, description, group_id, assigned_user_ids).

        Algorithm: "Last change wins" - compare updatedAt.
        """
        client_updated = client_task.get('updated_at')
        server_updated = server_task.get('updated_at')

        # Handle string timestamps from API
        if isinstance(client_updated, str):
            from dateutil import parser
            client_updated = parser.parse(client_updated)
        if isinstance(server_updated, str):
            from dateutil import parser
            server_updated = parser.parse(server_updated)

        if client_updated and server_updated:
            if client_updated > server_updated:
                logger.info("Description conflict resolved: client wins (newer)")
                return 'client', client_task
            else:
                logger.info("Description conflict resolved: server wins (newer)")
                return 'server', server_task
        elif client_updated:
            return 'client', client_task
        else:
            return 'server', server_task

    @staticmethod
    def _resolve_creation_deletion_conflict(client_task: Optional[Dict[str, Any]], server_task: Optional[Dict[str, Any]]) -> Tuple[str, Optional[Dict[str, Any]]]:
        """Resolve creation vs deletion conflict.

        Algorithm: Creation has priority over deletion.
        If task created on client and deleted on server → keep creation
        If task deleted on client and changed on server → keep server changes
        """
        if client_task and not server_task:
            # Task exists on client but not on server (client created, server deleted)
            logger.info("Creation/deletion conflict: client creation wins over server deletion")
            return 'client', client_task
        elif not client_task and server_task:
            # Task exists on server but not on client (server created/changed, client deleted)
            logger.info("Creation/deletion conflict: server wins (creation/change beats deletion)")
            return 'server', server_task
        else:
            # Both exist or both don't - shouldn't happen in this context
            return 'server', server_task

    @staticmethod
    def _resolve_last_change_wins(client_data: Dict[str, Any], server_data: Dict[str, Any]) -> Tuple[str, Dict[str, Any]]:
        """Default resolution: last change wins based on updated_at."""
        client_updated = client_data.get('updated_at')
        server_updated = server_data.get('updated_at')

        # Handle string timestamps from API
        if isinstance(client_updated, str):
            from dateutil import parser
            client_updated = parser.parse(client_updated)
        if isinstance(server_updated, str):
            from dateutil import parser
            server_updated = parser.parse(server_updated)

        if client_updated and server_updated:
            if client_updated > server_updated:
                return 'client', client_data
            else:
                return 'server', server_data
        elif client_updated:
            return 'client', client_data
        else:
            return 'server', server_data

    @staticmethod
    def resolve_user_conflict(
        client_user: Dict[str, Any],
        server_user: Dict[str, Any],
        conflict_fields: list[str]
    ) -> Tuple[str, Dict[str, Any]]:
        """Resolve conflict for a user.

        For users, typically only name conflicts, use last change wins.
        """
        return ConflictResolver._resolve_last_change_wins(client_user, server_user)

    @staticmethod
    def resolve_group_conflict(
        client_group: Dict[str, Any],
        server_group: Dict[str, Any],
        conflict_fields: list[str]
    ) -> Tuple[str, Dict[str, Any]]:
        """Resolve conflict for a group.

        For groups, typically name or user_ids conflicts, use last change wins.
        """
        return ConflictResolver._resolve_last_change_wins(client_group, server_group)