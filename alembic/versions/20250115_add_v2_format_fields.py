"""Add v2 format fields to debug_logs

Revision ID: 20250115_add_v2_format_fields
Revises: update_debug_logs_schema
Create Date: 2025-01-15 12:00:00.000000

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = '20250115_add_v2_format_fields'
down_revision = 'update_debug_logs_schema'
branch_labels = None
depends_on = None


def upgrade() -> None:
    """Add text and chunk_id fields for v2 format support."""
    # Add text field for decoded messages (v2 format)
    op.add_column('debug_logs', sa.Column('text', sa.Text(), nullable=True))
    
    # Add chunk_id field for tracking binary chunks
    op.add_column('debug_logs', sa.Column('chunk_id', sa.String(length=50), nullable=True))
    
    # Add index on chunk_id for faster lookups
    op.create_index('idx_debug_logs_chunk_id', 'debug_logs', ['chunk_id'], unique=False)
    
    # Make message_code nullable (for v2 format logs that use text instead)
    with op.batch_alter_table('debug_logs') as batch_op:
        batch_op.alter_column('message_code',
                              existing_type=sa.String(length=100),
                              nullable=True)


def downgrade() -> None:
    """Remove v2 format fields."""
    op.drop_index('idx_debug_logs_chunk_id', table_name='debug_logs')
    op.drop_column('debug_logs', 'chunk_id')
    op.drop_column('debug_logs', 'text')
    
    # Restore message_code as non-nullable
    with op.batch_alter_table('debug_logs') as batch_op:
        batch_op.alter_column('message_code',
                              existing_type=sa.String(length=100),
                              nullable=False)

