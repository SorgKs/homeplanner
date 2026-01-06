"""Rename active column to enabled in tasks table

Revision ID: 20260105_rename_active_to_enabled
Revises: 20251110_add_app_metadata_table
Create Date: 2026-01-05 06:25:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "20260105_rename_active_to_enabled"
down_revision: Union[str, None] = "20251110_add_app_metadata_table"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Add new 'enabled' column with default value True
    op.add_column(
        "tasks",
        sa.Column("enabled", sa.Boolean(), nullable=False, server_default=sa.true()),
    )

    # Copy data from 'active' to 'enabled'
    op.execute("UPDATE tasks SET enabled = active")

    # Drop the old 'active' column
    op.drop_column("tasks", "active")

    # Remove server default from 'enabled'
    with op.batch_alter_table("tasks") as batch_op:
        batch_op.alter_column("enabled", server_default=None)


def downgrade() -> None:
    # Add back the old 'active' column
    op.add_column(
        "tasks",
        sa.Column("active", sa.Boolean(), nullable=False, server_default=sa.true()),
    )

    # Copy data from 'enabled' to 'active'
    op.execute("UPDATE tasks SET active = enabled")

    # Drop the new 'enabled' column
    op.drop_column("tasks", "enabled")

    # Remove server default from 'active'
    with op.batch_alter_table("tasks") as batch_op:
        batch_op.alter_column("active", server_default=None)