"""Add task history table

Revision ID: a1b2c3d4e5f6
Revises: 65b1a7f858d4
Create Date: 2025-01-15 12:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'a1b2c3d4e5f6'
down_revision: Union[str, None] = '65b1a7f858d4'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Create task_history table and add last_shown_at to tasks."""
    # Add last_shown_at to tasks table
    with op.batch_alter_table('tasks', schema=None) as batch_op:
        batch_op.add_column(sa.Column('last_shown_at', sa.DateTime(), nullable=True))
    
    # Create task_history table
    op.create_table(
        'task_history',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('task_id', sa.Integer(), nullable=False),
        sa.Column('action', sa.Enum('CREATED', 'FIRST_SHOWN', 'CONFIRMED', 'UNCONFIRMED', 
                                    'EDITED', 'DELETED', 'ACTIVATED', 'DEACTIVATED', 
                                    name='taskhistoryaction'), nullable=False),
        sa.Column('action_timestamp', sa.DateTime(), nullable=False),
        sa.Column('iteration_date', sa.DateTime(), nullable=True),
        sa.Column('meta_data', sa.Text(), nullable=True),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_task_history_id'), 'task_history', ['id'], unique=False)
    op.create_index(op.f('ix_task_history_task_id'), 'task_history', ['task_id'], unique=False)
    op.create_index(op.f('ix_task_history_action'), 'task_history', ['action'], unique=False)
    op.create_index(op.f('ix_task_history_action_timestamp'), 'task_history', ['action_timestamp'], unique=False)
    op.create_index(op.f('ix_task_history_iteration_date'), 'task_history', ['iteration_date'], unique=False)
    op.create_foreign_key('fk_task_history_task_id', 'task_history', 'tasks', ['task_id'], ['id'], ondelete='CASCADE')


def downgrade() -> None:
    """Drop task_history table and remove last_shown_at from tasks."""
    op.drop_constraint('fk_task_history_task_id', 'task_history', type_='foreignkey')
    op.drop_index(op.f('ix_task_history_iteration_date'), table_name='task_history')
    op.drop_index(op.f('ix_task_history_action_timestamp'), table_name='task_history')
    op.drop_index(op.f('ix_task_history_action'), table_name='task_history')
    op.drop_index(op.f('ix_task_history_task_id'), table_name='task_history')
    op.drop_index(op.f('ix_task_history_id'), table_name='task_history')
    op.drop_table('task_history')
    
    # Remove last_shown_at from tasks table
    with op.batch_alter_table('tasks', schema=None) as batch_op:
        batch_op.drop_column('last_shown_at')
