"""Add role and status columns to users

Revision ID: c7d8e9f0ab12
Revises: b4c5d6e7f8a9
Create Date: 2025-11-14 15:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "c7d8e9f0ab12"
down_revision: Union[str, None] = "b4c5d6e7f8a9"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column(
            "role",
            sa.Enum("admin", "regular", "guest", name="userrole"),
            nullable=False,
            server_default="regular",
        ),
    )
    op.add_column(
        "users",
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.true()),
    )
    # Drop server defaults after backfilling
    with op.batch_alter_table("users") as batch_op:
        batch_op.alter_column("role", server_default=None)
        batch_op.alter_column("is_active", server_default=None)


def downgrade() -> None:
    with op.batch_alter_table("users") as batch_op:
        batch_op.drop_column("is_active")
        batch_op.drop_column("role")

    bind = op.get_bind()
    if bind.dialect.name != "sqlite":
        op.execute("DROP TYPE userrole")


