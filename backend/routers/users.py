"""API router for users."""

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from backend.database import get_db
from backend.schemas.user import UserCreate, UserResponse, UserSimple, UserUpdate
from backend.services.user_service import UserService

router = APIRouter()


@router.post("/", response_model=UserResponse, status_code=status.HTTP_201_CREATED)
def create_user(user: UserCreate, db: Session = Depends(get_db)) -> UserResponse:
    """Create a new user."""
    created = UserService.create_user(db, user)
    return UserResponse.model_validate(created)


@router.get("/", response_model=list[UserResponse] | list[UserSimple])
def list_users(simple: bool = Query(False, description="Simplified response with only active users and basic fields"), db: Session = Depends(get_db)) -> list[UserResponse] | list[UserSimple]:
    """List all users or active users with simplified fields."""
    import logging
    logger = logging.getLogger("homeplanner.api.users")
    logger.info(f"GET /users endpoint called with simple={simple}")

    if simple:
        users = UserService.get_simple_users(db)
        logger.info(f"Returning {len(users)} active users (simple mode) via API")
        return [UserSimple(id=user_id, name=name) for user_id, name in users]
    else:
        users = UserService.get_all_users(db)
        logger.info(f"Returning {len(users)} users (full mode) via API")
        return [UserResponse.model_validate(user) for user in users]


@router.get("/{user_id}", response_model=UserResponse)
def get_user(user_id: int, db: Session = Depends(get_db)) -> UserResponse:
    """Get a single user."""
    user = UserService.get_user(db, user_id)
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    return UserResponse.model_validate(user)


@router.put("/{user_id}", response_model=UserResponse)
def update_user(user_id: int, user_update: UserUpdate, db: Session = Depends(get_db)) -> UserResponse:
    """Update a user."""
    user = UserService.update_user(db, user_id, user_update)
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    return UserResponse.model_validate(user)


@router.delete("/{user_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_user(user_id: int, db: Session = Depends(get_db)) -> None:
    """Delete a user."""
    success = UserService.delete_user(db, user_id)
    if not success:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")


