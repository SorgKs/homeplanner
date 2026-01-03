"""Check users in database."""

import sys
from pathlib import Path

# Add backend to path
backend_path = Path(__file__).parent.parent / "backend"
sys.path.insert(0, str(backend_path))

from backend.database import SessionLocal
from backend.services.user_service import UserService

def main():
    db = SessionLocal()
    try:
        users = UserService.get_all_users(db)
        print(f"Found {len(users)} users:")
        for user in users:
            print(f"  - {user.id}: {user.name} ({user.email}) - {user.role} - active: {user.is_active}")
    finally:
        db.close()

if __name__ == "__main__":
    main()