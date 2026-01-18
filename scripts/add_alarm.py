from backend.database import engine
from sqlalchemy import text

with engine.connect() as conn:
    try:
        conn.execute(text('ALTER TABLE tasks ADD COLUMN alarm BOOLEAN DEFAULT FALSE NOT NULL'))
        conn.commit()
        print('Added alarm column')
    except Exception as e:
        print(f'Error: {e}')