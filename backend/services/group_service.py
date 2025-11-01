"""Service for group business logic."""

from typing import TYPE_CHECKING

from sqlalchemy.orm import Session

from backend.models.group import Group

if TYPE_CHECKING:
    from backend.schemas.group import GroupCreate, GroupUpdate


class GroupService:
    """Service for managing task groups."""

    @staticmethod
    def create_group(db: Session, group_data: "GroupCreate") -> Group:
        """Create a new group."""
        group = Group(**group_data.model_dump())
        db.add(group)
        db.commit()
        db.refresh(group)
        return group

    @staticmethod
    def get_group(db: Session, group_id: int) -> Group | None:
        """Get a group by ID."""
        return db.query(Group).filter(Group.id == group_id).first()

    @staticmethod
    def get_all_groups(db: Session) -> list[Group]:
        """Get all groups."""
        return db.query(Group).order_by(Group.name).all()

    @staticmethod
    def update_group(db: Session, group_id: int, group_data: "GroupUpdate") -> Group | None:
        """Update a group."""
        group = db.query(Group).filter(Group.id == group_id).first()
        if not group:
            return None

        update_data = group_data.model_dump(exclude_unset=True)
        for key, value in update_data.items():
            setattr(group, key, value)

        db.commit()
        db.refresh(group)
        return group

    @staticmethod
    def delete_group(db: Session, group_id: int) -> bool:
        """Delete a group."""
        group = db.query(Group).filter(Group.id == group_id).first()
        if not group:
            return False

        db.delete(group)
        db.commit()
        return True

