"""remove alarm_time column from tasks

Revision ID: 3380f776dfe9
Revises: d94c8af5aff3
Create Date: 2026-01-14 00:30:51.717125

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '3380f776dfe9'
down_revision: Union[str, None] = 'd94c8af5aff3'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Remove alarm_time column from tasks table
    op.drop_column('tasks', 'alarm_time')


def downgrade() -> None:
    # Add back alarm_time column to tasks table
    op.add_column('tasks', sa.Column('alarm_time', sa.DateTime(), nullable=True))

