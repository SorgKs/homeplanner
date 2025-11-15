"""Service for managing users."""

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
        return db.query(User).order_by(User.name).all()

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
    def delete_user(db: Session, user_id: int) -> bool:
        user = db.query(User).filter(User.id == user_id).first()
        if not user:
            return False
        db.delete(user)
        db.commit()
        return True


