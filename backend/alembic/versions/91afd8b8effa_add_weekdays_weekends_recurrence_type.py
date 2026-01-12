"""add_weekdays_weekends_recurrence_type

Revision ID: 91afd8b8effa
Revises: 8836f263f0a0
Create Date: 2025-11-02 21:00:24.004450

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '91afd8b8effa'
down_revision: Union[str, None] = '8836f263f0a0'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    pass


def downgrade() -> None:
    pass

