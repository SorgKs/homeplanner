"""Association tables for task relations."""

from sqlalchemy import Column, ForeignKey, Table

from backend.database import Base

# Association table between tasks and users (many-to-many)
task_user_association = Table(
    "task_users",
    Base.metadata,
    Column("task_id", ForeignKey("tasks.id", ondelete="CASCADE"), primary_key=True),
    Column("user_id", ForeignKey("users.id", ondelete="CASCADE"), primary_key=True),
)

__all__ = ["task_user_association"]


