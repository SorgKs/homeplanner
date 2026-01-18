"""Hash calculator for consistent hashing of tasks, users, and groups."""

import hashlib
from typing import Any


class HashCalculator:
    """Calculates SHA-256 hashes for tasks, users, and groups consistently with Android client."""

    @staticmethod
    def calculate_task_hash(task: Any) -> str:
        """Calculate hash for a task.

        Algorithm:
        1. Collect fields: id|title|description|taskType|recurrenceType|recurrenceInterval|intervalDays|reminderTime|groupId|enabled|completed|assignedUserIds|updatedAt
        2. Replace null fields with empty string ""
        3. Sort assignedUserIds and serialize as "id1,id2,id3"
        4. Compute SHA-256 hash

        Args:
            task: Task object with attributes

        Returns:
            SHA-256 hash as hex string
        """
        # Extract fields, handling None values
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

        # Handle assigned_user_ids - sort and join
        assigned_user_ids = getattr(task, 'assigned_user_ids', [])
        if assigned_user_ids:
            assigned_user_ids_str = ','.join(map(str, sorted(assigned_user_ids)))
        else:
            assigned_user_ids_str = ""

        updated_at = task.updated_at.isoformat() if task.updated_at else ""

        # Create the string to hash
        data_string = f"{task_id}|{title}|{description}|{task_type}|{recurrence_type}|{recurrence_interval}|{interval_days}|{reminder_time}|{group_id}|{enabled}|{completed}|{assigned_user_ids_str}|{updated_at}"

        # Calculate SHA-256 hash
        return hashlib.sha256(data_string.encode('utf-8')).hexdigest()

    @staticmethod
    def calculate_user_hash(user: Any) -> str:
        """Calculate hash for a user.

        Algorithm:
        1. String: "id|name"
        2. SHA-256 hash

        Args:
            user: User object with id and name attributes

        Returns:
            SHA-256 hash as hex string
        """
        user_id = user.id or ""
        name = user.name or ""

        data_string = f"{user_id}|{name}"
        return hashlib.sha256(data_string.encode('utf-8')).hexdigest()

    @staticmethod
    def calculate_group_hash(group: Any) -> str:
        """Calculate hash for a group.

        Algorithm:
        1. String: "id|name|userIds" (userIds as sorted string "id1,id2,id3")
        2. SHA-256 hash

        Args:
            group: Group object with id, name, and user_ids attributes

        Returns:
            SHA-256 hash as hex string
        """
        group_id = group.id or ""
        name = group.name or ""

        # Handle user_ids - sort and join
        user_ids = getattr(group, 'user_ids', [])
        if user_ids:
            user_ids_str = ','.join(map(str, sorted(user_ids)))
        else:
            user_ids_str = ""

        data_string = f"{group_id}|{name}|{user_ids_str}"
        return hashlib.sha256(data_string.encode('utf-8')).hexdigest()

    @staticmethod
    def calculate_combined_hash(id_hashes: list[tuple[int, str]]) -> str:
        """Calculate combined hash from individual element hashes.

        Used for periodic verification - takes list of (id, hash) pairs,
        sorts them by ID, and computes SHA-256 of concatenated hashes.

        Args:
            id_hashes: List of (id, hash) tuples

        Returns:
            Combined SHA-256 hash as hex string
        """
        # Sort by ID to ensure deterministic order
        sorted_id_hashes = sorted(id_hashes, key=lambda x: x[0])
        combined_string = ''.join(hash_value for _, hash_value in sorted_id_hashes)
        return hashlib.sha256(combined_string.encode('utf-8')).hexdigest()