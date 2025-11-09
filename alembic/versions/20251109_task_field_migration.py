"""Миграция структуры задач: обязательный reminder_time, completed/active флаги.

Основные изменения:
- делаем `reminder_time` обязательным, заполняя пустые значения данными из `next_due_date`;
- переносим `is_active` → `active`;
- добавляем `completed` (замена `last_completed_at`);
- удаляем колонку `next_due_date` и `last_completed_at`.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any, Mapping

import sqlalchemy as sa
from alembic import op

# revision identifiers, used by Alembic.
revision = "20251109_task_fields"
down_revision = "cf2a3d7f4b6e"
branch_labels = None
depends_on = None


def _table_columns(inspector: sa.engine.reflection.Inspector, table_name: str) -> set[str]:
    """Получить множество колонок таблицы."""

    return {col["name"] for col in inspector.get_columns(table_name)}


def _drop_index_if_exists(
    inspector: sa.engine.reflection.Inspector,
    table: str,
    column_name: str,
) -> None:
    """Удалить индекс по имени колонки, если он существует."""

    for index in inspector.get_indexes(table):
        if column_name in index.get("column_names", []):
            op.drop_index(index["name"], table_name=table)
            break


def _load_tasks_columns(
    connection: sa.engine.Connection,
    present_columns: set[str],
) -> list[Mapping[str, Any]]:
    """Загрузить данные из таблицы tasks только по нужным колонкам."""

    columns: list[sa.ColumnElement[Any]] = [
        sa.column("id"),
        sa.column("reminder_time"),
    ]
    optional_columns = [
        "next_due_date",
        "last_completed_at",
        "is_active",
        "completed",
        "active",
    ]
    for column_name in optional_columns:
        if column_name in present_columns:
            columns.append(sa.column(column_name))

    tasks_table = sa.table("tasks", *columns)
    return list(connection.execute(sa.select(*columns)))


def upgrade() -> None:
    """Применить миграцию."""

    bind = op.get_bind()
    inspector = sa.inspect(bind)
    columns = _table_columns(inspector, "tasks")

    # 1. Добавляем недостающие колонки completed/active.
    with op.batch_alter_table("tasks") as batch_op:
        if "completed" not in columns:
            batch_op.add_column(sa.Column("completed", sa.Boolean(), nullable=False, server_default=sa.text("0")))
        if "active" not in columns:
            batch_op.add_column(sa.Column("active", sa.Boolean(), nullable=False, server_default=sa.text("1")))

    # Обновляем набор колонок после добавления.
    columns = _table_columns(inspector, "tasks")

    connection = bind.connect()
    current_date = datetime.now().date()
    tasks_data = _load_tasks_columns(connection, columns)
    tasks_table = sa.table(
        "tasks",
        sa.column("id", sa.Integer),
        sa.column("reminder_time", sa.DateTime),
        sa.column("next_due_date", sa.DateTime),
        sa.column("last_completed_at", sa.DateTime),
        sa.column("completed", sa.Boolean),
        sa.column("is_active", sa.Boolean),
        sa.column("active", sa.Boolean),
    )

    for row in tasks_data:
        updates: dict[str, Any] = {}

        reminder_time = row.get("reminder_time")
        next_due_date = row.get("next_due_date")
        last_completed_at = row.get("last_completed_at")
        completed = row.get("completed")
        is_active = row.get("is_active")
        active = row.get("active")

        if reminder_time is None:
            if next_due_date is not None:
                updates["reminder_time"] = next_due_date
            else:
                updates["reminder_time"] = datetime.now()

        if completed is None:
            updates["completed"] = bool(
                last_completed_at and last_completed_at.date() == current_date
            )

        if active is None:
            updates["active"] = True if is_active is None else bool(is_active)

        if updates:
            connection.execute(
                tasks_table.update()
                .where(tasks_table.c.id == row["id"])
                .values(**updates)
            )

    # Сбрасываем server_default для новых колонок.
    with op.batch_alter_table("tasks") as batch_op:
        batch_op.alter_column(
            "completed",
            server_default=None,
            existing_type=sa.Boolean(),
        )
        batch_op.alter_column(
            "active",
            server_default=None,
            existing_type=sa.Boolean(),
        )

    # 2. Делаем reminder_time NOT NULL (после заполнения).
    with op.batch_alter_table("tasks") as batch_op:
        batch_op.alter_column(
            "reminder_time",
            existing_type=sa.DateTime(),
            nullable=False,
        )

    # 3. Переносим is_active → active, удаляем last_completed_at / next_due_date.
    if "next_due_date" in columns:
        _drop_index_if_exists(inspector, "tasks", "next_due_date")

    with op.batch_alter_table("tasks") as batch_op:
        if "is_active" in columns:
            batch_op.drop_column("is_active")
        if "last_completed_at" in columns:
            batch_op.drop_column("last_completed_at")
        if "next_due_date" in columns:
            batch_op.drop_column("next_due_date")


def downgrade() -> None:
    """Откатить миграцию."""

    bind = op.get_bind()
    inspector = sa.inspect(bind)
    columns = _table_columns(inspector, "tasks")

    with op.batch_alter_table("tasks") as batch_op:
        if "next_due_date" not in columns:
            batch_op.add_column(sa.Column("next_due_date", sa.DateTime(), nullable=True))
        if "last_completed_at" not in columns:
            batch_op.add_column(sa.Column("last_completed_at", sa.DateTime(), nullable=True))
        if "is_active" not in columns:
            batch_op.add_column(sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.text("1")))

    with op.batch_alter_table("tasks") as batch_op:
        batch_op.alter_column(
            "is_active",
            existing_type=sa.Boolean(),
            server_default=None,
        )

    connection = bind.connect()
    tasks_table = sa.table(
        "tasks",
        sa.column("id", sa.Integer),
        sa.column("reminder_time", sa.DateTime),
        sa.column("next_due_date", sa.DateTime),
        sa.column("last_completed_at", sa.DateTime),
        sa.column("completed", sa.Boolean),
        sa.column("is_active", sa.Boolean),
        sa.column("active", sa.Boolean),
    )

    rows = list(
        connection.execute(
            sa.select(
                tasks_table.c.id,
                tasks_table.c.reminder_time,
                tasks_table.c.completed,
                tasks_table.c.active,
            )
        )
    )

    for row in rows:
        connection.execute(
            tasks_table.update()
            .where(tasks_table.c.id == row.id)
            .values(
                next_due_date=row.reminder_time,
                is_active=True if row.active is None else bool(row.active),
                completed=row.completed,
            )
        )

    op.create_index(
        op.f("ix_tasks_next_due_date"),
        "tasks",
        ["next_due_date"],
        unique=False,
    )

    # Возвращаем reminder_time к nullable.
    with op.batch_alter_table("tasks") as batch_op:
        batch_op.alter_column(
            "reminder_time",
            existing_type=sa.DateTime(),
            nullable=True,
        )

    # Удаляем новые колонки.
    with op.batch_alter_table("tasks") as batch_op:
        batch_op.drop_column("active")
        batch_op.drop_column("completed")


