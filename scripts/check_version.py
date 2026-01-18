from backend.database import engine
from sqlalchemy import text

with engine.connect() as conn:
    result = conn.execute(text('SELECT version_num FROM alembic_version')).fetchone()
    print('Current version:', result[0] if result else 'None')