"""Update debug_logs table schema to match current model

Revision ID: update_debug_logs_schema
Revises: fa0aaa1f7284
Create Date: 2025-12-13

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'update_debug_logs_schema'
down_revision: Union[str, None] = 'fa0aaa1f7284'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Drop the old table
    op.drop_table('debug_logs')
    
    # Create the new table with correct schema
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
    # Drop the new table
    op.drop_table('debug_logs')
    
    # Recreate the old table
    op.create_table(
        "debug_logs",
        sa.Column("id", sa.Integer(), nullable=False, primary_key=True),
        sa.Column("timestamp", sa.DateTime(), nullable=False),
        sa.Column("level", sa.String(10), nullable=False),
        sa.Column("tag", sa.String(255), nullable=True),
        sa.Column("message", sa.Text(), nullable=False),
        sa.Column("exception", sa.Text(), nullable=True),
        sa.Column("device_info", sa.String(255), nullable=True),
        sa.Column("app_version", sa.String(50), nullable=True),
    )
    op.create_index(op.f("ix_debug_logs_id"), "debug_logs", ["id"], unique=False)
    op.create_index("idx_debug_logs_timestamp", "debug_logs", ["timestamp"], unique=False)
    op.create_index("idx_debug_logs_level", "debug_logs", ["level"], unique=False)
    op.create_index("idx_debug_logs_tag", "debug_logs", ["tag"], unique=False)