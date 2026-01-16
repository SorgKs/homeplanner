"""empty message

Revision ID: 829794682916
Revises: 20260105_rename_active_to_enabled, 9215f41a8633, c7d8e9f0ab12
Create Date: 2026-01-13 22:06:16.514344

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '829794682916'
down_revision: Union[str, None] = ('20260105_rename_active_to_enabled', '9215f41a8633', 'c7d8e9f0ab12')
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    pass


def downgrade() -> None:
    pass

