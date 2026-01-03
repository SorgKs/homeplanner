"""Service for managing users."""

import logging
from typing import TYPE_CHECKING

from sqlalchemy.orm import Session

from backend.models.user import User

if TYPE_CHECKING:
    from backend.schemas.user import UserCreate, UserUpdate


class UserService:
    """Business logic for users."""

    @staticmethod
    def create_user(db: Session, user_data: "UserCreate") -> User:
        payload = user_data.model_dump()
        if payload.get("email") == "":
            payload["email"] = None
        user = User(**payload)
        db.add(user)
        db.commit()
        db.refresh(user)
        return user

    @staticmethod
    def get_user(db: Session, user_id: int) -> User | None:
        return db.query(User).filter(User.id == user_id).first()

    @staticmethod
    def get_all_users(db: Session) -> list[User]:
        logger = logging.getLogger("homeplanner.users")
        logger.info("Starting query for all users")
        try:
            users = db.query(User).order_by(User.name).all()
            logger.info(f"Query executed successfully, found {len(users)} users")
            if not users:
                logger.warning("No users found in database - checking if table exists")
                # Check if table exists
                from sqlalchemy import inspect
                inspector = inspect(db.bind)
                tables = inspector.get_table_names()
                logger.warning(f"Available tables: {tables}")
                if 'users' not in tables:
                    logger.error("Users table does not exist in database!")
                else:
                    logger.warning("Users table exists but is empty")
            else:
                logger.debug(f"Users: {[{'id': u.id, 'name': u.name, 'email': u.email, 'is_active': u.is_active} for u in users]}")
            return users
        except Exception as e:
            logger.error(f"Error querying users: {e}", exc_info=True)
            return []

    @staticmethod
    def update_user(db: Session, user_id: int, user_data: "UserUpdate") -> User | None:
        user = db.query(User).filter(User.id == user_id).first()
        if not user:
            return None
        update_data = user_data.model_dump(exclude_unset=True)
        if "email" in update_data and (update_data["email"] == "" or update_data["email"] is None):
            update_data["email"] = None
        for key, value in update_data.items():
            setattr(user, key, value)
        db.commit()
        db.refresh(user)
        return user

    @staticmethod
    def get_simple_users(db: Session) -> list[tuple[int, str]]:
        """Get active users with only id and name."""
        logger = logging.getLogger("homeplanner.users")
        logger.info("Starting query for simple users")
        try:
            users = db.query(User.id, User.name).filter(User.is_active == True).order_by(User.name).all()
            logger.info(f"Query executed successfully, found {len(users)} active users")
            return users
        except Exception as e:
            logger.error(f"Error querying simple users: {e}", exc_info=True)
            return []

    @staticmethod
    def delete_user(db: Session, user_id: int) -> bool:
        user = db.query(User).filter(User.id == user_id).first()
        if not user:
            return False
        db.delete(user)
        db.commit()
        return True


