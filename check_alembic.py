#!/usr/bin/env python3

import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

from backend.database import engine
from sqlalchemy import text

def check_alembic_version():
    with engine.connect() as conn:
        result = conn.execute(text('SELECT version_num FROM alembic_version')).fetchone()
        print('Alembic version:', result[0] if result else 'None')

if __name__ == "__main__":
    check_alembic_version()