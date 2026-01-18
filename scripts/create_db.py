#!/usr/bin/env python3

import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

from backend.models import *  # noqa
from backend.database import init_db

if __name__ == "__main__":
    init_db()
    print("Database initialized")