"""remove_revision_from_tasks

Revision ID: 9215f41a8633
Revises: 20250115_debug_logs
Create Date: 2025-12-27 11:46:15.079838

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '9215f41a8633'
down_revision: Union[str, None] = '20250115_debug_logs'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Удалить колонку revision из таблицы tasks.
    
    Поле revision больше не используется в системе. Конфликты разрешаются
    по времени обновления (updated_at), а не по ревизиям.
    """
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    
    # Проверяем, существует ли колонка revision
    existing_columns = {col["name"] for col in inspector.get_columns("tasks")}
    
    if "revision" in existing_columns:
        with op.batch_alter_table("tasks") as batch_op:
            batch_op.drop_column("revision")


def downgrade() -> None:
    """Восстановить колонку revision в таблице tasks."""
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    
    # Проверяем, существует ли колонка revision
    existing_columns = {col["name"] for col in inspector.get_columns("tasks")}
    
    if "revision" not in existing_columns:
        with op.batch_alter_table("tasks") as batch_op:
            # Добавляем колонку с дефолтным значением 0
            batch_op.add_column(
                sa.Column("revision", sa.Integer(), nullable=False, server_default="0")
            )
        # Удаляем server_default после добавления колонки
        with op.batch_alter_table("tasks") as batch_op:
            batch_op.alter_column("revision", server_default=None)

