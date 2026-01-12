"""Add users table and task assignments

Revision ID: b4c5d6e7f8a9
Revises: fa0aaa1f7284
Create Date: 2025-11-14 10:30:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "b4c5d6e7f8a9"
down_revision: Union[str, None] = "fa0aaa1f7284"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("name", sa.String(length=255), nullable=False),
        sa.Column("email", sa.String(length=255), nullable=True),
        sa.Column("created_at", sa.DateTime(), nullable=False),
        sa.Column("updated_at", sa.DateTime(), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(op.f("ix_users_id"), "users", ["id"], unique=False)
    op.create_index("ux_users_name", "users", ["name"], unique=True)
    op.create_index("ux_users_email", "users", ["email"], unique=True)

    op.create_table(
        "task_users",
        sa.Column("task_id", sa.Integer(), nullable=False),
        sa.Column("user_id", sa.Integer(), nullable=False),
        sa.ForeignKeyConstraint(["task_id"], ["tasks.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("task_id", "user_id"),
    )
    op.create_index(op.f("ix_task_users_task_id"), "task_users", ["task_id"], unique=False)
    op.create_index(op.f("ix_task_users_user_id"), "task_users", ["user_id"], unique=False)


def downgrade() -> None:
    op.drop_index(op.f("ix_task_users_user_id"), table_name="task_users")
    op.drop_index(op.f("ix_task_users_task_id"), table_name="task_users")
    op.drop_table("task_users")

    op.drop_index("ux_users_email", table_name="users")
    op.drop_index("ux_users_name", table_name="users")
    op.drop_index(op.f("ix_users_id"), table_name="users")
    op.drop_table("users")


