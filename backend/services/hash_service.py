"""Hash service for generating SHA-256 hashes for tasks, users, and groups."""

from typing import Any, List
from sqlalchemy.orm import Session

from backend.models.task import Task
from backend.models.user import User
from backend.models.group import Group
from backend.utils.hash_calculator import HashCalculator


class HashService:
    """Service for generating SHA-256 hashes with consistent serialization."""

    @staticmethod
    def calculate_task_hash(task: Task) -> str:
        """Generate SHA-256 hash for a task.

        Uses the same algorithm as Android implementation:
        Concatenates fields with "|" separator, null → "".

        Args:
            task: Task instance

        Returns:
            SHA-256 hash as hex string
        """
        # Get assigned user IDs sorted
        assigned_user_ids = [user.id for user in task.assignees] if task.assignees else []

        # Create a mock object with the required fields
        class MockTask:
            def __init__(self, task: Task, assigned_user_ids: List[int]):
                self.id = task.id
                self.title = task.title
                self.description = task.description
                self.task_type = task.task_type.value if task.task_type else ""
                self.recurrence_type = task.recurrence_type.value if task.recurrence_type else ""
                self.recurrence_interval = task.recurrence_interval
                self.interval_days = task.interval_days
                self.reminder_time = task.reminder_time
                self.group_id = task.group_id
                self.enabled = task.enabled
                self.completed = task.completed
                self.assigned_user_ids = assigned_user_ids
                self.updated_at = task.updated_at

        mock_task = MockTask(task, assigned_user_ids)
        return HashCalculator.calculate_task_hash(mock_task)

    @staticmethod
    def calculate_user_hash(user: User) -> str:
        """Generate SHA-256 hash for a user.

        Args:
            user: User instance

        Returns:
            SHA-256 hash as hex string
        """
        return HashCalculator.calculate_user_hash(user)

    @staticmethod
    def calculate_group_hash(group: Group) -> str:
        """Generate SHA-256 hash for a group.

        Args:
            group: Group instance

        Returns:
            SHA-256 hash as hex string
        """
        # Get user IDs for the group
        # Note: Groups in current implementation don't have direct user relationships
        # This will need to be updated when groups are fully implemented
        user_ids = []  # Placeholder

        # Create a mock object
        class MockGroup:
            def __init__(self, group: Group, user_ids: List[int]):
                self.id = group.id
                self.name = group.name
                self.user_ids = user_ids

        mock_group = MockGroup(group, user_ids)
        return HashCalculator.calculate_group_hash(mock_group)

    @staticmethod
    def get_entity_hashes(db: Session, entity_type: str) -> List[dict[str, Any]]:
        """Get hashes for all entities of specified type.

        Args:
            db: Database session
            entity_type: "tasks", "users", or "groups"

        Returns:
            List of {"id": int, "hash": str} dictionaries
        """
        if entity_type == "tasks":
            from backend.models.task import Task
            entities = db.query(Task).options().all()  # No need for assignees here
            result = []
            for task in entities:
                hash_value = HashService.calculate_task_hash(task)
                result.append({"id": task.id, "hash": hash_value})
            return result

        elif entity_type == "users":
            from backend.models.user import User
            entities = db.query(User).all()
            result = []
            for user in entities:
                hash_value = HashService.calculate_user_hash(user)
                result.append({"id": user.id, "hash": hash_value})
            return result

        elif entity_type == "groups":
            from backend.models.group import Group
            entities = db.query(Group).all()
            result = []
            for group in entities:
                hash_value = HashService.calculate_group_hash(group)
                result.append({"id": group.id, "hash": hash_value})
            return result

        else:
            raise ValueError(f"Unsupported entity type: {entity_type}")

    @staticmethod
    def get_combined_hash(db: Session, entity_type: str) -> str:
        """Get combined hash for all entities of specified type.

        Args:
            db: Database session
            entity_type: "tasks", "users", or "groups"

        Returns:
            Combined SHA-256 hash as hex string
        """
        hashes = HashService.get_entity_hashes(db, entity_type)
        id_hashes = [(item["id"], item["hash"]) for item in hashes]
        return HashCalculator.calculate_combined_hash(id_hashes)