"""Add app_metadata table for storing application-level metadata like last_task_update.

Revision ID: 20251110_app_metadata
Revises: 20251109_task_fields
Create Date: 2025-11-10
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

# revision identifiers, used by Alembic.
revision = "20251110_add_app_metadata_table"
down_revision = "20251109_task_fields"
branch_labels = None
depends_on = None


def upgrade() -> None:
    """Create app_metadata table."""
    op.create_table(
        "app_metadata",
        sa.Column("key", sa.String(255), nullable=False, primary_key=True),
        sa.Column("value", sa.DateTime(), nullable=False),
        sa.Column("updated_at", sa.DateTime(), nullable=False, server_default=sa.func.now()),
    )
    op.create_index(op.f("ix_app_metadata_key"), "app_metadata", ["key"], unique=False)


def downgrade() -> None:
    """Drop app_metadata table."""
    op.drop_index(op.f("ix_app_metadata_key"), table_name="app_metadata")
    op.drop_table("app_metadata")

