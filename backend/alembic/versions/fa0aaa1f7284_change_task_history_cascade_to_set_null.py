"""change_task_history_cascade_to_set_null

Revision ID: fa0aaa1f7284
Revises: 91afd8b8effa
Create Date: 2025-11-02 21:30:38.982814

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'fa0aaa1f7284'
down_revision: Union[str, None] = '91afd8b8effa'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # SQLite doesn't support DROP CONSTRAINT for foreign keys
    # Need to recreate the table with SET NULL instead of CASCADE
    # First, get all data
    conn = op.get_bind()
    result = conn.execute(sa.text("SELECT * FROM task_history"))
    data = result.fetchall()
    
    # Drop old table
    op.drop_table('task_history')
    
    # Create new table with nullable task_id and SET NULL
    op.create_table(
        'task_history',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('task_id', sa.Integer(), nullable=True),
        sa.Column('action', sa.Enum('CREATED', 'FIRST_SHOWN', 'CONFIRMED', 'UNCONFIRMED', 
                                    'EDITED', 'DELETED', 'ACTIVATED', 'DEACTIVATED', 
                                    name='taskhistoryaction'), nullable=False),
        sa.Column('action_timestamp', sa.DateTime(), nullable=False),
        sa.Column('iteration_date', sa.DateTime(), nullable=True),
        sa.Column('meta_data', sa.Text(), nullable=True),
        sa.Column('comment', sa.Text(), nullable=True),
        sa.PrimaryKeyConstraint('id'),
        sa.ForeignKeyConstraint(['task_id'], ['tasks.id'], ondelete='SET NULL')
    )
    op.create_index(op.f('ix_task_history_id'), 'task_history', ['id'], unique=False)
    op.create_index(op.f('ix_task_history_task_id'), 'task_history', ['task_id'], unique=False)
    op.create_index(op.f('ix_task_history_action'), 'task_history', ['action'], unique=False)
    op.create_index(op.f('ix_task_history_action_timestamp'), 'task_history', ['action_timestamp'], unique=False)
    op.create_index(op.f('ix_task_history_iteration_date'), 'task_history', ['iteration_date'], unique=False)
    
    # Restore data
    if data:
        # Get column names from old data (before table was dropped)
        columns = ['id', 'task_id', 'action', 'action_timestamp', 'iteration_date', 'meta_data', 'comment']
        placeholders = ','.join(['?' for _ in columns])
        insert_stmt = f"INSERT INTO task_history ({','.join(columns)}) VALUES ({placeholders})"
        # Execute for each row - note: row is already a tuple, so we just pass the values directly
        for row in data:
            # Use raw SQLite connection for proper parameter binding
            raw_conn = conn.connection.driver_connection if hasattr(conn.connection, 'driver_connection') else conn.connection
            cursor = raw_conn.cursor()
            cursor.execute(insert_stmt, row)
            raw_conn.commit()
            cursor.close()


def downgrade() -> None:
    # Revert to CASCADE and non-nullable - recreate with CASCADE
    conn = op.get_bind()
    result = conn.execute(sa.text("SELECT * FROM task_history"))
    data = result.fetchall()
    
    op.drop_table('task_history')
    
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
        sa.Column('comment', sa.Text(), nullable=True),
        sa.PrimaryKeyConstraint('id'),
        sa.ForeignKeyConstraint(['task_id'], ['tasks.id'], ondelete='CASCADE')
    )
    op.create_index(op.f('ix_task_history_id'), 'task_history', ['id'], unique=False)
    op.create_index(op.f('ix_task_history_task_id'), 'task_history', ['task_id'], unique=False)
    op.create_index(op.f('ix_task_history_action'), 'task_history', ['action'], unique=False)
    op.create_index(op.f('ix_task_history_action_timestamp'), 'task_history', ['action_timestamp'], unique=False)
    op.create_index(op.f('ix_task_history_iteration_date'), 'task_history', ['iteration_date'], unique=False)
    
    if data:
        columns = ['id', 'task_id', 'action', 'action_timestamp', 'iteration_date', 'meta_data', 'comment']
        placeholders = ','.join(['?' for _ in columns])
        insert_stmt = f"INSERT INTO task_history ({','.join(columns)}) VALUES ({placeholders})"
        for row in data:
            conn.execute(sa.text(insert_stmt), row)
        conn.commit()

