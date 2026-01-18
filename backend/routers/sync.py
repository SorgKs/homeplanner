"""API router for sync endpoints v0.3."""

from datetime import datetime
from typing import List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from backend.database import get_db
from backend.schemas.sync_v3 import (
    HashCheckRequest,
    HashCheckResponse,
    FullStateResponse,
    ConflictResolutionRequest,
    ConflictResolutionResponse
)
from backend.services.hash_service import HashService
from backend.services.task_service import TaskService
from backend.services.user_service import UserService
from backend.services.conflict_resolver import ConflictResolver

router = APIRouter()


@router.post("/hash-check", response_model=HashCheckResponse)
def hash_check(request: HashCheckRequest, db: Session = Depends(get_db)) -> HashCheckResponse:
    """Check hashes and return differences.

    Accepts list of (entity_type, id, hash) pairs and returns:
    - conflicts: entities with different hashes
    - missing_on_client: entities that exist on server but not in client list
    - missing_on_server: entities that exist on client but not on server
    """
    entity_type = request.entity_type
    client_hashes = {item["id"]: item["hash"] for item in request.hashes}

    # Get server hashes
    server_hashes_list = HashService.get_entity_hashes(db, entity_type)
    server_hashes = {item["id"]: item["hash"] for item in server_hashes_list}

    # Find conflicts: entities that exist on both but have different hashes
    conflicts = []
    for entity_id in set(client_hashes.keys()) & set(server_hashes.keys()):
        if client_hashes[entity_id] != server_hashes[entity_id]:
            conflicts.append({
                "id": entity_id,
                "client_hash": client_hashes[entity_id],
                "server_hash": server_hashes[entity_id]
            })

    # Find missing on client: exist on server but not in client hashes
    missing_on_client = []
    for entity_id, hash_value in server_hashes.items():
        if entity_id not in client_hashes:
            missing_on_client.append({"id": entity_id, "hash": hash_value})

    # Find missing on server: exist in client hashes but not on server
    missing_on_server = []
    for entity_id, hash_value in client_hashes.items():
        if entity_id not in server_hashes:
            missing_on_server.append({"id": entity_id, "hash": hash_value})

    return HashCheckResponse(
        status="checked",
        conflicts=conflicts,
        missing_on_client=missing_on_client,
        missing_on_server=missing_on_server
    )


@router.get("/full-state/{entity_type}", response_model=FullStateResponse)
def get_full_state(entity_type: str, db: Session = Depends(get_db)) -> FullStateResponse:
    """Get full state of all entities of specified type."""
    if entity_type not in ["tasks", "users", "groups"]:
        raise HTTPException(status_code=400, detail=f"Unsupported entity type: {entity_type}")

    entities = []

    if entity_type == "tasks":
        from backend.schemas.task import TaskResponse
        tasks = TaskService.get_all_tasks(db, enabled_only=False)
        for task in tasks:
            # Convert to dict and add assigned_user_ids
            task_dict = {
                "id": task.id,
                "title": task.title,
                "description": task.description,
                "task_type": task.task_type.value if task.task_type else None,
                "recurrence_type": task.recurrence_type.value if task.recurrence_type else None,
                "recurrence_interval": task.recurrence_interval,
                "interval_days": task.interval_days,
                "reminder_time": task.reminder_time,
                "group_id": task.group_id,
                "enabled": task.enabled,
                "completed": task.completed,
                "assigned_user_ids": [user.id for user in task.assignees] if task.assignees else [],
                "updated_at": task.updated_at,
                "created_at": task.created_at
            }
            entities.append(task_dict)

    elif entity_type == "users":
        from backend.schemas.user import UserResponse
        from backend.models.user import User
        users = db.query(User).all()
        for user in users:
            user_dict = {
                "id": user.id,
                "name": user.name,
                "email": user.email,
                "role": user.role.value if user.role else None,
                "is_active": user.is_active,
                "updated_at": user.updated_at,
                "created_at": user.created_at
            }
            entities.append(user_dict)

    elif entity_type == "groups":
        from backend.schemas.group import GroupResponse
        from backend.models.group import Group
        groups = db.query(Group).all()
        for group in groups:
            group_dict = {
                "id": group.id,
                "name": group.name,
                "description": group.description,
                "updated_at": group.updated_at,
                "created_at": group.created_at,
                # Groups don't have users in current implementation
                "user_ids": []
            }
            entities.append(group_dict)

    return FullStateResponse(
        status="full_state",
        entity_type=entity_type,
        entities=entities,
        server_timestamp=datetime.now()
    )


@router.post("/resolve-conflicts", response_model=ConflictResolutionResponse)
def resolve_conflicts(request: ConflictResolutionRequest, db: Session = Depends(get_db)) -> ConflictResolutionResponse:
    """Resolve conflicts based on updated_at timestamps.

    Uses the conflict resolution rules from CACHE_STRATEGY_UPDATE_V1.md:
    - Last change wins for most fields
    - Creation has priority over deletion
    - Reminder time recalculation for certain conflicts
    """
    entity_type = request.entity_type
    resolutions = request.resolutions

    resolved_count = 0
    failed_count = 0
    details = []

    for resolution in resolutions:
        try:
            entity_id = resolution.get("id")
            if not entity_id:
                details.append({"id": entity_id, "status": "failed", "error": "Missing entity ID"})
                failed_count += 1
                continue

            if entity_type == "tasks":
                # Get current server state
                server_task = TaskService.get_task(db, entity_id)
                if not server_task:
                    details.append({"id": entity_id, "status": "failed", "error": "Task not found on server"})
                    failed_count += 1
                    continue

                # Apply conflict resolution based on the provided resolution data
                # The resolution should contain client data to merge
                client_data = resolution.get("client_data", {})

                # Determine which fields have conflicts (simplified - assume all fields can conflict)
                conflict_fields = list(client_data.keys())

                # Create dict representations for conflict resolution
                server_dict = {
                    "id": server_task.id,
                    "title": server_task.title,
                    "description": server_task.description,
                    "task_type": server_task.task_type.value if server_task.task_type else None,
                    "recurrence_type": server_task.recurrence_type.value if server_task.recurrence_type else None,
                    "recurrence_interval": server_task.recurrence_interval,
                    "interval_days": server_task.interval_days,
                    "reminder_time": server_task.reminder_time,
                    "group_id": server_task.group_id,
                    "enabled": server_task.enabled,
                    "completed": server_task.completed,
                    "assigned_user_ids": [user.id for user in server_task.assignees] if server_task.assignees else [],
                    "updated_at": server_task.updated_at
                }

                client_dict = client_data.copy()
                client_dict["id"] = entity_id

                # Resolve conflict
                winner, resolved_data = ConflictResolver.resolve_task_conflict(client_dict, server_dict, conflict_fields)

                if winner == "client":
                    # Apply client changes to server
                    try:
                        # Convert resolved_data to TaskUpdate format
                        update_data = {
                            "title": resolved_data.get("title"),
                            "description": resolved_data.get("description"),
                            "task_type": resolved_data.get("task_type"),
                            "recurrence_type": resolved_data.get("recurrence_type"),
                            "recurrence_interval": resolved_data.get("recurrence_interval"),
                            "interval_days": resolved_data.get("interval_days"),
                            "reminder_time": resolved_data.get("reminder_time"),
                            "enabled": resolved_data.get("enabled"),
                            "completed": resolved_data.get("completed"),
                            "group_id": resolved_data.get("group_id"),
                            "assigned_user_ids": resolved_data.get("assigned_user_ids", [])
                        }

                        # Apply the update with client timestamp
                        client_timestamp = client_data.get("updated_at")
                        from backend.schemas.task import TaskUpdate
                        task_update_data = TaskUpdate(**update_data)
                        updated_task = TaskService.update_task(db, entity_id, task_update_data, timestamp=client_timestamp)
                        if updated_task:
                            details.append({"id": entity_id, "status": "resolved", "winner": "client"})
                            resolved_count += 1
                        else:
                            details.append({"id": entity_id, "status": "failed", "error": "Update failed"})
                            failed_count += 1
                    except Exception as e:
                        details.append({"id": entity_id, "status": "failed", "error": str(e)})
                        failed_count += 1
                else:
                    # Server wins - no changes needed
                    details.append({"id": entity_id, "status": "resolved", "winner": "server"})
                    resolved_count += 1

            elif entity_type == "users":
                # Get current server state
                from backend.models.user import User
                server_user = db.query(User).filter(User.id == entity_id).first()
                if not server_user:
                    details.append({"id": entity_id, "status": "failed", "error": "User not found on server"})
                    failed_count += 1
                    continue

                # Apply conflict resolution for users
                client_data = resolution.get("client_data", {})
                conflict_fields = ["name", "email", "role", "is_active"]  # Typical user fields

                server_dict = {
                    "id": server_user.id,
                    "name": server_user.name,
                    "email": server_user.email,
                    "role": server_user.role.value if server_user.role else None,
                    "is_active": server_user.is_active,
                    "updated_at": server_user.updated_at
                }

                client_dict = client_data.copy()
                client_dict["id"] = entity_id

                winner, resolved_data = ConflictResolver.resolve_user_conflict(client_dict, server_dict, conflict_fields)

                if winner == "client":
                    # Apply client changes
                    try:
                        from backend.schemas.user import UserUpdate
                        update_data = UserUpdate(
                            name=resolved_data.get("name"),
                            email=resolved_data.get("email"),
                            role=resolved_data.get("role"),
                            is_active=resolved_data.get("is_active")
                        )
                        # Use UserService to update
                        updated_user = UserService.update_user(db, entity_id, update_data)
                        if updated_user:
                            details.append({"id": entity_id, "status": "resolved", "winner": "client"})
                            resolved_count += 1
                        else:
                            details.append({"id": entity_id, "status": "failed", "error": "Update failed"})
                            failed_count += 1
                    except Exception as e:
                        details.append({"id": entity_id, "status": "failed", "error": str(e)})
                        failed_count += 1
                else:
                    details.append({"id": entity_id, "status": "resolved", "winner": "server"})
                    resolved_count += 1

            elif entity_type == "groups":
                # Get current server state
                from backend.models.group import Group
                server_group = db.query(Group).filter(Group.id == entity_id).first()
                if not server_group:
                    details.append({"id": entity_id, "status": "failed", "error": "Group not found on server"})
                    failed_count += 1
                    continue

                # Apply conflict resolution for groups
                client_data = resolution.get("client_data", {})
                conflict_fields = ["name", "description"]  # Typical group fields (users not implemented yet)

                server_dict = {
                    "id": server_group.id,
                    "name": server_group.name,
                    "description": server_group.description,
                    "updated_at": server_group.updated_at
                }

                client_dict = client_data.copy()
                client_dict["id"] = entity_id

                winner, resolved_data = ConflictResolver.resolve_group_conflict(client_dict, server_dict, conflict_fields)

                if winner == "client":
                    # Apply client changes
                    try:
                        from backend.schemas.group import GroupUpdate
                        update_data = GroupUpdate(
                            name=resolved_data.get("name"),
                            description=resolved_data.get("description")
                        )
                        # Use GroupService to update
                        from backend.services.group_service import GroupService
                        updated_group = GroupService.update_group(db, entity_id, update_data)
                        if updated_group:
                            details.append({"id": entity_id, "status": "resolved", "winner": "client"})
                            resolved_count += 1
                        else:
                            details.append({"id": entity_id, "status": "failed", "error": "Update failed"})
                            failed_count += 1
                    except Exception as e:
                        details.append({"id": entity_id, "status": "failed", "error": str(e)})
                        failed_count += 1
                else:
                    details.append({"id": entity_id, "status": "resolved", "winner": "server"})
                    resolved_count += 1

            else:
                details.append({"id": entity_id, "status": "failed", "error": f"Unsupported entity type: {entity_type}"})
                failed_count += 1

        except Exception as e:
            details.append({"id": resolution.get("id"), "status": "failed", "error": str(e)})
            failed_count += 1

    return ConflictResolutionResponse(
        status="resolved",
        resolved_count=resolved_count,
        failed_count=failed_count,
        details=details
    )