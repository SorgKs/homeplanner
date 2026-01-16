from backend.database import engine
from sqlalchemy import text

with engine.connect() as conn:
    conn.execute(text("UPDATE alembic_version SET version_num = '3380f776dfe9'"))
    conn.commit()

print('Version set to 3380f776dfe9')