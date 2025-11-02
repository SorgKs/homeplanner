"""Unit tests for GroupService."""

from typing import TYPE_CHECKING

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from backend.database import Base
from backend.schemas.group import GroupCreate, GroupUpdate
from backend.services.group_service import GroupService

if TYPE_CHECKING:
    from _pytest.fixtures import FixtureRequest
    from sqlalchemy.orm import Session


# Test database
SQLALCHEMY_DATABASE_URL = "sqlite:///./test_group_service.db"

engine = create_engine(
    SQLALCHEMY_DATABASE_URL, connect_args={"check_same_thread": False}
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


@pytest.fixture(scope="function")
def db_session() -> "Session":
    """Create test database session."""
    Base.metadata.create_all(bind=engine)
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()
        Base.metadata.drop_all(bind=engine)


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

