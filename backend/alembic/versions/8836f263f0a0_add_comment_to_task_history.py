"""add_comment_to_task_history

Revision ID: 8836f263f0a0
Revises: a1b2c3d4e5f6
Create Date: 2025-11-02 20:01:38.838094

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '8836f263f0a0'
down_revision: Union[str, None] = 'a1b2c3d4e5f6'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('task_history', sa.Column('comment', sa.Text(), nullable=True))


def downgrade() -> None:
    op.drop_column('task_history', 'comment')

