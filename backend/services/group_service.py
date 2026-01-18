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
        # Check if group with same name already exists
        existing = db.query(Group).filter(Group.name == group_data.name).first()
        if existing:
            raise ValueError(f"Group with name '{group_data.name}' already exists")
        
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
    def update_group(db: Session, group_id: int, group_data: "GroupUpdate", resolve_conflicts: bool = False, timestamp: datetime | None = None) -> Group | None:
        """Update a group."""
        group = db.query(Group).filter(Group.id == group_id).first()
        if not group:
            return None

        update_data = group_data.model_dump(exclude_unset=True)

        # Conflict resolution for sync operations
        if resolve_conflicts and timestamp is not None:
            from backend.services.conflict_resolver import ConflictResolver

            # Create client group data for conflict resolution
            client_group_data = group.__dict__.copy()
            client_group_data.update(update_data)
            client_group_data['updated_at'] = timestamp

            # Determine changed fields
            changed_fields = list(update_data.keys())

            # Apply conflict resolution
            winner, resolved_data = ConflictResolver.resolve_group_conflict(
                client_group_data, group.__dict__, changed_fields
            )

            if winner == 'server':
                # Server wins - no changes needed
                return group
            # Client wins - proceed with update
            update_data = {k: v for k, v in resolved_data.items() if k in update_data}
        
        # Check if name is being updated and if it conflicts with existing group
        if "name" in update_data:
            existing = db.query(Group).filter(
                Group.name == update_data["name"],
                Group.id != group_id
            ).first()
            if existing:
                raise ValueError(f"Group with name '{update_data['name']}' already exists")
        
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

