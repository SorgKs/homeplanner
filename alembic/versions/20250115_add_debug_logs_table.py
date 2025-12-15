"""Add debug_logs table for storing client debug logs.

Revision ID: 20250115_debug_logs
Revises: 20251110_app_metadata
Create Date: 2025-01-15
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

# revision identifiers, used by Alembic.
revision = "20250115_debug_logs"
down_revision = "20251110_app_metadata"
branch_labels = None
depends_on = None


def upgrade() -> None:
    """Create debug_logs table."""
    op.create_table(
        "debug_logs",
        sa.Column("id", sa.Integer(), nullable=False, primary_key=True),
        sa.Column("timestamp", sa.DateTime(), nullable=False),
        sa.Column("level", sa.String(10), nullable=False),
        sa.Column("tag", sa.String(255), nullable=True),
        sa.Column("message_code", sa.String(100), nullable=False),
        sa.Column("context", sa.Text(), nullable=True),
        sa.Column("device_id", sa.String(255), nullable=True),
        sa.Column("device_info", sa.String(255), nullable=True),
        sa.Column("app_version", sa.String(50), nullable=True),
        sa.Column("dictionary_revision", sa.String(20), nullable=True),
    )
    op.create_index(op.f("ix_debug_logs_id"), "debug_logs", ["id"], unique=False)
    op.create_index("idx_debug_logs_timestamp", "debug_logs", ["timestamp"], unique=False)
    op.create_index("idx_debug_logs_level", "debug_logs", ["level"], unique=False)
    op.create_index("idx_debug_logs_tag", "debug_logs", ["tag"], unique=False)
    op.create_index("idx_debug_logs_message_code", "debug_logs", ["message_code"], unique=False)
    op.create_index("idx_debug_logs_device_id", "debug_logs", ["device_id"], unique=False)


def downgrade() -> None:
    """Drop debug_logs table."""
    op.drop_index("idx_debug_logs_device_id", table_name="debug_logs")
    op.drop_index("idx_debug_logs_message_code", table_name="debug_logs")
    op.drop_index("idx_debug_logs_tag", table_name="debug_logs")
    op.drop_index("idx_debug_logs_level", table_name="debug_logs")
    op.drop_index("idx_debug_logs_timestamp", table_name="debug_logs")
    op.drop_index(op.f("ix_debug_logs_id"), table_name="debug_logs")
    op.drop_table("debug_logs")
