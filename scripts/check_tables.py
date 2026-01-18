#!/usr/bin/env python3

import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

from backend.database import engine
from sqlalchemy import inspect

def check_tables():
    inspector = inspect(engine)
    tables = inspector.get_table_names()
    print("Available tables in the database:")
    for table in sorted(tables):
        print(f"  - {table}")

def check_alarm_column():
    inspector = inspect(engine)
    columns = inspector.get_columns('tasks')
    print("\nColumns in 'tasks' table:")
    alarm_present = False
    for column in columns:
        print(f"  - {column['name']}: {column['type']}")
        if column['name'] == 'alarm':
            alarm_present = True
    print(f"\nAlarm column present: {alarm_present}")

if __name__ == "__main__":
    check_tables()
    check_alarm_column()