"""Add task revision, soft locks, recurrence and notifications tables."""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

# revision identifiers, used by Alembic.
revision = "cf2a3d7f4b6e"
down_revision = "fa0aaa1f7284"
branch_labels = None
depends_on = None


def upgrade() -> None:
    """Apply schema changes for task revisioning and notification tables."""
    bind = op.get_bind()
    inspector = sa.inspect(bind)

    existing_task_columns = {col["name"] for col in inspector.get_columns("tasks")}
    columns_to_add: list[sa.Column] = []
    should_remove_default = False

    if "revision" not in existing_task_columns:
        columns_to_add.append(
            sa.Column("revision", sa.Integer(), nullable=False, server_default="0")
        )
        should_remove_default = True
    if "soft_lock_owner" not in existing_task_columns:
        columns_to_add.append(sa.Column("soft_lock_owner", sa.String(length=64), nullable=True))
    if "soft_lock_expires_at" not in existing_task_columns:
        columns_to_add.append(sa.Column("soft_lock_expires_at", sa.DateTime(), nullable=True))

    if columns_to_add:
        with op.batch_alter_table("tasks") as batch_op:
            for column in columns_to_add:
                batch_op.add_column(column)

    if should_remove_default:
        with op.batch_alter_table("tasks") as batch_op:
            batch_op.alter_column("revision", server_default=None)

    if not inspector.has_table("task_recurrence"):
        op.create_table(
            "task_recurrence",
            sa.Column("id", sa.String(length=36), nullable=False),
            sa.Column("task_id", sa.Integer(), nullable=False),
            sa.Column("rrule", sa.Text(), nullable=False),
            sa.Column("end_at", sa.DateTime(), nullable=True),
            sa.Column(
                "created_at",
                sa.DateTime(),
                nullable=False,
                server_default=sa.func.now(),
            ),
            sa.Column(
                "updated_at",
                sa.DateTime(),
                nullable=False,
                server_default=sa.func.now(),
            ),
            sa.ForeignKeyConstraint(
                ["task_id"],
                ["tasks.id"],
                ondelete="CASCADE",
            ),
            sa.PrimaryKeyConstraint("id"),
            sa.UniqueConstraint("task_id"),
        )
        op.create_index(
            op.f("ix_task_recurrence_task_id"),
            "task_recurrence",
            ["task_id"],
            unique=False,
        )

    if not inspector.has_table("task_notifications"):
        op.create_table(
            "task_notifications",
            sa.Column("id", sa.String(length=36), nullable=False),
            sa.Column("task_id", sa.Integer(), nullable=False),
            sa.Column("notification_type", sa.String(length=32), nullable=False),
            sa.Column("channel", sa.String(length=32), nullable=False),
            sa.Column("offset_minutes", sa.Integer(), nullable=False, server_default="0"),
            sa.Column(
                "created_at",
                sa.DateTime(),
                nullable=False,
                server_default=sa.func.now(),
            ),
            sa.Column(
                "updated_at",
                sa.DateTime(),
                nullable=False,
                server_default=sa.func.now(),
            ),
            sa.ForeignKeyConstraint(
                ["task_id"],
                ["tasks.id"],
                ondelete="CASCADE",
            ),
            sa.PrimaryKeyConstraint("id"),
        )
        op.create_index(
            op.f("ix_task_notifications_task_id"),
            "task_notifications",
            ["task_id"],
            unique=False,
        )


def downgrade() -> None:
    """Revert schema changes."""
    op.drop_index(op.f("ix_task_notifications_task_id"), table_name="task_notifications")
    op.drop_table("task_notifications")

    op.drop_index(op.f("ix_task_recurrence_task_id"), table_name="task_recurrence")
    op.drop_table("task_recurrence")

    with op.batch_alter_table("tasks") as batch_op:
        batch_op.drop_column("soft_lock_expires_at")
        batch_op.drop_column("soft_lock_owner")
        batch_op.drop_column("revision")

