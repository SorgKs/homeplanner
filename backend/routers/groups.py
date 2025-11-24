"""API router for groups."""

from typing import TYPE_CHECKING

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from backend.database import get_db
from backend.schemas.group import GroupCreate, GroupResponse, GroupUpdate
from backend.services.group_service import GroupService

if TYPE_CHECKING:
    pass

router = APIRouter()


@router.post("/", response_model=GroupResponse, status_code=status.HTTP_201_CREATED)
def create_group(
    group: GroupCreate,
    db: Session = Depends(get_db),
) -> GroupResponse:
    """Create a new group."""
    try:
        created_group = GroupService.create_group(db, group)
        return GroupResponse.model_validate(created_group)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=str(e),
        )


@router.get("/", response_model=list[GroupResponse])
def get_groups(
    db: Session = Depends(get_db),
) -> list[GroupResponse]:
    """Get all groups."""
    groups = GroupService.get_all_groups(db)
    return [GroupResponse.model_validate(group) for group in groups]


@router.get("/{group_id}", response_model=GroupResponse)
def get_group(
    group_id: int,
    db: Session = Depends(get_db),
) -> GroupResponse:
    """Get a specific group by ID."""
    group = GroupService.get_group(db, group_id)
    if not group:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Group with id {group_id} not found",
        )
    return GroupResponse.model_validate(group)


@router.put("/{group_id}", response_model=GroupResponse)
def update_group(
    group_id: int,
    group_update: GroupUpdate,
    db: Session = Depends(get_db),
) -> GroupResponse:
    """Update a group."""
    try:
        updated_group = GroupService.update_group(db, group_id, group_update)
        if not updated_group:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Group with id {group_id} not found",
            )
        return GroupResponse.model_validate(updated_group)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=str(e),
        )


@router.delete("/{group_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_group(
    group_id: int,
    db: Session = Depends(get_db),
) -> None:
    """Delete a group."""
    success = GroupService.delete_group(db, group_id)
    if not success:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Group with id {group_id} not found",
        )

