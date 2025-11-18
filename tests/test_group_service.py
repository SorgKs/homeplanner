"""Unit tests for GroupService."""

from collections.abc import Generator
from typing import TYPE_CHECKING

import pytest

from backend.database import Base
from backend.schemas.group import GroupCreate, GroupUpdate
from backend.services.group_service import GroupService
from tests.utils import create_sqlite_engine

if TYPE_CHECKING:
    from _pytest.fixtures import FixtureRequest
    from sqlalchemy.orm import Session


# Test database
engine, SessionLocal = create_sqlite_engine("test_group_service.db")


@pytest.fixture(scope="module")
def db_setup() -> Generator[None, None, None]:
    """Create test database schema once per module."""
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


@pytest.fixture(scope="function")
def db_session(db_setup: None) -> Generator["Session", None, None]:
    """Create test database session and clean data between tests using optimized DELETE."""
    from sqlalchemy import text

    db = SessionLocal()
    try:
        # Clean all tables before each test
        # Disable foreign key checks for faster deletion, then re-enable
        with db.begin():
            db.execute(text("PRAGMA foreign_keys = OFF"))
            # Delete all data - order doesn't matter with FK checks disabled
            db.execute(text("DELETE FROM task_users"))
            db.execute(text("DELETE FROM task_history"))
            db.execute(text("DELETE FROM tasks"))
            db.execute(text("DELETE FROM events"))
            db.execute(text("DELETE FROM groups"))
            db.execute(text("DELETE FROM users"))
            db.execute(text("DELETE FROM app_metadata"))
            db.execute(text("PRAGMA foreign_keys = ON"))
        db.commit()
        yield db
    finally:
        db.rollback()
        db.close()


class TestGroupService:
    """Unit tests for GroupService."""

    def test_create_group(self, db_session: "Session") -> None:
        """Test creating a group."""
        group_data = GroupCreate(
            name="Test Group",
            description="Test Description",
        )
        
        group = GroupService.create_group(db_session, group_data)
        
        assert group.id is not None
        assert group.name == "Test Group"
        assert group.description == "Test Description"

    def test_get_group(self, db_session: "Session") -> None:
        """Test getting a group by ID."""
        group_data = GroupCreate(name="Test Group")
        
        created_group = GroupService.create_group(db_session, group_data)
        group = GroupService.get_group(db_session, created_group.id)
        
        assert group is not None
        assert group.id == created_group.id
        assert group.name == "Test Group"

    def test_get_group_not_found(self, db_session: "Session") -> None:
        """Test getting a non-existent group."""
        group = GroupService.get_group(db_session, 999)
        assert group is None

    def test_get_all_groups(self, db_session: "Session") -> None:
        """Test getting all groups."""
        group1_data = GroupCreate(name="Group 1")
        group2_data = GroupCreate(name="Group 2")
        
        GroupService.create_group(db_session, group1_data)
        GroupService.create_group(db_session, group2_data)
        
        groups = GroupService.get_all_groups(db_session)
        
        assert len(groups) == 2
        # Groups should be ordered by name
        assert groups[0].name == "Group 1"
        assert groups[1].name == "Group 2"

    def test_update_group(self, db_session: "Session") -> None:
        """Test updating a group."""
        group_data = GroupCreate(name="Original Name")
        
        created_group = GroupService.create_group(db_session, group_data)
        
        update_data = GroupUpdate(name="Updated Name")
        updated_group = GroupService.update_group(db_session, created_group.id, update_data)
        
        assert updated_group is not None
        assert updated_group.name == "Updated Name"
        assert updated_group.id == created_group.id

    def test_update_group_not_found(self, db_session: "Session") -> None:
        """Test updating a non-existent group."""
        update_data = GroupUpdate(name="Updated Name")
        updated_group = GroupService.update_group(db_session, 999, update_data)
        assert updated_group is None

    def test_delete_group(self, db_session: "Session") -> None:
        """Test deleting a group."""
        group_data = GroupCreate(name="Test Group")
        
        created_group = GroupService.create_group(db_session, group_data)
        success = GroupService.delete_group(db_session, created_group.id)
        
        assert success is True
        
        group = GroupService.get_group(db_session, created_group.id)
        assert group is None

    def test_delete_group_not_found(self, db_session: "Session") -> None:
        """Test deleting a non-existent group."""
        success = GroupService.delete_group(db_session, 999)
        assert success is False

