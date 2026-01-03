"""Add test user to database."""

import sys
from pathlib import Path

# Add backend to path
backend_path = Path(__file__).parent.parent / "backend"
sys.path.insert(0, str(backend_path))

from backend.database import SessionLocal
from backend.services.user_service import UserService
from backend.schemas.user import UserCreate

def main():
    db = SessionLocal()
    try:
        user_data = UserCreate(
            name="Test User",
            email="test@example.com",
            role="regular",
            status="active"
        )
        user = UserService.create_user(db, user_data)
        print(f"Created user: {user.name} (id: {user.id})")
    finally:
        db.close()

if __name__ == "__main__":
    main()